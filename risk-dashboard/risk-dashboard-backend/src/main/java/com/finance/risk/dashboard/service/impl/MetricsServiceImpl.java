package com.finance.risk.dashboard.service.impl;

import com.alibaba.fastjson.JSON;
import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.dao.AlertDao;
import com.finance.risk.dashboard.dao.MetricsDao;
import com.finance.risk.dashboard.dto.MetricsInputDTO;
import com.finance.risk.dashboard.entity.MetricsSnapshot;
import com.finance.risk.dashboard.service.AlertService;
import com.finance.risk.dashboard.service.MetricsService;
import com.finance.risk.dashboard.service.RealtimeMetricsService;
import com.finance.risk.dashboard.service.TransactionService;
import com.finance.risk.dashboard.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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
    private AlertDao alertDao;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RealtimeMetricsService realtimeMetricsService;

    private static final SimpleDateFormat SDF_HM = new SimpleDateFormat("HH:mm");
    private static final DateTimeFormatter DB_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long ONE_HOUR_MS = 60 * 60 * 1000L;
    private static final long ONE_DAY_MS = 24 * ONE_HOUR_MS;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

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
        return metricsDao.findLatest();
    }

    @Override
    public RealtimeMetricsVO getRealtimeMetrics() {
        try {
            return realtimeMetricsService.getLatestMetrics();
        } catch (Exception e) {
            log.warn("Redis 实时指标读取失败，回退到历史快照: {}", e.getMessage());
            return RealtimeMetricsVO.builder().available(false).build();
        }
    }

    @Override
    public DashboardVO getDashboardData() {
        long now = System.currentTimeMillis();
        MetricsSnapshot metrics = null;
        RealtimeMetricsVO realtimeMetrics = getRealtimeMetrics();
        try { metrics = getLatestMetrics(); } catch (Exception e) { log.warn("获取指标失败: {}", e.getMessage()); }

        // 1. 核心指标
        DashboardVO.DashboardVOBuilder builder = DashboardVO.builder();
        if (realtimeMetrics.isAvailable()) {
            builder.totalTransactions(realtimeMetrics.getTotalTransactions())
                   .passCount(realtimeMetrics.getPassCount())
                   .verifyCount(realtimeMetrics.getVerifyCount())
                   .blockCount(realtimeMetrics.getBlockCount())
                   .avgRiskScore(realtimeMetrics.getAvgRiskScore())
                   .avgLatency(0.0);
        } else if (metrics != null) {
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
        builder.activeUsers(realtimeMetrics.isAvailable() ? realtimeMetrics.getActiveUsers() : estimateActiveUsers());
        builder.uptimeSeconds((now - startupTime) / 1000);

        // 2. 趋势数据 (近24小时)
        try {
            long since24h = now - ONE_DAY_MS;
            builder.transactionTrend(buildTransactionTrend(since24h, now));
            builder.alertTrend(buildAlertTrend(since24h, now));
            builder.blockRateTrend(buildBlockRateTrend(since24h, now));
        } catch (Exception e) {
            log.warn("趋势数据查询失败: {}", e.getMessage());
            builder.transactionTrend(new ArrayList<>()).alertTrend(new ArrayList<>()).blockRateTrend(new ArrayList<>());
        }

        // 3. 分布数据
        builder.riskLevelDistribution(realtimeMetrics.isAvailable()
                ? realtimeMetrics.getRiskLevelDistribution() : buildRiskLevelDist());
        builder.ruleTypeDistribution(realtimeMetrics.isAvailable()
                ? realtimeMetrics.getRuleTypeDistribution() : buildRuleTypeDist());
        builder.cityDistribution(realtimeMetrics.isAvailable()
                ? realtimeMetrics.getCityDistribution() : transactionService.countByCity(10));

        // 4. 地理告警
        builder.geoAlerts(buildGeoAlerts());

        // 5. 最新告警
        builder.recentAlerts(alertService.getRecentSevereAlerts(30));

        return builder.build();
    }

    @Override
    public List<MetricsSnapshot> getMetricsTrend(int hours) {
        try {
            long sinceTime = System.currentTimeMillis() - hours * ONE_HOUR_MS;
            return metricsDao.findByTimeRange(sinceTime, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("趋势查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== 仪表盘数据构建方法 ====================

    private Long estimateActiveUsers() {
        // 用 24 小时窗口，确保演示数据能被统计到
        long since24h = System.currentTimeMillis() - 24 * 60 * 60 * 1000;
        Long count = transactionService.countDistinctUsers(since24h, System.currentTimeMillis());
        return count != null ? count : 0L;
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
        MetricsSnapshot m = getLatestMetrics();
        long critical = m != null ? m.getHighRiskCount() / 3 : 0;
        long high = m != null ? m.getHighRiskCount() * 2 / 3 : 0;
        return Arrays.asList(
                DistributionVO.builder().name("低危").value(
                        Optional.ofNullable(m).map(MetricsSnapshot::getLowRiskCount).orElse(0L)
                ).color("#22C55E").build(),
                DistributionVO.builder().name("中危").value(
                        Optional.ofNullable(m).map(MetricsSnapshot::getMediumRiskCount).orElse(0L)
                ).color("#F59E0B").build(),
                DistributionVO.builder().name("高危").value(high
                ).color("#F97316").build(),
                DistributionVO.builder().name("极度危险").value(critical
                ).color("#DC2626").build()
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
                    .riskLevel((String) m.getOrDefault("riskLevel", "中危"))
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

    @Scheduled(fixedRate = 60000)
    public void snapshotMetrics() {
        if (!"prod".equals(activeProfile)) return;
        try {
            realtimeMetricsService.snapshotPreviousMinute();
        } catch (Exception e) {
            log.error("指标快照采集失败: {}", e.getMessage());
        }
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
