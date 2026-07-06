package com.finance.risk.dashboard.service.impl;

import com.alibaba.fastjson.JSON;
import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.dao.TransactionDao;
import com.finance.risk.dashboard.dto.TransactionInputDTO;
import com.finance.risk.dashboard.entity.Transaction;
import com.finance.risk.dashboard.service.TransactionService;
import com.finance.risk.dashboard.vo.DistributionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    @Resource
    private TransactionDao transactionDao;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private static final long CACHE_EXPIRE_MINUTES = 5;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int receiveTransactions(List<TransactionInputDTO> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            log.warn("交易数据列表为空，跳过处理");
            return 0;
        }

        int count = 0;
        for (TransactionInputDTO dto : transactions) {
            Transaction entity = convertToEntity(dto);
            int result = transactionDao.insert(entity);
            if (result > 0) {
                cacheTransaction(entity);
                count++;
            }
        }

        log.info("批量接收交易数据完成，成功写入 {} 条", count);
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean receiveTransaction(TransactionInputDTO dto) {
        if (dto == null) {
            return false;
        }
        Transaction entity = convertToEntity(dto);
        int result = transactionDao.insert(entity);
        if (result > 0) {
            cacheTransaction(entity);
            return true;
        }
        return false;
    }

    @Override
    public List<Transaction> getRecentTransactions(int limit) {
        try {
            String cacheKey = Constants.REDIS_TRANSACTION_PREFIX + "recent";
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return JSON.parseArray(cached.toString(), Transaction.class);
            }
        } catch (Exception e) {
            log.warn("Redis缓存读取失败: {}", e.getMessage());
        }

        return transactionDao.findRecent(limit);
    }

    @Override
    public List<DistributionVO> countByCity(int limit) {
        long sinceTime = System.currentTimeMillis() - 60 * 60 * 1000;
        List<TransactionDao.CityCount> counts = transactionDao.countByCity(sinceTime, limit);

        if (counts == null || counts.isEmpty()) {
            return new ArrayList<>();
        }

        return counts.stream()
                .map(c -> DistributionVO.builder()
                        .name(c.getCity())
                        .value(c.getCnt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public Long countByTimeRange(Long startTime, Long endTime) {
        return transactionDao.countByTimeRange(startTime, endTime);
    }

    private Transaction convertToEntity(TransactionInputDTO dto) {
        return Transaction.builder()
                .transId(dto.getTransId())
                .userId(dto.getUserId())
                .amount(dto.getAmount())
                .transTimestamp(dto.getTimestamp())
                .city(dto.getCity())
                .geoLocation(dto.getGeoLocation())
                .deviceId(dto.getDeviceId())
                .networkType(dto.getNetworkType())
                .devScore(dto.getDevScore())
                .ipAddress(dto.getIpAddress())
                .osType(dto.getOsType())
                .osVersion(dto.getOsVersion())
                .screenResolution(dto.getScreenResolution())
                .batteryLevel(dto.getBatteryLevel())
                .rootJailbreak(dto.getRootJailbreak())
                .simOperator(dto.getSimOperator())
                .userAgent(dto.getUserAgent())
                .dnsServer(dto.getDnsServer())
                .wifiSsid(dto.getWifiSsid())
                .transType(dto.getTransType())
                .payChannel(dto.getPayChannel())
                .inputMethod(dto.getInputMethod())
                .clickDuration(dto.getClickDuration())
                .note(dto.getNote())
                .pageUrl(dto.getPageUrl())
                .counterpartyId(dto.getCounterpartyId())
                .counterpartyName(dto.getCounterpartyName())
                .counterpartyBank(dto.getCounterpartyBank())
                .loginSessionId(dto.getLoginSessionId())
                .loginFailCount(dto.getLoginFailCount())
                .build();
    }

    private void cacheTransaction(Transaction entity) {
        try {
            String key = Constants.REDIS_TRANSACTION_PREFIX + entity.getTransId();
            redisTemplate.opsForValue().set(key, JSON.toJSONString(entity),
                    CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("交易缓存写入失败: {}", e.getMessage());
        }
    }
}
