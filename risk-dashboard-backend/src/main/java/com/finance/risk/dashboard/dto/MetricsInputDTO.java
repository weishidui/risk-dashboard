package com.finance.risk.dashboard.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 实时指标数据接入 DTO
 * 提供给 Spark Streaming 聚合计算后写入 (供仪表盘展示)
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Data
@ApiModel(description = "实时指标数据接入对象")
public class MetricsInputDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "统计时间戳不能为空")
    @ApiModelProperty(value = "统计时间戳 (13位毫秒级)", required = true, example = "1719734400000")
    private Long snapshotTime;

    @NotNull(message = "交易总量不能为空")
    @ApiModelProperty(value = "当前秒级交易总量", required = true, example = "1200")
    private Long totalTransactions;

    @ApiModelProperty(value = "正常放行数", example = "800")
    private Long passCount;

    @ApiModelProperty(value = "待核验数", example = "300")
    private Long verifyCount;

    @ApiModelProperty(value = "拦截数", example = "100")
    private Long blockCount;

    @ApiModelProperty(value = "高危告警数", example = "100")
    private Long highRiskCount;

    @ApiModelProperty(value = "中危告警数", example = "300")
    private Long mediumRiskCount;

    @ApiModelProperty(value = "低危告警数", example = "800")
    private Long lowRiskCount;

    @ApiModelProperty(value = "平均风险评分", example = "45.5")
    private Double avgRiskScore;

    @ApiModelProperty(value = "设备环境异常数", example = "50")
    private Long envRiskCount;

    @ApiModelProperty(value = "金额异常数", example = "80")
    private Long amountRiskCount;

    @ApiModelProperty(value = "异地瞬移异常数", example = "15")
    private Long teleportRiskCount;

    @ApiModelProperty(value = "地理偏离异常数", example = "120")
    private Long geoRiskCount;

    @ApiModelProperty(value = "平均处理延迟(毫秒)", example = "350")
    private Double avgLatency;
}
