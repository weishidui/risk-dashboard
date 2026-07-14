package com.finance.risk.dashboard.common;

/**
 * 风险规则类型枚举
 * 对应需求文档 4.2 节风险评分规则逻辑
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
public enum RuleTypeEnum {

    /** 金额异常 — 当前消费超历史均值3倍，+30分 */
    AMOUNT_ANOMALY("金额突变", "amount_anomaly", 30,
            "当前交易金额超过近30天平均消费金额的3倍"),

    /** 地理偏离 — 城市不在常用城市列表中，+20分 */
    GEO_DEVIATION("地理偏离", "geo_deviation", 20,
            "交易城市不在用户近3个月高频城市Top3列表中"),

    /** 异地瞬移 — 坐标变化速度 > 1000km/h，+80分 */
    TELEPORT("异地瞬移", "teleport", 80,
            "当前交易坐标与上次交易坐标间移动速度超过1000km/h"),

    /** 环境风险 — VPN或设备安全分过低，+40分 */
    ENV_RISK("环境风险", "env_risk", 40,
            "网络类型为VPN或设备安全评分低于50分");

    /** 规则中文名 */
    private final String label;

    /** 规则代码 (用于数据库存储) */
    private final String code;

    /** 风险分值 */
    private final int score;

    /** 规则描述 */
    private final String description;

    RuleTypeEnum(String label, String code, int score, String description) {
        this.label = label;
        this.code = code;
        this.score = score;
        this.description = description;
    }

    // ========== Getters ==========
    public String getLabel() { return label; }
    public String getCode() { return code; }
    public int getScore() { return score; }
    public String getDescription() { return description; }

    /**
     * 根据规则代码查找枚举
     */
    public static RuleTypeEnum fromCode(String code) {
        for (RuleTypeEnum rule : values()) {
            if (rule.getCode().equals(code)) {
                return rule;
            }
        }
        return null;
    }
}
