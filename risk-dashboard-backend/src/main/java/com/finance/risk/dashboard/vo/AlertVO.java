package com.finance.risk.dashboard.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 告警列表视图对象 — 对应 risk_alert 表前端的展示
 */
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

    @ApiModelProperty(value = "用户标识 (脱敏后)")
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

    /** 处理动作：由 risk_level 推导 (低危→PASS, 中危→VERIFY, 高危→BLOCK) */
    @ApiModelProperty(value = "处理动作 (PASS/VERIFY/BLOCK)")
    private String action;

    @ApiModelProperty(value = "告警入库时间 (格式化后)")
    private String createTime;
}
