package com.finance.risk.dashboard.controller;

import com.finance.risk.dashboard.common.Constants;
import com.finance.risk.dashboard.dto.AlertInputDTO;
import com.finance.risk.dashboard.dto.MetricsInputDTO;
import com.finance.risk.dashboard.dto.ProfileInputDTO;
import com.finance.risk.dashboard.dto.TransactionInputDTO;
import com.finance.risk.dashboard.service.AlertService;
import com.finance.risk.dashboard.service.MetricsService;
import com.finance.risk.dashboard.service.ProfileService;
import com.finance.risk.dashboard.service.TransactionService;
import com.finance.risk.dashboard.vo.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * 数据接入控制器
 *
 * 【核心接口】提供给上游数据处理程序调用，将处理结果接入可视化展示端。
 *
 * 数据流向：
 *   实时链路：Kafka → Spark Streaming → 本接口 → MySQL + Redis → 前端
 *   离线链路：HDFS → Spark SQL → 本接口 → Redis → 前端
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Api(tags = "1. 数据接入接口 (供数据处理程序调用)")
@RestController
@RequestMapping(Constants.API_DATA_PREFIX)
public class DataReceiveController {

    private static final Logger log = LoggerFactory.getLogger(DataReceiveController.class);

    @Resource
    private TransactionService transactionService;

    @Resource
    private AlertService alertService;

    @Resource
    private MetricsService metricsService;

    @Resource
    private ProfileService profileService;

    // ==================== 交易流水数据接入 ====================

    @ApiOperation(value = "接收交易流水数据",
            notes = "提供给 Kafka/Spark Streaming 实时流处理后调用。"
                    + "对应需求文档 3.1 节数据字典。"
                    + "支持单条和批量两种模式。")
    @PostMapping(Constants.DATA_TRANSACTION)
    public Result<String> receiveTransaction(@Valid @RequestBody TransactionInputDTO transaction) {
        log.info("接收交易流水: transId={}, userId={}, amount={}",
                transaction.getTransId(), transaction.getUserId(), transaction.getAmount());

        boolean success = transactionService.receiveTransaction(transaction);
        if (success) {
            return Result.ok("交易流水接收成功");
        }
        return Result.fail("交易流水接收失败");
    }

    @ApiOperation(value = "批量接收交易流水数据",
            notes = "支持 Spark Streaming 微批次批量写入，提高吞吐量。")
    @PostMapping("/transaction/batch")
    public Result<String> receiveTransactionBatch(@Valid @RequestBody List<TransactionInputDTO> transactions) {
        log.info("批量接收交易流水: 共 {} 条", transactions.size());

        int count = transactionService.receiveTransactions(transactions);
        return Result.ok("成功接收 " + count + " 条交易流水");
    }

    // ==================== 风控告警数据接入 ====================

    @ApiOperation(value = "接收风控告警结果",
            notes = "提供给 Spark Streaming 实时风控引擎判定后调用。"
                    + "对应需求文档 3.3 节数据字典。")
    @PostMapping(Constants.DATA_ALERT)
    public Result<String> receiveAlert(@Valid @RequestBody AlertInputDTO alert) {
        log.info("接收告警结果: alertId={}, riskLevel={}, finalScore={}",
                alert.getAlertId(), alert.getRiskLevel(), alert.getFinalScore());

        boolean success = alertService.receiveAlert(alert);
        if (success) {
            return Result.ok("告警结果接收成功");
        }
        return Result.fail("告警结果接收失败");
    }

    @ApiOperation(value = "批量接收风控告警结果",
            notes = "支持 Spark Streaming 微批次批量写入告警结果。")
    @PostMapping("/alert/batch")
    public Result<String> receiveAlertBatch(@Valid @RequestBody List<AlertInputDTO> alerts) {
        log.info("批量接收告警结果: 共 {} 条", alerts.size());

        int count = alertService.receiveAlerts(alerts);
        return Result.ok("成功接收 " + count + " 条告警结果");
    }

    // ==================== 指标快照数据接入 ====================

    @ApiOperation(value = "接收实时指标快照",
            notes = "提供给 Spark Streaming 窗口聚合计算后调用，"
                    + "用于仪表盘实时指标展示。")
    @PostMapping(Constants.DATA_METRICS)
    public Result<String> receiveMetrics(@Valid @RequestBody MetricsInputDTO metrics) {
        log.info("接收指标快照: totalTxns={}, avgScore={}, avgLatency={}",
                metrics.getTotalTransactions(), metrics.getAvgRiskScore(), metrics.getAvgLatency());

        boolean success = metricsService.receiveMetrics(metrics);
        if (success) {
            return Result.ok("指标快照接收成功");
        }
        return Result.fail("指标快照接收失败");
    }

    // ==================== 用户画像数据接入 ====================

    @ApiOperation(value = "接收用户画像数据",
            notes = "提供给 HDFS/Spark SQL 离线批处理计算后调用。"
                    + "对应需求文档 3.2 节数据字典。"
                    + "画像数据会被写入Redis缓存供实时风控引擎查询。")
    @PostMapping(Constants.DATA_PROFILE)
    public Result<String> receiveProfile(@Valid @RequestBody ProfileInputDTO profile) {
        log.info("接收用户画像: userId={}, avgAmt30d={}",
                profile.getUserId(), profile.getAvgAmt30d());

        boolean success = profileService.receiveProfile(profile);
        if (success) {
            return Result.ok("用户画像接收并缓存成功");
        }
        return Result.fail("用户画像接收失败");
    }
}
