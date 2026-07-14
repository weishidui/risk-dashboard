package com.finance.risk.dashboard.common;

/**
 * 系统常量定义
 *
 * @author Risk Dashboard Team
 * @since 1.0.0
 */
public final class Constants {

    private Constants() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }

    // ==================== 数据接入接口路径 ====================
    /** 数据处理结果接入根路径 */
    public static final String API_DATA_PREFIX = "/api/data";

    /** 交易流水接入 */
    public static final String DATA_TRANSACTION = "/transaction";

    /** 风控告警接入 */
    public static final String DATA_ALERT = "/alert";

    /** 用户画像接入 */
    public static final String DATA_PROFILE = "/profile";

    /** 指标快照接入 */
    public static final String DATA_METRICS = "/metrics";

    // ==================== 前端展示接口路径 ====================
    public static final String API_DASHBOARD_PREFIX = "/api/dashboard";
    public static final String API_ALERT_PREFIX = "/api/alert";
    public static final String API_TRANSACTION_PREFIX = "/api/transaction";
    public static final String API_METRICS_PREFIX = "/api/metrics";

    // ==================== Redis Key 前缀 ====================
    /** 用户画像缓存前缀 */
    public static final String REDIS_PROFILE_PREFIX = "risk:profile:";

    /** 实时交易缓存前缀 */
    public static final String REDIS_TRANSACTION_PREFIX = "risk:trans:";

    /** 最新指标快照 */
    public static final String REDIS_LATEST_METRICS = "risk:metrics:latest";

    /** 实时告警列表 (最近100条) */
    public static final String REDIS_ALERT_LIST = "risk:alert:list";

    // ==================== 业务常量 ====================
    /** 金额异常倍数阈值 (当前消费 > 历史均值 * 3) */
    public static final double AMOUNT_ANOMALY_RATIO = 3.0;

    /** 异地瞬移速度阈值 (km/h) */
    public static final double TELEPORT_SPEED_THRESHOLD = 1000.0;

    /** 设备安全分阈值 */
    public static final int DEV_SCORE_THRESHOLD = 50;

    /** 综合评分 - 低危上限 (不包含) */
    public static final int SCORE_LOW_THRESHOLD = 60;

    /** 综合评分 - 中危上限 (包含) */
    public static final int SCORE_MEDIUM_THRESHOLD = 80;

    // ==================== 分页常量 ====================
    /** 默认分页大小 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 最大分页大小 */
    public static final int MAX_PAGE_SIZE = 100;

    // ==================== 认证接口路径 ====================
    public static final String API_AUTH_PREFIX = "/api/auth";

    // ==================== JWT ====================
    public static final String JWT_SECRET = "risk-dashboard-jwt-secret-key-2026";
    public static final long JWT_EXPIRATION_MS = 24 * 60 * 60 * 1000L;

    // ==================== 角色定义 ====================
    public static final String ROLE_TRADER  = "trader";
    /** Legacy role retained only so existing accounts can continue to log in. */
    public static final String ROLE_ANALYST = "analyst";
    public static final String ROLE_REALTIME_ANALYST = "realtime_analyst";
    public static final String ROLE_OFFLINE_ANALYST = "offline_analyst";
    public static final String ROLE_ADMIN   = "admin";
    public static final String ROLE_AUDITOR = "auditor";

    // ==================== WebSocket ====================
    /** WebSocket 广播频道 - 实时告警 */
    public static final String WS_TOPIC_ALERT = "/topic/alert";

    /** WebSocket 广播频道 - 实时交易 */
    public static final String WS_TOPIC_TRANSACTION = "/topic/transaction";

    /** WebSocket 广播频道 - 指标更新 */
    public static final String WS_TOPIC_METRICS = "/topic/metrics";
}
