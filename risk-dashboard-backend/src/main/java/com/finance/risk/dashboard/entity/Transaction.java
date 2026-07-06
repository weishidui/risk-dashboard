package com.finance.risk.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 交易流水实体 — 对应 transaction_history 表 (31字段)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String transId;
    private String userId;
    private Double amount;
    private Long transTimestamp;
    private String city;
    private String geoLocation;
    private String deviceId;
    private String networkType;
    private Integer devScore;

    // 设备/环境维度 (10字段)
    private String ipAddress;
    private String osType;
    private String osVersion;
    private String screenResolution;
    private Integer batteryLevel;
    private Integer rootJailbreak;
    private String simOperator;
    private String userAgent;
    private String dnsServer;
    private String wifiSsid;

    // 交易行为维度 (6字段)
    private String transType;
    private String payChannel;
    private String inputMethod;
    private Long clickDuration;
    private String note;
    private String pageUrl;

    // 收款方维度 (3字段)
    private String counterpartyId;
    private String counterpartyName;
    private String counterpartyBank;

    // 身份/会话维度 (2字段)
    private String loginSessionId;
    private Integer loginFailCount;

    private String createTime;
}
