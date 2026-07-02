package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.service.AlertService;
import com.finance.risk.dashboard.vo.AlertVO;
import com.finance.risk.dashboard.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 告警管理控制器 — 查询 risk_alert 表
 */
@Api(tags = "3. 告警管理接口")
@RestController
@RequestMapping(Constants.API_ALERT_PREFIX)
public class AlertController {

    @Resource
    private AlertService alertService;

    @ApiOperation(value = "分页查询告警列表", notes = "支持按风险等级、时间范围筛选。")
    @GetMapping("/list")
    public Result<Map<String, Object>> queryAlertList(
            @ApiParam(value = "风险等级 (高危/中危/低危)") @RequestParam(required = false) String riskLevel,
            @ApiParam(value = "开始时间 (yyyy-MM-dd HH:mm:ss)") @RequestParam(required = false) String startTime,
            @ApiParam(value = "结束时间 (yyyy-MM-dd HH:mm:ss)") @RequestParam(required = false) String endTime,
            @ApiParam(value = "页码", defaultValue = "1") @RequestParam(defaultValue = "1") int page,
            @ApiParam(value = "每页大小", defaultValue = "20") @RequestParam(defaultValue = "20") int pageSize) {

        Map<String, Object> result = alertService.queryAlertList(
                riskLevel, startTime, endTime, page, pageSize);
        return Result.ok(result);
    }

    @ApiOperation(value = "获取最新告警列表", notes = "仪表盘实时告警滚动展示。")
    @GetMapping("/recent")
    public Result<List<AlertVO>> getRecentAlerts(
            @ApiParam(value = "获取数量", defaultValue = "20") @RequestParam(defaultValue = "20") int limit) {
        List<AlertVO> alerts = alertService.getRecentAlerts(limit);
        return Result.ok(alerts);
    }

    @ApiOperation(value = "风险等级分布统计", notes = "用于饼图展示高/中/低危分布。")
    @GetMapping("/stat/risk-level")
    public Result<List<Map<String, Object>>> statByRiskLevel() {
        return Result.ok(alertService.countByRiskLevel());
    }

    @ApiOperation(value = "风险类型分布统计", notes = "用于饼图展示各类触发规则占比。")
    @GetMapping("/stat/rule-type")
    public Result<List<Map<String, Object>>> statByRuleType() {
        return Result.ok(alertService.countByHitRule());
    }

    @ApiOperation(value = "高危告警城市分布", notes = "用于地图展示高危告警的城市分布。")
    @GetMapping("/stat/city-risk")
    public Result<List<Map<String, Object>>> statCityRisk(
            @ApiParam(value = "数量限制", defaultValue = "20") @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(alertService.countHighRiskByCity(limit));
    }
}
