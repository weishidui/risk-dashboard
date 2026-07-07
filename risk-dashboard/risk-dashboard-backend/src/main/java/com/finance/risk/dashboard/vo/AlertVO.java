package com.finance.risk.dashboard.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "告警展示对象")
public class AlertVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "预警编号")
    private String alertId;
    @ApiModelProperty(value = "交易流水号")
    private String transId;
    @ApiModelProperty(value = "用户标识(脱敏)")
    private String userId;
    @ApiModelProperty(value = "交易金额")
    private Double amount;
    @ApiModelProperty(value = "交易城市")
    private String city;
    @ApiModelProperty(value = "触发规则标签")
    private String hitRules;
    @ApiModelProperty(value = "综合风险评分")
    private Integer finalScore;
    @ApiModelProperty(value = "风险等级")
    private String riskLevel;
    @ApiModelProperty(value = "告警位置")
    private String alertLoc;
    @ApiModelProperty(value = "处理动作(PASS/VERIFY/BLOCK)")
    private String action;

    // 告警处理工作流
    @ApiModelProperty(value = "处理状态(pending/processing/verified/blocked/closed)")
    private String status;
    @ApiModelProperty(value = "处理人")
    private String handler;
    @ApiModelProperty(value = "处理时间戳")
    private Long handleTime;
    @ApiModelProperty(value = "处理备注")
    private String handleRemark;

    // 风控详情
    @ApiModelProperty(value = "收款方ID")
    private String counterpartyId;
    @ApiModelProperty(value = "交易IP")
    private String ipAddress;
    @ApiModelProperty(value = "是否新设备")
    private Integer isNewDevice;
    @ApiModelProperty(value = "是否首次收款方")
    private Integer isNewCounterparty;
    @ApiModelProperty(value = "资金链路ID")
    private String chainId;

    @ApiModelProperty(value = "告警入库时间")
    private String createTime;
}
