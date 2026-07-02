-- ============================================================
-- 金融交易风险实时监控与分析平台 - 数据库初始化脚本
-- 数据库名称: risk_control
-- 严格对应《数据库结构.md》三张核心业务表 + 一张指标快照表
-- 兼容 MySQL 5.7+ / H2
-- ============================================================

-- ----------------------------
-- 1. Kafka 原始交易流水表: transaction_history
-- 对应数据库结构.md 第 2 节
-- ----------------------------
CREATE TABLE IF NOT EXISTS transaction_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    trans_id        VARCHAR(64)  NOT NULL COMMENT '交易流水号，全局唯一 ID',
    user_id         VARCHAR(64)  NOT NULL COMMENT '用户标识，关联画像库主键',
    amount          DOUBLE       NOT NULL COMMENT '本次交易金额',
    trans_timestamp BIGINT       NOT NULL COMMENT '交易时间戳，13 位毫秒级时间戳',
    city            VARCHAR(64)  NOT NULL COMMENT '交易发生地城市名',
    geo_location    VARCHAR(64)  NOT NULL COMMENT '地理经纬度，格式如 116.3,39.9',
    device_id       VARCHAR(64)  NOT NULL COMMENT '设备指纹，硬件唯一标识码',
    network_type    VARCHAR(20)  NOT NULL COMMENT '网络类型：WiFi、4G、5G、VPN',
    dev_score       INT          NOT NULL COMMENT '设备安全分，范围 0-100',
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '数据入库时间',

    UNIQUE INDEX idx_th_trans_id (trans_id),
    INDEX idx_th_user_id (user_id),
    INDEX idx_th_trans_ts (trans_timestamp),
    INDEX idx_th_city (city),
    INDEX idx_th_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Kafka 原始交易流水表';


-- ----------------------------
-- 2. 用户历史行为画像表: user_profile
-- 对应数据库结构.md 第 3 节
-- ----------------------------
CREATE TABLE IF NOT EXISTS user_profile (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    user_id       VARCHAR(64)   NOT NULL COMMENT '用户标识',
    avg_amt_30d   DOUBLE        NOT NULL COMMENT '过去 30 天单笔平均金额',
    common_cities VARCHAR(255)  NOT NULL COMMENT '过去 3 个月高频城市 Top 3，逗号分隔',
    common_devs   VARCHAR(255)  NOT NULL COMMENT '用户常用设备 ID 列表，逗号分隔',
    last_trans_ts BIGINT        NOT NULL COMMENT '上一笔交易发生的毫秒级时间戳',
    last_city     VARCHAR(64)   NOT NULL COMMENT '上一笔交易发生城市',
    update_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '画像更新时间',

    UNIQUE INDEX idx_up_user_id (user_id),
    INDEX idx_up_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户历史行为画像表';


-- ----------------------------
-- 3. 实时风控告警结果表: risk_alert
-- 对应数据库结构.md 第 4 节
-- ----------------------------
CREATE TABLE IF NOT EXISTS risk_alert (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    alert_id    VARCHAR(64)   NOT NULL COMMENT '告警编号',
    trans_id    VARCHAR(64)   NOT NULL COMMENT '交易流水号，关联原始交易',
    user_id     VARCHAR(64)   NOT NULL COMMENT '用户标识',
    amount      DOUBLE        NOT NULL COMMENT '本次交易金额',
    city        VARCHAR(64)   NOT NULL COMMENT '交易城市',
    hit_rules   VARCHAR(255)  NOT NULL COMMENT '命中的风险规则标签，多个规则用分号分隔',
    final_score INT           NOT NULL COMMENT '综合风险评分，范围 0-100',
    risk_level  VARCHAR(20)   NOT NULL COMMENT '风险等级：低危、中危、高危',
    alert_loc   VARCHAR(64)   NOT NULL COMMENT '告警位置，用于大屏地图展示',
    raw_json    TEXT          NULL     COMMENT 'Kafka 原始 JSON 报文',
    create_time TIMESTAMP     DEFAULT CURRENT_TIMESTAMP COMMENT '告警入库时间',

    INDEX idx_ra_trans_id (trans_id),
    INDEX idx_ra_user_id (user_id),
    INDEX idx_ra_risk_level (risk_level),
    INDEX idx_ra_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实时风控告警结果表';


-- ----------------------------
-- 4. 指标快照表: t_metrics (应用扩展，不在数据库结构.md 中)
-- 用于仪表盘趋势图展示
-- ----------------------------
CREATE TABLE IF NOT EXISTS t_metrics (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    snapshot_time         BIGINT         NOT NULL COMMENT '统计时间戳',
    total_transactions    BIGINT         DEFAULT 0 COMMENT '当前秒级交易总量',
    pass_count            BIGINT         DEFAULT 0 COMMENT '正常放行数',
    verify_count          BIGINT         DEFAULT 0 COMMENT '待核验数',
    block_count           BIGINT         DEFAULT 0 COMMENT '拦截数',
    high_risk_count       BIGINT         DEFAULT 0 COMMENT '高危告警数',
    medium_risk_count     BIGINT         DEFAULT 0 COMMENT '中危告警数',
    low_risk_count        BIGINT         DEFAULT 0 COMMENT '低危告警数',
    avg_risk_score        DECIMAL(5,2)   DEFAULT 0 COMMENT '平均风险评分',
    env_risk_count        BIGINT         DEFAULT 0 COMMENT '环境异常数',
    amount_risk_count     BIGINT         DEFAULT 0 COMMENT '金额异常数',
    teleport_risk_count   BIGINT         DEFAULT 0 COMMENT '异地瞬移异常数',
    geo_risk_count        BIGINT         DEFAULT 0 COMMENT '地理偏离异常数',
    avg_latency           DECIMAL(10,2)  DEFAULT 0 COMMENT '平均处理延迟(毫秒)',

    INDEX idx_mt_snapshot_time (snapshot_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实时指标快照表';


-- ----------------------------
-- 5. 模拟数据插入 (仅用于开发演示)
-- ----------------------------
INSERT INTO risk_alert (alert_id, trans_id, user_id, hit_rules, amount, final_score,
    risk_level, city, alert_loc, raw_json)
VALUES
('ALT001', 'TXN001', 'USER1001', '金额突变', 15000.00, 35, '低危', '北京', '北京', '{"trans_id":"TXN001","user_id":"USER1001","amount":15000}'),
('ALT002', 'TXN002', 'USER1002', '地理偏离;环境风险', 8000.00, 65, '中危', '上海', '上海', '{"trans_id":"TXN002","user_id":"USER1002","amount":8000}'),
('ALT003', 'TXN003', 'USER1003', '金额突变;异地瞬移;环境风险', 50000.00, 95, '高危', '深圳', '深圳', '{"trans_id":"TXN003","user_id":"USER1003","amount":50000}'),
('ALT004', 'TXN004', 'USER1004', '环境风险', 3000.00, 42, '低危', '广州', '广州', '{"trans_id":"TXN004","user_id":"USER1004","amount":3000}'),
('ALT005', 'TXN005', 'USER1005', '金额突变', 25000.00, 55, '中危', '杭州', '杭州', '{"trans_id":"TXN005","user_id":"USER1005","amount":25000}'),
('ALT006', 'TXN006', 'USER1006', '异地瞬移;环境风险', 12000.00, 88, '高危', '成都', '成都', '{"trans_id":"TXN006","user_id":"USER1006","amount":12000}'),
('ALT007', 'TXN007', 'USER1007', '地理偏离', 5000.00, 22, '低危', '武汉', '武汉', '{"trans_id":"TXN007","user_id":"USER1007","amount":5000}');
