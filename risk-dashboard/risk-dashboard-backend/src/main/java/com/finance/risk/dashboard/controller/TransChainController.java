package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.entity.TransChain;
import com.finance.risk.dashboard.service.TransChainService;
import com.finance.risk.dashboard.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Api(tags = "7. 资金链路追踪接口")
@RestController
@RequestMapping("/api/chain")
public class TransChainController {

    @Resource
    private TransChainService service;

    @ApiOperation("分页查询资金链路列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @ApiParam(value = "页码", defaultValue = "1") @RequestParam(defaultValue = "1") int page,
            @ApiParam(value = "每页大小", defaultValue = "20") @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(service.queryList(page, pageSize));
    }

    @ApiOperation("按链路ID查询完整链路")
    @GetMapping("/{chainId}")
    public Result<List<TransChain>> getByChainId(
            @ApiParam(value = "链路ID", required = true) @PathVariable String chainId) {
        return Result.ok(service.queryByChainId(chainId));
    }

    @ApiOperation("查询环形回流链路")
    @GetMapping("/loops")
    public Result<List<TransChain>> getLoopChains(
            @ApiParam(value = "获取数量", defaultValue = "20") @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(service.queryLoopChains(limit));
    }

    @ApiOperation("资金链路统计信息")
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.ok(service.getStats());
    }
}
