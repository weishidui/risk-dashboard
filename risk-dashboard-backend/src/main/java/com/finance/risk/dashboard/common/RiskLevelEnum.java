package com.finance.risk.dashboard.common;

public enum RiskLevelEnum {

    LOW("低危", "green", "PASS", "放行", 0, 40),
    MEDIUM("中危", "yellow", "VERIFY", "核验", 41, 70),
    HIGH("高危", "red", "BLOCK", "拦截", 71, 120),
    CRITICAL("极度危险", "darkred", "BLOCK", "自动拦截+冻结", 121, Integer.MAX_VALUE),
    HARD_BLOCK("直接拦截", "darkred", "BLOCK", "命中硬阻断直接拦截", -1, -1);

    private final String label;
    private final String color;
    private final String actionCode;
    private final String actionName;
    private final int minScore;
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

    public static RiskLevelEnum fromScore(int finalScore) {
        if (finalScore > 120) return CRITICAL;
        if (finalScore > 70) return HIGH;
        if (finalScore > 40) return MEDIUM;
        return LOW;
    }

    public static String getActionByScore(int finalScore) {
        return fromScore(finalScore).getActionCode();
    }

    public String getLabel() { return label; }
    public String getColor() { return color; }
    public String getActionCode() { return actionCode; }
    public String getActionName() { return actionName; }
    public int getMinScore() { return minScore; }
    public int getMaxScore() { return maxScore; }
}
