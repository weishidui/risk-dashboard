USE risk_control;

CREATE TABLE IF NOT EXISTS risk_alert (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  alert_id VARCHAR(64) NOT NULL UNIQUE,
  trans_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  amount DOUBLE NOT NULL,
  city VARCHAR(64) NOT NULL,
  hit_rules VARCHAR(500) NOT NULL,
  final_score INT NOT NULL,
  risk_level VARCHAR(20) NOT NULL,
  alert_loc VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  handler VARCHAR(64) NULL,
  handle_time BIGINT NULL,
  handle_remark VARCHAR(500) NULL,
  counterparty_id VARCHAR(64) NULL,
  ip_address VARCHAR(45) NULL,
  is_new_device TINYINT(1) NOT NULL DEFAULT 0,
  is_new_counterparty TINYINT(1) NOT NULL DEFAULT 0,
  chain_id VARCHAR(64) NULL,
  raw_json TEXT NULL,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_trans_id (trans_id),
  INDEX idx_user_id (user_id),
  INDEX idx_risk_level (risk_level),
  INDEX idx_status (status),
  INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS counterparty_blacklist (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  counterparty_id VARCHAR(64) NOT NULL UNIQUE,
  counterparty_name VARCHAR(64),
  risk_level VARCHAR(20) NOT NULL,
  risk_type VARCHAR(64),
  risk_tags VARCHAR(255),
  source VARCHAR(64) NOT NULL DEFAULT 'manual_seed',
  total_received_24h DOUBLE DEFAULT 0,
  total_received_7d DOUBLE DEFAULT 0,
  unique_payers_24h INT DEFAULT 0,
  registration_days INT NOT NULL DEFAULT 0,
  fast_in_out_score INT DEFAULT 0,
  status VARCHAR(20) DEFAULT 'active',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO counterparty_blacklist
  (counterparty_id, counterparty_name, risk_level, risk_type, risk_tags, source,
   total_received_24h, total_received_7d, unique_payers_24h, registration_days, fast_in_out_score, status)
VALUES
  ('user_risk_000001', '赵强', 'high', 'fraud', 'fraud,money_mule', 'manual_seed',
   850000, 900000, 25, 1, 95, 'active')
ON DUPLICATE KEY UPDATE
  risk_level = VALUES(risk_level),
  risk_type = VALUES(risk_type),
  risk_tags = VALUES(risk_tags),
  total_received_24h = VALUES(total_received_24h),
  total_received_7d = VALUES(total_received_7d),
  unique_payers_24h = VALUES(unique_payers_24h),
  registration_days = VALUES(registration_days),
  fast_in_out_score = VALUES(fast_in_out_score),
  status = VALUES(status);

