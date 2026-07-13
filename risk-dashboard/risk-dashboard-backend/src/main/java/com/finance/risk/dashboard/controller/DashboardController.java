package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.service.MetricsService;
import com.finance.risk.dashboard.vo.DashboardVO;
import com.finance.risk.dashboard.vo.RealtimeMetricsVO;
import com.finance.risk.dashboard.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.*;

/**
 * 仪表盘控制器
 * 提供前端大屏展示所需的所有聚合数据
 */
@Api(tags = "2. 仪表盘接口 (供前端大屏调用)")
@RestController
@RequestMapping(Constants.API_DASHBOARD_PREFIX)
public class DashboardController {

    @Resource private MetricsService metricsService;
    @Resource private DataSource dataSource;
    @Resource private RedisTemplate<String, Object> redisTemplate;

    @ApiOperation("获取仪表盘首页综合数据")
    @GetMapping("/overview")
    public Result<DashboardVO> getDashboardOverview() {
        return Result.ok(metricsService.getDashboardData());
    }

    @ApiOperation("获取近60秒实时指标")
    @GetMapping("/realtime-metrics")
    public Result<RealtimeMetricsVO> getRealtimeMetrics() {
        return Result.ok(metricsService.getRealtimeMetrics());
    }

    @ApiOperation("获取系统链路健康状态")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();

        // MySQL
        try (Connection conn = dataSource.getConnection()) {
            status.put("mysql", conn.isValid(2) ? "ok" : "fail");
        } catch (Exception e) { status.put("mysql", "fail"); }

        // Redis
        try {
            redisTemplate.opsForValue().get("health:check");
            status.put("redis", "ok");
        } catch (Exception e) { status.put("redis", "fail"); }

        // 后端服务
        status.put("server", "ok");
        status.put("uptime", System.currentTimeMillis());

        return Result.ok(status);
    }
}
