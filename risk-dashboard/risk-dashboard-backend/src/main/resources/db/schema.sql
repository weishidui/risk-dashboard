-- ============================================================
-- 金融交易风险实时监控与分析平台 - 数据库初始化脚本 (v4.0)
-- 数据库名称: risk_control
-- 严格对应《数据库结构.md》五张核心业务表 + 一张指标快照表
-- 兼容 MySQL 5.7+ / H2
-- ============================================================

-- ----------------------------
-- 1. Kafka 原始交易流水表: transaction_history (31字段)
-- ----------------------------
CREATE TABLE IF NOT EXISTS transaction_history (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    trans_id          VARCHAR(64)  NOT NULL,
    user_id           VARCHAR(64)  NOT NULL,
    amount            DOUBLE       NOT NULL,
    trans_timestamp   BIGINT       NOT NULL,
    city              VARCHAR(64)  NOT NULL,
    geo_location      VARCHAR(64)  NOT NULL,
    device_id         VARCHAR(64)  NOT NULL,
    network_type      VARCHAR(20)  NOT NULL,
    dev_score         INT          NOT NULL DEFAULT 80,
    -- 设备/环境维度
    ip_address        VARCHAR(45)  DEFAULT '',
    os_type           VARCHAR(20)  DEFAULT '',
    os_version        VARCHAR(30)  DEFAULT '',
    screen_resolution VARCHAR(20)  DEFAULT '',
    battery_level     INT          DEFAULT NULL,
    root_jailbreak    TINYINT(1)   DEFAULT 0,
    sim_operator      VARCHAR(20)  DEFAULT NULL,
    user_agent        VARCHAR(500) DEFAULT '',
    dns_server        VARCHAR(45)  DEFAULT NULL,
    wifi_ssid         VARCHAR(100) DEFAULT NULL,
    -- 交易行为维度
    trans_type        VARCHAR(20)  DEFAULT '',
    pay_channel       VARCHAR(20)  DEFAULT '',
    input_method      VARCHAR(20)  DEFAULT 'manual',
    click_duration    BIGINT       DEFAULT 0,
    note              VARCHAR(200) DEFAULT NULL,
    page_url          VARCHAR(200) DEFAULT '',
    -- 收款方维度
    counterparty_id   VARCHAR(64)  DEFAULT '',
    counterparty_name VARCHAR(64)  DEFAULT '',
    counterparty_bank VARCHAR(64)  DEFAULT '',
    -- 身份/会话维度
    login_session_id  VARCHAR(64)  DEFAULT '',
    login_fail_count  INT          DEFAULT 0,
    create_time       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    UNIQUE INDEX idx_th_trans_id (trans_id),
    INDEX idx_th_user_id (user_id),
    INDEX idx_th_trans_ts (trans_timestamp),
    INDEX idx_th_city (city),
    INDEX idx_th_counterparty (counterparty_id),
    INDEX idx_th_create_time (create_time)
);


-- ----------------------------
-- 2. 用户历史行为画像表: user_profile (24字段)
-- ----------------------------
CREATE TABLE IF NOT EXISTS user_profile (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id               VARCHAR(64)   NOT NULL,
    -- 行为基线
    avg_amt_30d           DOUBLE        NOT NULL DEFAULT 0,
    common_cities         VARCHAR(255)  NOT NULL DEFAULT '',
    common_devs           VARCHAR(255)  NOT NULL DEFAULT '',
    common_pay_channels   VARCHAR(100)  DEFAULT '',
    common_trans_types    VARCHAR(100)  DEFAULT '',
    common_counterparties VARCHAR(500)  DEFAULT '',
    -- 历史快照
    last_trans_ts         BIGINT        NOT NULL DEFAULT 0,
    last_city             VARCHAR(64)   DEFAULT '',
    last_ip               VARCHAR(45)   DEFAULT '',
    last_login_time       BIGINT        DEFAULT 0,
    -- 账户信息
    registration_time     BIGINT        DEFAULT 0,
    total_balance         DOUBLE        DEFAULT 0,
    single_limit          DOUBLE        DEFAULT 100000,
    daily_limit           DOUBLE        DEFAULT 200000,
    monthly_limit         DOUBLE        DEFAULT 1000000,
    account_status        VARCHAR(20)   DEFAULT 'normal',
    -- 累计统计
    login_count_24h       INT           DEFAULT 0,
    trans_count_24h       INT           DEFAULT 0,
    trans_amount_24h      DOUBLE        DEFAULT 0,
    trans_count_7d        INT           DEFAULT 0,
    cancel_retry_count    INT           DEFAULT 0,
    -- 风险标记
    risk_tags             VARCHAR(255)  DEFAULT NULL,
    risk_score            INT           DEFAULT 0,
    update_time           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE INDEX idx_up_user_id (user_id),
    INDEX idx_up_update_time (update_time)
);


-- ----------------------------
-- 3. 实时风控告警结果表: risk_alert (20字段)
-- ----------------------------
CREATE TABLE IF NOT EXISTS risk_alert (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_id             VARCHAR(64)   NOT NULL,
    trans_id             VARCHAR(64)   NOT NULL,
    user_id              VARCHAR(64)   NOT NULL,
    amount               DOUBLE        NOT NULL,
    city                 VARCHAR(64)   NOT NULL,
    hit_rules            VARCHAR(500)  NOT NULL,
    final_score          INT           NOT NULL,
    risk_level           VARCHAR(20)   NOT NULL,
    alert_loc            VARCHAR(64)   NOT NULL,
    -- 告警处理工作流
    status               VARCHAR(20)   DEFAULT 'pending',
    handler              VARCHAR(64)   DEFAULT NULL,
    handle_time          BIGINT        DEFAULT NULL,
    handle_remark        VARCHAR(500)  DEFAULT NULL,
    -- 风控详情
    counterparty_id      VARCHAR(64)   DEFAULT NULL,
    ip_address           VARCHAR(45)   DEFAULT NULL,
    is_new_device        TINYINT(1)    DEFAULT 0,
    is_new_counterparty  TINYINT(1)    DEFAULT 0,
    chain_id             VARCHAR(64)   DEFAULT NULL,
    raw_json             TEXT          NULL,
    create_time          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,

    UNIQUE INDEX idx_ra_alert_id (alert_id),
    INDEX idx_ra_trans_id (trans_id),
    INDEX idx_ra_user_id (user_id),
    INDEX idx_ra_risk_level (risk_level),
    INDEX idx_ra_status (status),
    INDEX idx_ra_create_time (create_time)
);


-- ----------------------------
-- 4. 收款方风险黑名单表: counterparty_blacklist (12字段)
-- ----------------------------
CREATE TABLE IF NOT EXISTS counterparty_blacklist (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    counterparty_id     VARCHAR(64)   NOT NULL,
    counterparty_name   VARCHAR(64)   DEFAULT NULL,
    risk_level          VARCHAR(20)   NOT NULL,
    risk_type           VARCHAR(100)  NOT NULL,
    source              VARCHAR(50)   NOT NULL,
    total_received_24h  DOUBLE        DEFAULT 0,
    total_received_7d   DOUBLE        DEFAULT 0,
    unique_payers_24h   INT           DEFAULT 0,
    registration_days   INT           DEFAULT 0,
    risk_tags           VARCHAR(255)  DEFAULT NULL,
    created_time        TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE INDEX idx_cb_cpty_id (counterparty_id),
    INDEX idx_cb_risk_level (risk_level),
    INDEX idx_cb_received_7d (total_received_7d)
);


-- ----------------------------
-- 5. 资金链路追踪表: trans_chain (11字段)
-- ----------------------------
CREATE TABLE IF NOT EXISTS trans_chain (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    chain_id      VARCHAR(64)  NOT NULL,
    trans_id      VARCHAR(64)  NOT NULL,
    from_user_id  VARCHAR(64)  NOT NULL,
    to_user_id    VARCHAR(64)  NOT NULL,
    hop_order     INT          NOT NULL,
    amount        DOUBLE       NOT NULL,
    trans_time    BIGINT       NOT NULL,
    chain_depth   INT          NOT NULL,
    is_loop       TINYINT(1)   DEFAULT 0,
    create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_tc_chain_id (chain_id),
    INDEX idx_tc_from (from_user_id),
    INDEX idx_tc_to (to_user_id),
    INDEX idx_tc_trans_id (trans_id),
    INDEX idx_tc_hop (hop_order)
);


-- ----------------------------
-- 6. 指标快照表: t_metrics
-- ----------------------------
CREATE TABLE IF NOT EXISTS t_metrics (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_time         BIGINT         NOT NULL,
    total_transactions    BIGINT         DEFAULT 0,
    pass_count            BIGINT         DEFAULT 0,
    verify_count          BIGINT         DEFAULT 0,
    block_count           BIGINT         DEFAULT 0,
    high_risk_count       BIGINT         DEFAULT 0,
    medium_risk_count     BIGINT         DEFAULT 0,
    low_risk_count        BIGINT         DEFAULT 0,
    avg_risk_score        DECIMAL(5,2)   DEFAULT 0,
    env_risk_count        BIGINT         DEFAULT 0,
    amount_risk_count     BIGINT         DEFAULT 0,
    teleport_risk_count   BIGINT         DEFAULT 0,
    geo_risk_count        BIGINT         DEFAULT 0,
    avg_latency           DECIMAL(10,2)  DEFAULT 0,

    INDEX idx_mt_snapshot_time (snapshot_time)
);


-- ----------------------------
-- 7. 系统用户表: sys_user
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(128) NOT NULL,
    role        VARCHAR(32)  NOT NULL,
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_su_username (username)
);

-- ----------------------------
-- 8. 演示数据
-- ----------------------------
INSERT INTO sys_user (username, password, role) VALUES ('admin', 'admin123', 'admin');
-- 交易和告警数据由离线链路或实时风控链路写入，此处不提供演示数据。
