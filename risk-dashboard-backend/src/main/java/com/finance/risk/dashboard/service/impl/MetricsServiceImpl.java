package com.finance.risk.dashboard.service.impl;

import com.alibaba.fastjson.JSON;
import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.dao.MetricsDao;
import com.finance.risk.dashboard.dto.MetricsInputDTO;
import com.finance.risk.dashboard.entity.MetricsSnapshot;
import com.finance.risk.dashboard.service.AlertService;
import com.finance.risk.dashboard.service.MetricsService;
import com.finance.risk.dashboard.service.TransactionService;
import com.finance.risk.dashboard.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 指标统计服务实现
 * 负责仪表盘首页所有数据的聚合计算
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Service
public class MetricsServiceImpl implements MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsServiceImpl.class);

    @Resource
    private MetricsDao metricsDao;

    @Resource
    private AlertService alertService;

    @Resource
    private TransactionService transactionService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private static final SimpleDateFormat SDF_HM = new SimpleDateFormat("HH:mm");
    private static final long ONE_HOUR_MS = 60 * 60 * 1000L;
    private static final long ONE_DAY_MS = 24 * ONE_HOUR_MS;

    /** 系统启动时间 */
    private final long startupTime = System.currentTimeMillis();

    @Override
    public boolean receiveMetrics(MetricsInputDTO dto) {
        if (dto == null) {
            return false;
        }
        MetricsSnapshot entity = convertToEntity(dto);
        int result = metricsDao.insert(entity);
        if (result > 0) {
            // 缓存最新指标
            redisTemplate.opsForValue().set(Constants.REDIS_LATEST_METRICS,
                    JSON.toJSONString(entity));
        }
        return result > 0;
    }

    @Override
    public MetricsSnapshot getLatestMetrics() {
        // 先从Redis读取
        try {
            Object cached = redisTemplate.opsForValue().get(Constants.REDIS_LATEST_METRICS);
            if (cached != null) {
                return JSON.parseObject(cached.toString(), MetricsSnapshot.class);
            }
        } catch (Exception e) {
            log.warn("Redis缓存读取失败: {}", e.getMessage());
        }
        return metricsDao.findLatest();
    }

    @Override
    public DashboardVO getDashboardData() {
        MetricsSnapshot metrics = getLatestMetrics();
        long now = System.currentTimeMillis();

        // 1. 核心指标
        DashboardVO.DashboardVOBuilder builder = DashboardVO.builder();
        if (metrics != null) {
            builder.totalTransactions(metrics.getTotalTransactions())
                   .passCount(metrics.getPassCount())
                   .verifyCount(metrics.getVerifyCount())
                   .blockCount(metrics.getBlockCount())
                   .avgRiskScore(metrics.getAvgRiskScore())
                   .avgLatency(metrics.getAvgLatency());
        } else {
            // 默认值
            builder.totalTransactions(0L).passCount(0L).verifyCount(0L)
                   .blockCount(0L).avgRiskScore(0.0).avgLatency(0.0);
        }

        // 活跃用户 (近5分钟内有过交易的用户)
        builder.activeUsers(estimateActiveUsers());
        builder.uptimeSeconds((now - startupTime) / 1000);

        // 2. 趋势数据 (近24小时)
        long since24h = now - ONE_DAY_MS;
        builder.transactionTrend(buildTransactionTrend(since24h, now));
        builder.alertTrend(buildAlertTrend(since24h, now));
        builder.blockRateTrend(buildBlockRateTrend(since24h, now));

        // 3. 分布数据
        builder.riskLevelDistribution(buildRiskLevelDist());
        builder.ruleTypeDistribution(buildRuleTypeDist());
        builder.cityDistribution(transactionService.countByCity(10));

        // 4. 地理告警
        builder.geoAlerts(buildGeoAlerts());

        // 5. 最新告警
        builder.recentAlerts(alertService.getRecentAlerts(10));

        return builder.build();
    }

    @Override
    public List<MetricsSnapshot> getMetricsTrend(int hours) {
        long sinceTime = System.currentTimeMillis() - hours * ONE_HOUR_MS;
        return metricsDao.findByTimeRange(sinceTime, System.currentTimeMillis());
    }

    // ==================== 仪表盘数据构建方法 ====================

    private Long estimateActiveUsers() {
        long since5min = System.currentTimeMillis() - 5 * 60 * 1000;
        return transactionService.countByTimeRange(since5min, System.currentTimeMillis());
    }

    private List<TrendPointVO> buildTransactionTrend(long since, long now) {
        List<MetricsSnapshot> snapshots = metricsDao.findByTimeRange(since, now);
        if (snapshots == null || snapshots.isEmpty()) {
            return generateEmptyTrend(since, now, 24);
        }
        return snapshots.stream()
                .map(s -> TrendPointVO.builder()
                        .time(SDF_HM.format(new Date(s.getSnapshotTime())))
                        .value(s.getTotalTransactions().doubleValue())
                        .timestamp(s.getSnapshotTime())
                        .build())
                .collect(Collectors.toList());
    }

    private List<TrendPointVO> buildAlertTrend(long since, long now) {
        List<MetricsSnapshot> snapshots = metricsDao.findByTimeRange(since, now);
        if (snapshots == null || snapshots.isEmpty()) {
            return generateEmptyTrend(since, now, 24);
        }
        return snapshots.stream()
                .map(s -> TrendPointVO.builder()
                        .time(SDF_HM.format(new Date(s.getSnapshotTime())))
                        .value((double) (s.getHighRiskCount() + s.getMediumRiskCount()))
                        .timestamp(s.getSnapshotTime())
                        .build())
                .collect(Collectors.toList());
    }

    private List<TrendPointVO> buildBlockRateTrend(long since, long now) {
        List<MetricsSnapshot> snapshots = metricsDao.findByTimeRange(since, now);
        if (snapshots == null || snapshots.isEmpty()) {
            return generateEmptyTrend(since, now, 24);
        }
        return snapshots.stream()
                .map(s -> {
                    double rate = s.getTotalTransactions() > 0
                            ? (s.getBlockCount().doubleValue() / s.getTotalTransactions()) * 100
                            : 0;
                    return TrendPointVO.builder()
                            .time(SDF_HM.format(new Date(s.getSnapshotTime())))
                            .value(Math.round(rate * 100.0) / 100.0)
                            .timestamp(s.getSnapshotTime())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<DistributionVO> buildRiskLevelDist() {
        return Arrays.asList(
                DistributionVO.builder().name("低危").value(
                        Optional.ofNullable(getLatestMetrics())
                                .map(MetricsSnapshot::getLowRiskCount).orElse(0L)
                ).color("#67C23A").build(),
                DistributionVO.builder().name("中危").value(
                        Optional.ofNullable(getLatestMetrics())
                                .map(MetricsSnapshot::getMediumRiskCount).orElse(0L)
                ).color("#E6A23C").build(),
                DistributionVO.builder().name("高危").value(
                        Optional.ofNullable(getLatestMetrics())
                                .map(MetricsSnapshot::getHighRiskCount).orElse(0L)
                ).color("#F56C6C").build()
        );
    }

    private List<DistributionVO> buildRuleTypeDist() {
        MetricsSnapshot metrics = getLatestMetrics();
        if (metrics == null) {
            return new ArrayList<>();
        }
        return Arrays.asList(
                DistributionVO.builder().name("金额异常").value(metrics.getAmountRiskCount())
                        .color("#409EFF").build(),
                DistributionVO.builder().name("地理偏离").value(metrics.getGeoRiskCount())
                        .color("#E6A23C").build(),
                DistributionVO.builder().name("异地瞬移").value(metrics.getTeleportRiskCount())
                        .color("#F56C6C").build(),
                DistributionVO.builder().name("环境风险").value(metrics.getEnvRiskCount())
                        .color("#909399").build()
        );
    }

    private List<GeoAlertVO> buildGeoAlerts() {
        List<Map<String, Object>> cityAlerts = alertService.countHighRiskByCity(20);

        return cityAlerts.stream().map(m -> {
            Double lng = (Double) m.getOrDefault("longitude", 116.3);
            Double lat = (Double) m.getOrDefault("latitude", 39.9);
            return GeoAlertVO.builder()
                    .alertId(UUID.randomUUID().toString().substring(0, 8))
                    .city((String) m.get("city"))
                    .longitude(lng)
                    .latitude(lat)
                    .riskLevel("高危")
                    .build();
        }).collect(Collectors.toList());
    }

    private List<TrendPointVO> generateEmptyTrend(long since, long now, int points) {
        List<TrendPointVO> trend = new ArrayList<>();
        long interval = (now - since) / points;
        for (int i = 0; i < points; i++) {
            long t = since + i * interval;
            trend.add(TrendPointVO.builder()
                    .time(SDF_HM.format(new Date(t)))
                    .value(0.0)
                    .timestamp(t)
                    .build());
        }
        return trend;
    }

    private MetricsSnapshot convertToEntity(MetricsInputDTO dto) {
        return MetricsSnapshot.builder()
                .snapshotTime(dto.getSnapshotTime())
                .totalTransactions(dto.getTotalTransactions())
                .passCount(dto.getPassCount())
                .verifyCount(dto.getVerifyCount())
                .blockCount(dto.getBlockCount())
                .highRiskCount(dto.getHighRiskCount())
                .mediumRiskCount(dto.getMediumRiskCount())
                .lowRiskCount(dto.getLowRiskCount())
                .avgRiskScore(dto.getAvgRiskScore())
                .envRiskCount(dto.getEnvRiskCount())
                .amountRiskCount(dto.getAmountRiskCount())
                .teleportRiskCount(dto.getTeleportRiskCount())
                .geoRiskCount(dto.getGeoRiskCount())
                .avgLatency(dto.getAvgLatency())
                .build();
    }
}
