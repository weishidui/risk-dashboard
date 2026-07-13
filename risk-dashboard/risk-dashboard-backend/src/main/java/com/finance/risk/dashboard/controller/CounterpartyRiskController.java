package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.dao.CounterpartyBlacklistDao;
import com.finance.risk.dashboard.dao.TransactionDao;
import com.finance.risk.dashboard.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 收款方风险分析（离线分析模块5）
 */
@Api(tags = "12. 收款方风险分析接口")
@RestController
@RequestMapping("/api/counterparty-risk")
public class CounterpartyRiskController {

    @Resource
    private TransactionDao transactionDao;
    @Resource
    private CounterpartyBlacklistDao blacklistDao;

    @ApiOperation("收款方风险总览")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalCounterparties", transactionDao.countDistinctCounterparties());
        data.put("blacklistCount", blacklistDao.count());
        // 多对一集中收款：付款方 ≥ 20 的收款方数
        List<TransactionDao.CounterpartyAgg> all = transactionDao.aggregateByCounterparty(0, 999999);
        long manyToOne = all.stream().filter(a -> a.getPayerCount() >= 20).count();
        data.put("manyToOneCount", manyToOne);
        return Result.ok(data);
    }

    @ApiOperation("收款方风险列表")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @ApiParam("页码") @RequestParam(defaultValue = "1") int page,
            @ApiParam("每页大小") @RequestParam(defaultValue = "20") int pageSize) {
        int offset = (page - 1) * pageSize;
        List<TransactionDao.CounterpartyAgg> rows = transactionDao.aggregateByCounterparty(offset, pageSize);
        Long total = transactionDao.countDistinctCounterparties();

        List<Map<String, Object>> list = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("counterpartyId", r.getCounterpartyId());
            m.put("transCount", r.getTransCount());
            m.put("totalAmount", r.getTotalAmount());
            m.put("payerCount", r.getPayerCount());
            m.put("isManyToOne", r.getPayerCount() >= 20);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return Result.ok(result);
    }
}
