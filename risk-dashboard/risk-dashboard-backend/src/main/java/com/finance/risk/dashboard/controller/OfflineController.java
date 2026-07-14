package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.dao.*;
import com.finance.risk.dashboard.service.OfflineAnalysisService;
import com.finance.risk.dashboard.service.OfflineTaskStatusService;
import com.finance.risk.dashboard.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * 离线分析总览 + 离线任务监控 + 分析触发
 */
@Api(tags = "9. 离线分析接口")
@RestController
@RequestMapping("/api/offline")
public class OfflineController {

    private static final long OVERVIEW_CACHE_MS = 60_000L;

    @Resource private UserProfileDao userProfileDao;
    @Resource private OfflineAdsDao offlineAdsDao;
    @Resource private CounterpartyBlacklistDao blacklistDao;
    @Resource private TransChainDao chainDao;
    @Resource private RedisTemplate<String, Object> redisTemplate;
    @Resource private OfflineAnalysisService analysisService;
    @Resource private OfflineTaskStatusService taskStatusService;

    private volatile Map<String, Object> cachedOverview;
    private volatile long cachedOverviewAt;
    private volatile Map<String, Object> activeDashboardSnapshot;
    private final Object overviewCacheLock = new Object();

    @ApiOperation("离线总览汇总数据")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        long now = System.currentTimeMillis();
        Map<String, Object> cached = cachedOverview;
        if (cached != null && now - cachedOverviewAt < OVERVIEW_CACHE_MS) {
            return Result.ok(new LinkedHashMap<>(cached));
        }

        synchronized (overviewCacheLock) {
            cached = cachedOverview;
            now = System.currentTimeMillis();
            if (cached != null && now - cachedOverviewAt < OVERVIEW_CACHE_MS) {
                return Result.ok(new LinkedHashMap<>(cached));
            }

            Map<String, Object> data = buildOverviewData();
            cachedOverview = new LinkedHashMap<>(data);
            cachedOverviewAt = now;
            return Result.ok(data);
        }
    }

    private Map<String, Object> buildOverviewData() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> snapshot = offlineAdsDao.findLatestOverview();
        data.put("dt", value(snapshot, "dt", taskStatusService.getCachedLatestDt("UNKNOWN")));
        data.put("windowStart", value(snapshot, "window_start", ""));
        data.put("windowEnd", value(snapshot, "window_end", ""));

        // MySQL 各表行数
        data.put("dwdTransCount", value(snapshot, "total_transactions", 0L));
        data.put("userProfileCount", value(snapshot, "distinct_users", 0L));
        data.put("highRiskUserCount", value(snapshot, "high_risk_users", 0L));
        data.put("riskUserCount", value(snapshot, "risk_users", 0L));
        data.put("blacklistCount", blacklistDao.count());
        data.put("chainCount", chainDao.count());
        data.put("loopChainCount", chainDao.countLoopChains());
        data.put("deviceCount", value(snapshot, "distinct_devices", 0L));
        data.put("counterpartyCount", value(snapshot, "distinct_counterparties", 0L));

        // Redis 同步状态
        try {
            Set<String> profileKeys = redisTemplate.keys("profile:*");
            Set<String> deviceKeys = redisTemplate.keys("device_risk:*");
            data.put("redisProfileCount", profileKeys != null ? (long) profileKeys.size() : 0L);
            data.put("redisDeviceCount", deviceKeys != null ? (long) deviceKeys.size() : 0L);
            data.put("redisSynced", profileKeys != null && !profileKeys.isEmpty());
        } catch (Exception e) {
            data.put("redisProfileCount", 0L);
            data.put("redisDeviceCount", 0L);
            data.put("redisSynced", false);
        }

        return data;
    }

    private Object value(Map<String, Object> values, String key, Object fallback) {
        Object value = values.get(key);
        return value == null ? fallback : value;
    }

    @ApiOperation("风险评分分布")
    @GetMapping("/risk-distribution")
    public Result<List<Map<String, Object>>> riskDistribution() {
        Map<String, Object> snapshot = offlineAdsDao.findLatestOverview();
        Object dt = snapshot.get("dt");
        List<Map<String, Object>> rows = offlineAdsDao.findScoreDistribution(dt == null ? "" : String.valueOf(dt));
        List<Map<String, Object>> list = new ArrayList<>();
        if (rows.isEmpty()) {
            String[] labels = {"≤0", "1-20", "21-40", "41-60", "61-80", "81-100"};
            for (String l : labels) {
                Map<String, Object> m = new LinkedHashMap<>(); m.put("name", l); m.put("value", 0); list.add(m);
            }
        } else {
            for (Map<String, Object> r : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", r.get("name")); m.put("value", r.get("value")); list.add(m);
            }
        }
        return Result.ok(list);
    }

    @ApiOperation("离线风险分析大屏数据")
    @GetMapping("/dashboard-data")
    public Result<Map<String, Object>> dashboardData() {
        Map<String, Object> snapshot = activeDashboardSnapshot;
        if (analysisService.hasActiveTask() && snapshot != null) {
            return Result.ok(new LinkedHashMap<>(snapshot));
        }
        if (!analysisService.hasActiveTask()) {
            activeDashboardSnapshot = null;
        }
        return Result.ok(buildDashboardData());
    }

    private Map<String, Object> buildDashboardData() {
        Map<String, Object> overview = buildOverviewData();
        String dt = String.valueOf(overview.get("dt"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("overview", overview);
        data.put("scoreDistribution", offlineAdsDao.findScoreDistribution(dt));
        data.put("provinceRanking", offlineAdsDao.findProvinceRanking(dt, 12));
        data.put("cityRanking", offlineAdsDao.findCityRanking(dt, 10));
        data.put("ruleRanking", offlineAdsDao.findRuleRanking(dt, 12));
        data.put("timeTrend", offlineAdsDao.findTrend(dt));
        data.put("behaviorDistribution", offlineAdsDao.findBehaviorDistribution(dt));
        data.put("featureDistributions", offlineAdsDao.findFeatureDistributions(dt));
        data.put("highRiskUsers", offlineAdsDao.findEntityRanking("ads_high_risk_user_rank", dt, 10));
        data.put("deviceRanking", offlineAdsDao.findEntityRanking("ads_device_risk_rank", dt, 10));
        data.put("counterpartyRanking", offlineAdsDao.findEntityRanking("ads_counterparty_risk_rank", dt, 10));
        data.put("crossRegionFlows", offlineAdsDao.findCrossRegionFlows(dt, 10));
        data.put("highRiskTransactions", offlineAdsDao.findHighRiskTransactions(dt, 20));
        return data;
    }

    @ApiOperation("离线任务状态")
    @GetMapping("/task-status")
    public Result<List<Map<String, Object>>> taskStatus(@RequestParam(required = false) String dt) {
        return Result.ok(taskStatusService.getTaskStatus(dt));
    }

    // ==================== 离线分析触发 ====================

    @ApiOperation("触发近30天离线分析")
    @PostMapping("/analyze/recent-30-days")
    public Result<Map<String, Object>> startAnalyze() {
        Map<String, Object> previousCompletedSnapshot = buildDashboardData();
        Map<String, Object> result = analysisService.startRecent30DaysAnalysis();
        if (Boolean.TRUE.equals(result.get("success"))) {
            activeDashboardSnapshot = previousCompletedSnapshot;
            return Result.ok(result);
        }
        return Result.fail((String) result.get("message"));
    }

    @ApiOperation("查询离线分析任务状态")
    @GetMapping("/analyze/status")
    public Result<Map<String, Object>> analyzeStatus(@RequestParam(defaultValue = "") String dt) {
        if (dt.isEmpty()) {
            dt = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        return Result.ok(analysisService.getStatus(dt));
    }

    @ApiOperation("查询当前离线分析任务")
    @GetMapping("/analyze/current")
    public Result<Map<String, Object>> currentAnalyzeStatus() {
        return Result.ok(analysisService.getCurrentStatus());
    }

    @ApiOperation("请求在安全检查点暂停离线分析")
    @PostMapping("/analyze/pause")
    public Result<Map<String, Object>> pauseAnalyze() {
        try {
            Map<String, Object> result = analysisService.pauseCurrentAnalysis();
            return Boolean.TRUE.equals(result.get("success")) ? Result.ok(result) : Result.fail((String) result.get("message"));
        } catch (IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
    }

    @ApiOperation("继续已暂停的离线分析")
    @PostMapping("/analyze/resume")
    public Result<Map<String, Object>> resumeAnalyze() {
        try {
            Map<String, Object> result = analysisService.resumeCurrentAnalysis();
            return Boolean.TRUE.equals(result.get("success")) ? Result.ok(result) : Result.fail((String) result.get("message"));
        } catch (IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
    }

    @ApiOperation("请求在安全检查点结束离线分析")
    @PostMapping("/analyze/cancel")
    public Result<Map<String, Object>> cancelAnalyze() {
        try {
            Map<String, Object> result = analysisService.cancelCurrentAnalysis();
            return Boolean.TRUE.equals(result.get("success")) ? Result.ok(result) : Result.fail((String) result.get("message"));
        } catch (IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
    }
}
