package com.finance.risk.dashboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 风控告警结果实体 — 对应 risk_alert 表
 * 字段严格对齐《数据库结构.md》第 4 节
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
    /** 交易城市 */
    private String city;
    /** 命中的风险规则标签，多个用分号分隔 */
    private String hitRules;
    /** 综合风险评分 0-100 */
    private Integer finalScore;
    /** 风险等级: 低危 / 中危 / 高危 */
    private String riskLevel;
    /** 告警位置，用于大屏地图展示 */
    private String alertLoc;
    /** Kafka 原始 JSON 报文 */
    private String rawJson;
    /** 告警入库时间 */
    private String createTime;
}
