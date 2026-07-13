package com.finance.risk.dashboard.controller;

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
 * 交易行为统计（离线分析模块6）
 */
@Api(tags = "10. 交易行为统计接口")
@RestController
@RequestMapping("/api/transaction-stats")
public class TransactionStatsController {

    @Resource
    private TransactionDao transactionDao;

    @ApiOperation("每日交易量趋势")
    @GetMapping("/daily-trend")
    public Result<List<Map<String, Object>>> dailyTrend(
            @ApiParam("往前推天数") @RequestParam(defaultValue = "30") int days) {
        long since = System.currentTimeMillis() - days * 86400000L;
        List<TransactionDao.DailyCount> rows = transactionDao.countByDay(since);
        List<Map<String, Object>> list = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", r.getDt());
            m.put("count", r.getCnt());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }

    @ApiOperation("支付渠道分布")
    @GetMapping("/pay-channel")
    public Result<List<Map<String, Object>>> payChannelDist() {
        List<TransactionDao.ChannelCount> rows = transactionDao.countByPayChannel();
        List<Map<String, Object>> list = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", labelPayChannel(r.getPayChannel()));
            m.put("value", r.getCnt());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }

    @ApiOperation("交易类型分布")
    @GetMapping("/trans-type")
    public Result<List<Map<String, Object>>> transTypeDist() {
        List<TransactionDao.TypeCount> rows = transactionDao.countByTransType();
        List<Map<String, Object>> list = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.getTransType());
            m.put("value", r.getCnt());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }

    @ApiOperation("金额区间分布")
    @GetMapping("/amount-range")
    public Result<List<Map<String, Object>>> amountRangeDist() {
        List<TransactionDao.RangeCount> rows = transactionDao.countByAmountRange();
        List<Map<String, Object>> list = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.getRng());
            m.put("value", r.getCnt());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }

    @ApiOperation("城市交易量排名")
    @GetMapping("/city")
    public Result<List<Map<String, Object>>> cityRank(
            @ApiParam("数量") @RequestParam(defaultValue = "15") int limit) {
        long since = System.currentTimeMillis() - 30 * 86400000L;
        List<TransactionDao.CityCount> rows = transactionDao.countByCity(since, limit);
        List<Map<String, Object>> list = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.getCity());
            m.put("value", r.getCnt());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }

    @ApiOperation("总览统计")
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        long now = System.currentTimeMillis();
        long since30d = now - 30 * 86400000L;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalTransactions", transactionDao.countByTimeRange(0L, now));
        data.put("recent30dTransactions", transactionDao.countByTimeRange(since30d, now));
        data.put("distinctUsers", transactionDao.countDistinctUsers(0L, now));
        data.put("distinctDevices", transactionDao.countDistinctDevices());
        data.put("distinctCities", transactionDao.countByCity(0L, 999).size());
        return Result.ok(data);
    }

    private String labelPayChannel(String code) {
        if (code == null) return "未知";
        switch (code) {
            case "bank_card": return "银行卡";
            case "balance": return "余额";
            case "wechat": return "微信";
            case "alipay": return "支付宝";
            default: return code;
        }
    }
}
