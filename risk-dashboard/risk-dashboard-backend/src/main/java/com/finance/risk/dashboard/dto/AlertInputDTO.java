package com.finance.risk.dashboard.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
@ApiModel(description = "风控预警结果数据接入对象")
public class AlertInputDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank @ApiModelProperty(value = "预警编号", required = true)
    private String alertId;
    @NotBlank @ApiModelProperty(value = "关联交易流水号", required = true)
    private String transId;
    @NotBlank @ApiModelProperty(value = "用户标识", required = true)
    private String userId;
    @NotNull @ApiModelProperty(value = "交易金额", required = true)
    private Double amount;
    @NotBlank @ApiModelProperty(value = "交易城市", required = true)
    private String city;
    @NotBlank @ApiModelProperty(value = "触发规则标签(分号分隔)", required = true)
    private String hitRules;
    @NotNull @ApiModelProperty(value = "综合风险评分(0-300+)", required = true)
    private Integer finalScore;
    @NotBlank @ApiModelProperty(value = "风险等级(低危/中危/高危/极度危险)", required = true)
    private String riskLevel;
    @NotBlank @ApiModelProperty(value = "告警位置", required = true)
    private String alertLoc;

    @ApiModelProperty(value = "处理状态(pending/processing/verified/blocked/closed)")
    private String status;
    @ApiModelProperty(value = "关联收款方ID")
    private String counterpartyId;
    @ApiModelProperty(value = "交易IP地址")
    private String ipAddress;
    @ApiModelProperty(value = "是否新设备(0=否 1=是)")
    private Integer isNewDevice;
    @ApiModelProperty(value = "是否首次收款方(0=否 1=是)")
    private Integer isNewCounterparty;
    @ApiModelProperty(value = "关联资金链路ID")
    private String chainId;

    @ApiModelProperty(value = "Kafka 原始 JSON 报文")
    private String rawJson;
}
