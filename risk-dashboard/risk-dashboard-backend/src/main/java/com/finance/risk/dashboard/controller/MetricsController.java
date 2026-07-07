package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.entity.MetricsSnapshot;
import com.finance.risk.dashboard.service.MetricsService;
import com.finance.risk.dashboard.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 指标统计控制器
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Api(tags = "5. 指标统计接口 (供前端页面调用)")
@RestController
@RequestMapping(Constants.API_METRICS_PREFIX)
public class MetricsController {

    @Resource
    private MetricsService metricsService;

    @ApiOperation(value = "获取最新指标快照",
            notes = "返回当前最新的实时风控指标数据。")
    @GetMapping("/latest")
    public Result<MetricsSnapshot> getLatestMetrics() {
        MetricsSnapshot metrics = metricsService.getLatestMetrics();
        return Result.ok(metrics);
    }

    @ApiOperation(value = "获取趋势数据",
            notes = "按小时获取近N小时的指标变化趋势，用于折线图展示。")
    @GetMapping("/trend")
    public Result<List<MetricsSnapshot>> getMetricsTrend(
            @ApiParam(value = "最近N小时", defaultValue = "24") @RequestParam(defaultValue = "24") int hours) {
        List<MetricsSnapshot> trend = metricsService.getMetricsTrend(hours);
        return Result.ok(trend);
    }
}
