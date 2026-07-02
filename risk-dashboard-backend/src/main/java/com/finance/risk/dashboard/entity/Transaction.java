package com.finance.risk.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 交易流水实体 — 对应 transaction_history 表
 * 字段严格对齐《数据库结构.md》第 2 节
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
    /** 交易时间戳 (13位毫秒级)，对应字段 trans_timestamp */
    private Long transTimestamp;
    private String city;
    /** 地理经纬度，格式如 "116.3,39.9" */
    private String geoLocation;
    private String deviceId;
    /** 网络类型：WiFi / 4G / 5G / VPN */
    private String networkType;
    /** 设备安全分，范围 0-100 */
    private Integer devScore;
    /** 数据入库时间 */
    private String createTime;
}
