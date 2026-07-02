package com.finance.risk.dashboard.common;

/**
 * 风险等级枚举
 * 对应需求文档 4.2 节评分体系：
 *   总分 < 60  → 低危 (绿色/放行)
 *   60 ≤ 总分 ≤ 80 → 中危 (黄色/核验)
 *   总分 > 80 → 高危 (红色/拦截)
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
public enum RiskLevelEnum {

    /** 低危 — 绿色放行 (score < 60) */
    LOW("低危", "green", "PASS", "放行", 0, 59),

    /** 中危 — 黄色核验 (60 ≤ score ≤ 80) */
    MEDIUM("中危", "yellow", "VERIFY", "核验", 60, 80),

    /** 高危 — 红色拦截 (score > 80) */
    HIGH("高危", "red", "BLOCK", "拦截", 81, 100);

    /** 中文名称 */
    private final String label;

    /** 显示颜色 */
    private final String color;

    /** 处理动作代码 */
    private final String actionCode;

    /** 处理动作名称 */
    private final String actionName;

    /** 分值下限 (包含) */
    private final int minScore;

    /** 分值上限 (包含) */
    private final int maxScore;

    RiskLevelEnum(String label, String color, String actionCode,
                  String actionName, int minScore, int maxScore) {
        this.label = label;
        this.color = color;
        this.actionCode = actionCode;
        this.actionName = actionName;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    /**
     * 根据综合风险评分计算风险等级
     *
     * @param finalScore 综合风险评分 (0-100)
     * @return 对应的风险等级枚举
     */
    public static RiskLevelEnum fromScore(int finalScore) {
        if (finalScore < 60) {
            return LOW;
        } else if (finalScore <= 80) {
            return MEDIUM;
        } else {
            return HIGH;
        }
    }

    /**
     * 根据分值获取对应的处理动作
     *
     * @param finalScore 综合风险评分
     * @return 处理动作代码 (PASS/VERIFY/BLOCK)
     */
    public static String getActionByScore(int finalScore) {
        return fromScore(finalScore).getActionCode();
    }

    // ========== Getters ==========
    public String getLabel() { return label; }
    public String getColor() { return color; }
    public String getActionCode() { return actionCode; }
    public String getActionName() { return actionName; }
    public int getMinScore() { return minScore; }
    public int getMaxScore() { return maxScore; }
}
