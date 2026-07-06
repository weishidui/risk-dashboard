package com.finance.risk.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 风控告警结果实体 — 对应 risk_alert 表 (20字段)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String alertId;
    private String transId;
    private String userId;
    private Double amount;
    private String city;
    private String hitRules;
    private Integer finalScore;
    private String riskLevel;
    private String alertLoc;

    // 告警处理工作流
    private String status;
    private String handler;
    private Long handleTime;
    private String handleRemark;

    // 风控详情
    private String counterpartyId;
    private String ipAddress;
    private Integer isNewDevice;
    private Integer isNewCounterparty;
    private String chainId;

    private String rawJson;
    private String createTime;
}
