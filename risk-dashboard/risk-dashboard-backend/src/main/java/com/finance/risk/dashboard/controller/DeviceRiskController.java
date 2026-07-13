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
 * 设备风险分析（离线分析模块4）
 * 数据来源：MySQL transaction_history 按 device_id 聚合
 */
@Api(tags = "11. 设备风险分析接口")
@RestController
@RequestMapping("/api/device-risk")
public class DeviceRiskController {

    @Resource
    private TransactionDao transactionDao;

    @ApiOperation("设备风险总览")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("totalDevices", transactionDao.countDistinctDevices());
        // 共享设备：关联用户 ≥ 3
        long sharedThreshold = 3;
        data.put("sharedDeviceNote", "关联用户≥" + sharedThreshold + "的设备需要从列表聚合判断");
        return Result.ok(data);
    }

    @ApiOperation("设备风险列表（按交易量降序）")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @ApiParam("页码") @RequestParam(defaultValue = "1") int page,
            @ApiParam("每页大小") @RequestParam(defaultValue = "20") int pageSize) {
        int offset = (page - 1) * pageSize;
        List<TransactionDao.DeviceAgg> rows = transactionDao.aggregateByDevice(offset, pageSize);
        Long total = transactionDao.countDistinctDevices();

        List<Map<String, Object>> list = rows.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deviceId", r.getDeviceId());
            m.put("userCount", r.getUserCount());
            m.put("transCount", r.getTransCount());
            m.put("avgScore", Math.round(r.getAvgScore() * 100.0) / 100.0);
            m.put("jailbreakCount", r.getJailbreakCount());
            m.put("cityCount", r.getCityCount());
            m.put("isShared", r.getUserCount() >= 3);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return Result.ok(result);
    }

    @ApiOperation("设备详情")
    @GetMapping("/{deviceId}")
    public Result<Map<String, Object>> detail(
            @ApiParam("设备ID") @PathVariable String deviceId) {
        // 查询该设备聚合信息（从第一条记录取近似值）
        List<TransactionDao.DeviceAgg> rows = transactionDao.aggregateByDevice(0, 999999);
        Map<String, Object> detail = rows.stream()
                .filter(r -> deviceId.equals(r.getDeviceId()))
                .findFirst()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("deviceId", r.getDeviceId());
                    m.put("userCount", r.getUserCount());
                    m.put("transCount", r.getTransCount());
                    m.put("avgScore", Math.round(r.getAvgScore() * 100.0) / 100.0);
                    m.put("jailbreakCount", r.getJailbreakCount());
                    m.put("cityCount", r.getCityCount());
                    m.put("isShared", r.getUserCount() >= 3);
                    return m;
                }).orElse(null);
        return detail != null ? Result.ok(detail) : Result.fail("设备不存在");
    }
}
