package com.finance.risk.dashboard.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 风控预警结果数据接入 DTO — 对应 risk_alert 表
 */
@Data
@ApiModel(description = "风控预警结果数据接入对象")
public class AlertInputDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "预警编号不能为空")
    @ApiModelProperty(value = "预警编号 (唯一告警ID)", required = true, example = "ALT20260630120000001")
    private String alertId;

    @NotBlank(message = "关联交易流水号不能为空")
    @ApiModelProperty(value = "关联交易流水号", required = true)
    private String transId;

    @NotBlank(message = "用户标识不能为空")
    @ApiModelProperty(value = "用户标识", required = true)
    private String userId;

    @NotNull(message = "交易金额不能为空")
    @ApiModelProperty(value = "交易金额", required = true)
    private Double amount;

    @NotBlank(message = "交易城市不能为空")
    @ApiModelProperty(value = "交易城市", required = true, example = "深圳")
    private String city;

    @NotBlank(message = "触发规则标签不能为空")
    @ApiModelProperty(value = "触发规则标签 (多个规则用分号分隔)", required = true,
            example = "金额突变;异地瞬移")
    private String hitRules;

    @NotNull(message = "综合风险评分不能为空")
    @Min(value = 0, message = "风险评分最小为0")
    @Max(value = 100, message = "风险评分最大为100")
    @ApiModelProperty(value = "综合风险评分 (0-100)", required = true, example = "85")
    private Integer finalScore;

    @NotBlank(message = "风险等级不能为空")
    @ApiModelProperty(value = "风险等级 (高危/中危/低危)", required = true,
            example = "高危", allowableValues = "高危,中危,低危")
    private String riskLevel;

    @NotBlank(message = "告警位置不能为空")
    @ApiModelProperty(value = "告警位置，用于大屏地图展示", required = true, example = "深圳")
    private String alertLoc;

    @ApiModelProperty(value = "Kafka 原始 JSON 报文")
    private String rawJson;
}
