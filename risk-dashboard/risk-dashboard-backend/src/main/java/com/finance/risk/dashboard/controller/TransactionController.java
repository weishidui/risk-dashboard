package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.entity.Transaction;
import com.finance.risk.dashboard.service.TransactionService;
import com.finance.risk.dashboard.vo.DistributionVO;
import com.finance.risk.dashboard.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 交易流水控制器
 * 提供交易流水查询和统计接口
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Api(tags = "4. 交易流水接口 (供前端页面调用)")
@RestController
@RequestMapping(Constants.API_TRANSACTION_PREFIX)
public class TransactionController {

    @Resource
    private TransactionService transactionService;

    @ApiOperation(value = "获取最新交易流水 (瀑布流展示)",
            notes = "按接收时间倒序返回最近交易数据。")
    @GetMapping("/recent")
    public Result<List<Transaction>> getRecentTransactions(
            @ApiParam(value = "获取数量", defaultValue = "50") @RequestParam(defaultValue = "50") int limit) {
        List<Transaction> list = transactionService.getRecentTransactions(limit);
        return Result.ok(list);
    }

    @ApiOperation(value = "城市交易量分布统计",
            notes = "用于柱状图展示各城市交易活跃度。")
    @GetMapping("/stat/city")
    public Result<List<DistributionVO>> statByCity(
            @ApiParam(value = "数量限制", defaultValue = "10") @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(transactionService.countByCity(limit));
    }
}
