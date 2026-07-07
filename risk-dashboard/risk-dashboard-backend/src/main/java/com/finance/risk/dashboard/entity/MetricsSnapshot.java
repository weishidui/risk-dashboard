package com.finance.risk.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 实时指标快照实体
 * 用于仪表盘核心指标统计
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricsSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 统计时间戳 */
    private Long snapshotTime;

    /** 当前秒级交易总量 */
    private Long totalTransactions;

    /** 正常放行数 */
    private Long passCount;

    /** 待核验数 */
    private Long verifyCount;

    /** 拦截数 */
    private Long blockCount;

    /** 高危告警数 */
    private Long highRiskCount;

    /** 中危告警数 */
    private Long mediumRiskCount;

    /** 低危告警数 */
    private Long lowRiskCount;

    /** 平均风险评分 */
    private Double avgRiskScore;

    /** 设备环境异常数 */
    private Long envRiskCount;

    /** 金额异常数 */
    private Long amountRiskCount;

    /** 异地瞬移异常数 */
    private Long teleportRiskCount;

    /** 地理偏离异常数 */
    private Long geoRiskCount;

    /** 平均处理延迟 (毫秒) */
    private Double avgLatency;
}
