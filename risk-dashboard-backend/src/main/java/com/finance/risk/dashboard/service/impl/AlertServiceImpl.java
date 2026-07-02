package com.finance.risk.dashboard.service.impl;

import com.alibaba.fastjson.JSON;
import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.dao.AlertDao;
import com.finance.risk.dashboard.dto.AlertInputDTO;
import com.finance.risk.dashboard.entity.AlertResult;
import com.finance.risk.dashboard.service.AlertService;
import com.finance.risk.dashboard.vo.AlertVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AlertServiceImpl implements AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertServiceImpl.class);

    @Resource
    private AlertDao alertDao;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private static final DateTimeFormatter DB_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 城市坐标映射 (用于地图展示，因 risk_alert 表不再存储 geo_location) */
    private static final Map<String, String> CITY_COORDS = new HashMap<>();
    static {
        CITY_COORDS.put("北京", "116.40,39.90"); CITY_COORDS.put("上海", "121.47,31.23");
        CITY_COORDS.put("广州", "113.26,23.13"); CITY_COORDS.put("深圳", "114.07,22.55");
        CITY_COORDS.put("杭州", "120.15,30.28"); CITY_COORDS.put("成都", "104.06,30.67");
        CITY_COORDS.put("武汉", "114.30,30.60"); CITY_COORDS.put("南京", "118.79,32.06");
        CITY_COORDS.put("重庆", "106.55,29.57"); CITY_COORDS.put("西安", "108.94,34.26");
        CITY_COORDS.put("长沙", "112.94,28.23"); CITY_COORDS.put("郑州", "113.65,34.76");
        CITY_COORDS.put("济南", "117.00,36.67"); CITY_COORDS.put("福州", "119.30,26.07");
        CITY_COORDS.put("合肥", "117.27,31.86"); CITY_COORDS.put("南昌", "115.89,28.68");
        CITY_COORDS.put("昆明", "102.71,25.04"); CITY_COORDS.put("贵阳", "106.71,26.57");
        CITY_COORDS.put("南宁", "108.33,22.84"); CITY_COORDS.put("海口", "110.33,20.03");
        CITY_COORDS.put("兰州", "103.73,36.03"); CITY_COORDS.put("沈阳", "123.43,41.80");
        CITY_COORDS.put("长春", "125.35,43.88"); CITY_COORDS.put("哈尔滨", "126.63,45.75");
        CITY_COORDS.put("石家庄", "114.48,38.03"); CITY_COORDS.put("太原", "112.53,37.87");
        CITY_COORDS.put("天津", "117.19,39.12"); CITY_COORDS.put("香港", "114.17,22.28");
        CITY_COORDS.put("澳门", "113.55,22.19");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int receiveAlerts(List<AlertInputDTO> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            log.warn("告警数据列表为空，跳过处理");
            return 0;
        }

        List<AlertResult> entities = alerts.stream()
                .map(this::convertToEntity)
                .collect(Collectors.toList());

        int count = alertDao.batchInsert(entities);
        log.info("批量接收告警数据完成，成功写入 {} 条", count);

        if (count > 0) {
            try {
                redisTemplate.opsForList().leftPushAll(
                        Constants.REDIS_ALERT_LIST,
                        entities.stream().map(JSON::toJSONString).toArray(String[]::new));
                redisTemplate.opsForList().trim(Constants.REDIS_ALERT_LIST, 0, 199);
            } catch (Exception e) {
                log.error("Redis 缓存写入失败: {}", e.getMessage());
            }
        }

        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean receiveAlert(AlertInputDTO alert) {
        if (alert == null) {
            return false;
        }
        AlertResult entity = convertToEntity(alert);
        return alertDao.insert(entity) > 0;
    }

    @Override
    public Map<String, Object> queryAlertList(String riskLevel, String startTime, String endTime,
                                               int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<AlertResult> list = alertDao.findList(riskLevel, startTime, endTime, offset, pageSize);
        Long total = alertDao.count(riskLevel, startTime, endTime);

        List<AlertVO> vos = list.stream().map(this::convertToVO).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("list", vos);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    @Override
    public List<AlertVO> getRecentAlerts(int limit) {
        try {
            List<Object> cachedList = redisTemplate.opsForList()
                    .range(Constants.REDIS_ALERT_LIST, 0, limit - 1);
            if (cachedList != null && !cachedList.isEmpty()) {
                return cachedList.stream()
                        .map(o -> JSON.parseObject(o.toString(), AlertResult.class))
                        .map(this::convertToVO)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Redis 缓存读取失败，回退到数据库查询: {}", e.getMessage());
        }

        List<AlertResult> list = alertDao.findList(null, null, null, 0, limit);
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> countByRiskLevel() {
        String sinceTime = LocalDateTime.now().minusDays(1).format(DB_TIME_FMT);
        List<AlertDao.RiskLevelCount> counts = alertDao.countByRiskLevel(sinceTime);

        return counts.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", c.getRiskLevel());
            map.put("value", c.getCnt());
            map.put("color", getRiskColor(c.getRiskLevel()));
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> countByHitRule() {
        String sinceTime = LocalDateTime.now().minusDays(1).format(DB_TIME_FMT);
        List<AlertDao.RuleCount> counts = alertDao.countByHitRule(sinceTime);

        return counts.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("name", c.getHitRules());
            map.put("value", c.getCnt());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> countHighRiskByCity(int limit) {
        String sinceTime = LocalDateTime.now().minusDays(1).format(DB_TIME_FMT);
        List<AlertDao.CityAlertCount> counts = alertDao.countHighRiskByCity(sinceTime, limit);

        return counts.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("city", c.getAlertLoc());
            map.put("count", c.getCnt());
            String coords = CITY_COORDS.getOrDefault(c.getAlertLoc(), "116.40,39.90");
            String[] parts = coords.split(",");
            if (parts.length == 2) {
                try {
                    map.put("longitude", Double.parseDouble(parts[0].trim()));
                    map.put("latitude", Double.parseDouble(parts[1].trim()));
                } catch (NumberFormatException ignored) {}
            }
            return map;
        }).collect(Collectors.toList());
    }

    // ==================== 内部转换方法 ====================

    private AlertResult convertToEntity(AlertInputDTO dto) {
        return AlertResult.builder()
                .alertId(dto.getAlertId())
                .transId(dto.getTransId())
                .userId(dto.getUserId())
                .hitRules(dto.getHitRules())
                .amount(dto.getAmount())
                .finalScore(dto.getFinalScore())
                .riskLevel(dto.getRiskLevel())
                .city(dto.getCity())
                .alertLoc(dto.getAlertLoc())
                .rawJson(dto.getRawJson())
                .build();
    }

    private AlertVO convertToVO(AlertResult entity) {
        return AlertVO.builder()
                .alertId(entity.getAlertId())
                .transId(entity.getTransId())
                .userId(maskUserId(entity.getUserId()))
                .hitRules(entity.getHitRules())
                .amount(entity.getAmount())
                .finalScore(entity.getFinalScore())
                .riskLevel(entity.getRiskLevel())
                .city(entity.getCity())
                .alertLoc(entity.getAlertLoc())
                .action(riskLevelToAction(entity.getRiskLevel()))
                .createTime(entity.getCreateTime())
                .build();
    }

    /** 由 risk_level 推导处理动作: 低危→PASS, 中危→VERIFY, 高危→BLOCK */
    private String riskLevelToAction(String riskLevel) {
        if ("高危".equals(riskLevel)) return "BLOCK";
        if ("中危".equals(riskLevel)) return "VERIFY";
        return "PASS";
    }

    private String maskUserId(String userId) {
        if (userId == null || userId.length() <= 7) {
            return userId;
        }
        return userId.substring(0, 3) + "****" + userId.substring(userId.length() - 4);
    }

    private String getRiskColor(String riskLevel) {
        if ("高危".equals(riskLevel)) return "#F56C6C";
        if ("中危".equals(riskLevel)) return "#E6A23C";
        return "#67C23A";
    }
}
