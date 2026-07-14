package com.finance.risk.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 收款方风险黑名单实体 — 对应 counterparty_blacklist 表 (12字段)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounterpartyBlacklist implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String counterpartyId;
    private String counterpartyName;
    private String riskLevel;
    private String riskType;
    private String source;
    private Double totalReceived24h;
    private Double totalReceived7d;
    private Integer uniquePayers24h;
    private Integer registrationDays;
    private String riskTags;
    private String createdTime;
    private String updateTime;
}
