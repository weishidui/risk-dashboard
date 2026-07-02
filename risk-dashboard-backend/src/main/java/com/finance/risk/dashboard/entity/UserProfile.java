package com.finance.risk.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户历史行为画像实体 — 对应 user_profile 表
 * 字段严格对齐《数据库结构.md》第 3 节
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String userId;
    /** 过去30天单笔平均金额 */
    private Double avgAmt30d;
    /** 过去3个月高频城市 Top3，逗号分隔 */
    private String commonCities;
    /** 用户常用设备 ID 列表，逗号分隔 */
    private String commonDevs;
    /** 上一笔交易发生的毫秒级时间戳 */
    private Long lastTransTs;
    /** 上一笔交易发生城市 */
    private String lastCity;
    /** 画像更新时间 */
    private String updateTime;
}
