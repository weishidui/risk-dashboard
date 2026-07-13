package com.finance.risk.dashboard.service;

import com.finance.risk.dashboard.dao.MetricsDao;
import com.finance.risk.dashboard.entity.MetricsSnapshot;
import com.finance.risk.dashboard.vo.DistributionVO;
import com.finance.risk.dashboard.vo.RealtimeMetricsVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聚合 risk-streaming 写入 Redis 的 10 秒指标桶。
 * Redis 只负责短窗口实时数据，MySQL t_metrics 只保存分钟级历史快照。
 */
@Service
public class RealtimeMetricsService {

    private static final long BUCKET_MS = 10_000L;
    private static final int LIVE_BUCKET_COUNT = 6;
    private static final int TOP_LIMIT = 10;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MetricsDao metricsDao;

    private volatile long lastSnapshotBucket = -1L;

    public RealtimeMetricsVO getLatestMetrics() {
        return aggregate(System.currentTimeMillis(), LIVE_BUCKET_COUNT);
    }

    /** 将上一个完整分钟写入 t_metrics，避免把仍在写入的当前分钟作为历史快照。 */
    public synchronized boolean snapshotPreviousMinute() {
        long now = System.currentTimeMillis();
        long minuteEnd = (now / 60_000L) * 60_000L;
        long minuteBucket = minuteEnd - 60_000L;
        if (minuteBucket <= 0 || minuteBucket == lastSnapshotBucket) {
            return false;
        }

        RealtimeMetricsVO metrics = aggregate(minuteEnd, LIVE_BUCKET_COUNT);
        lastSnapshotBucket = minuteBucket;
        if (!metrics.isAvailable()) {
            return false;
        }

        MetricsSnapshot snapshot = MetricsSnapshot.builder()
                .snapshotTime(minuteBucket)
                .totalTransactions(metrics.getTotalTransactions())
                .passCount(metrics.getPassCount())
                .verifyCount(metrics.getVerifyCount())
                .blockCount(metrics.getBlockCount())
                .highRiskCount(metrics.getHighRiskCount() + metrics.getCriticalRiskCount())
                .mediumRiskCount(metrics.getMediumRiskCount())
                .lowRiskCount(metrics.getLowRiskCount())
                .avgRiskScore(metrics.getAvgRiskScore())
                .envRiskCount(metrics.getEnvRiskCount())
                .amountRiskCount(metrics.getAmountRiskCount())
                .teleportRiskCount(metrics.getTeleportRiskCount())
                .geoRiskCount(metrics.getGeoRiskCount())
                .avgLatency(0.0)
                .build();
        return metricsDao.insert(snapshot) > 0;
    }

    private RealtimeMetricsVO aggregate(long endExclusive, int bucketCount) {
        long newestBucket = ((endExclusive - 1L) / BUCKET_MS) * BUCKET_MS;
        long total = 0L;
        long pass = 0L;
        long verify = 0L;
        long block = 0L;
        long low = 0L;
        long medium = 0L;
        long high = 0L;
        long critical = 0L;
        long env = 0L;
        long amount = 0L;
        long teleport = 0L;
        long geo = 0L;
        double scoreSum = 0.0;
        List<String> userKeys = new ArrayList<>();
        Map<String, Long> cities = new HashMap<>();
        Map<String, Long> rules = new HashMap<>();

        for (int i = 0; i < bucketCount; i++) {
            long bucket = newestBucket - i * BUCKET_MS;
            Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(metricKey(bucket));
            total += count(values, "txn_total");
            pass += count(values, "decision_pass");
            verify += count(values, "decision_review");
            block += count(values, "decision_block");
            low += count(values, "risk_low");
            medium += count(values, "risk_medium");
            high += count(values, "risk_high");
            critical += count(values, "risk_critical");
            env += count(values, "env_risk_count");
            amount += count(values, "amount_risk_count");
            teleport += count(values, "teleport_risk_count");
            geo += count(values, "geo_risk_count");
            scoreSum += decimal(values, "score_sum");
            merge(cities, stringRedisTemplate.opsForHash().entries(cityKey(bucket)));
            merge(rules, stringRedisTemplate.opsForHash().entries(ruleKey(bucket)));
            userKeys.add(userKey(bucket));
        }

        long activeUsers = 0L;
        if (!userKeys.isEmpty()) {
            Long size = stringRedisTemplate.opsForHyperLogLog().size(userKeys.toArray(new String[0]));
            activeUsers = size == null ? 0L : size;
        }

        double avgScore = total == 0 ? 0.0 : round(scoreSum / total);
        double severeRate = total == 0 ? 0.0 : ((high + critical) * 100.0 / total);
        double blockRate = total == 0 ? 0.0 : (block * 100.0 / total);
        double riskIndex = round(Math.min(100.0, avgScore * 0.45 + severeRate * 0.30 + blockRate * 0.25));

        return RealtimeMetricsVO.builder()
                .available(total > 0)
                .windowSeconds(bucketCount * BUCKET_MS / 1000L)
                .windowEndTime(endExclusive)
                .totalTransactions(total)
                .passCount(pass)
                .verifyCount(verify)
                .blockCount(block)
                .activeUsers(activeUsers)
                .avgRiskScore(avgScore)
                .riskIndex(riskIndex)
                .lowRiskCount(low)
                .mediumRiskCount(medium)
                .highRiskCount(high)
                .criticalRiskCount(critical)
                .envRiskCount(env)
                .amountRiskCount(amount)
                .teleportRiskCount(teleport)
                .geoRiskCount(geo)
                .riskLevelDistribution(Arrays.asList(
                        item("低危", low, "#22C55E"),
                        item("中危", medium, "#F59E0B"),
                        item("高危", high, "#F97316"),
                        item("极度危险", critical, "#DC2626")
                ))
                .cityDistribution(top(cities, TOP_LIMIT))
                .ruleTypeDistribution(top(rules, TOP_LIMIT))
                .build();
    }

    private String metricKey(long bucket) {
        return "risk:rt:metric:10s:" + bucket;
    }

    private String userKey(long bucket) {
        return "risk:rt:users:10s:" + bucket;
    }

    private String cityKey(long bucket) {
        return "risk:rt:city:10s:" + bucket;
    }

    private String ruleKey(long bucket) {
        return "risk:rt:rule:10s:" + bucket;
    }

    private long count(Map<Object, Object> values, String field) {
        if (values == null || values.get(field) == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(values.get(field)));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private double decimal(Map<Object, Object> values, String field) {
        if (values == null || values.get(field) == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(values.get(field)));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private void merge(Map<String, Long> target, Map<Object, Object> source) {
        if (source == null) {
            return;
        }
        for (Map.Entry<Object, Object> entry : source.entrySet()) {
            String name = String.valueOf(entry.getKey());
            try {
                target.merge(name, Long.parseLong(String.valueOf(entry.getValue())), Long::sum);
            } catch (NumberFormatException ignored) {
                // Redis 中的非数字字段不参与图表聚合。
            }
        }
    }

    private List<DistributionVO> top(Map<String, Long> values, int limit) {
        if (values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map.Entry<String, Long>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(Map.Entry<String, Long>::getValue).reversed());
        List<DistributionVO> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            Map.Entry<String, Long> entry = entries.get(i);
            result.add(item(entry.getKey(), entry.getValue(), null));
        }
        return result;
    }

    private DistributionVO item(String name, long value, String color) {
        return DistributionVO.builder().name(name).value(value).color(color).build();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
