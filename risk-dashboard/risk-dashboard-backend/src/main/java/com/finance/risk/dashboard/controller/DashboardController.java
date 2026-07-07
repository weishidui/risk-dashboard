package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.service.MetricsService;
import com.finance.risk.dashboard.vo.DashboardVO;
import com.finance.risk.dashboard.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 仪表盘控制器
 * 提供前端大屏展示所需的所有聚合数据
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Api(tags = "2. 仪表盘接口 (供前端大屏调用)")
@RestController
@RequestMapping(Constants.API_DASHBOARD_PREFIX)
public class DashboardController {

    @Resource
    private MetricsService metricsService;

    @ApiOperation(value = "获取仪表盘首页综合数据",
            notes = "聚合所有实时指标、趋势数据、风险分布、地理告警和最新告警列表。"
                    + "前端仪表盘通过此接口一次性获取首页所有数据。")
    @GetMapping("/overview")
    public Result<DashboardVO> getDashboardOverview() {
        DashboardVO dashboard = metricsService.getDashboardData();
        return Result.ok(dashboard);
    }

    @ApiOperation(value = "获取系统健康状态",
            notes = "返回系统运行状态、数据连接状态等。")
    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("系统运行正常");
    }
}
