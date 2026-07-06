package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.entity.CounterpartyBlacklist;
import com.finance.risk.dashboard.service.CounterpartyBlacklistService;
import com.finance.risk.dashboard.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Api(tags = "6. 收款方黑名单管理接口")
@RestController
@RequestMapping("/api/blacklist")
public class CounterpartyBlacklistController {

    @Resource
    private CounterpartyBlacklistService service;

    @ApiOperation("分页查询黑名单列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @ApiParam(value = "页码", defaultValue = "1") @RequestParam(defaultValue = "1") int page,
            @ApiParam(value = "每页大小", defaultValue = "20") @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.queryList(page, pageSize));
    }

    @ApiOperation("按风险等级筛选黑名单")
    @GetMapping("/by-level")
    public Result<Map<String, Object>> byRiskLevel(
            @ApiParam(value = "风险等级(high/medium/low)", required = true) @RequestParam String riskLevel,
            @ApiParam(value = "页码", defaultValue = "1") @RequestParam(defaultValue = "1") int page,
            @ApiParam(value = "每页大小", defaultValue = "20") @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.queryByRiskLevel(riskLevel, page, pageSize));
    }

    @ApiOperation("收款方黑名单风险等级分布")
    @GetMapping("/stat/risk-level")
    public Result<List<Map<String, Object>>> statByRiskLevel() {
        return Result.ok(service.countByRiskLevel());
    }

    @ApiOperation("按ID查询收款方详情")
    @GetMapping("/{counterpartyId}")
    public Result<CounterpartyBlacklist> getById(
            @ApiParam(value = "收款方账户ID", required = true) @PathVariable String counterpartyId) {
        CounterpartyBlacklist entity = service.getByCounterpartyId(counterpartyId);
        return entity != null ? Result.ok(entity) : Result.fail("收款方不存在");
    }
}
