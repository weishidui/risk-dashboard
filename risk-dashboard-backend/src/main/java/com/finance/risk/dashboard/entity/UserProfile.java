package com.finance.risk.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户历史行为画像实体 — 对应 user_profile 表 (24字段)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String userId;

    // 行为基线 (6字段)
    private Double avgAmt30d;
    private String commonCities;
    private String commonDevs;
    private String commonPayChannels;
    private String commonTransTypes;
    private String commonCounterparties;

    // 历史快照 (4字段)
    private Long lastTransTs;
    private String lastCity;
    private String lastIp;
    private Long lastLoginTime;

    // 账户信息 (6字段)
    private Long registrationTime;
    private Double totalBalance;
    private Double singleLimit;
    private Double dailyLimit;
    private Double monthlyLimit;
    private String accountStatus;

    // 累计统计 (5字段)
    private Integer loginCount24h;
    private Integer transCount24h;
    private Double transAmount24h;
    private Integer transCount7d;
    private Integer cancelRetryCount;

    // 风险标记 (2字段)
    private String riskTags;
    private Integer riskScore;

    private String updateTime;
}
