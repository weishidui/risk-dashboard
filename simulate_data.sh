#!/bin/bash
# ============================================================
# 金融风控系统 - 模拟数据接入脚本
# 模拟 Lambda 架构中两条数据处理链路的结果写入
# ============================================================

API="http://localhost:8080/api/data"
PASS=0; FAIL=0

log() { echo -e "\n\033[1;36m>>> $1\033[0m"; }
ok()  { echo -e "  \033[32m✓ $1\033[0m"; ((PASS++)); }
err() { echo -e "  \033[31m✗ $1\033[0m"; ((FAIL++)); }

call() {
  local resp=$(curl -s -w "\n%{http_code}" -X POST "$API/$1" -H "Content-Type: application/json" -d "$2")
  local code=$(echo "$resp" | tail -1)
  local body=$(echo "$resp" | sed '$d')
  if [ "$code" = "200" ]; then
    ok "$3"
  else
    err "$3 — HTTP $code: $body"
  fi
}

echo "============================================================"
echo "  金融风控系统 - 模拟数据接入"
echo "  目标: $API"
echo "============================================================"

# ============================================================
# 第一步：模拟离线批处理路径 (HDFS → Spark SQL → Redis/展示端)
# 对应需求文档 2.2 节「离线行为画像模块」
# 作用：将用户历史行为画像写入，供实时风控引擎查询比对
# ============================================================
log "第一步：离线批处理 — 用户画像数据接入 (模拟 HDFS → Spark SQL)"

# 用户A：正常用户，北京常驻，日均消费 5000
call "profile" '{
  "userId": "USER_NORMAL_WANG",
  "avgAmt30d": 5000.00,
  "commonCities": ["北京","上海","广州"],
  "commonDevs": ["DEV_OFFICE_PC", "DEV_IPHONE_12"],
  "lastTransTs": 1719730000000,
  "lastCity": "北京",
  "baseRiskLevel": "低危"
}' "写入画像: USER_NORMAL_WANG (正常用户，日均¥5000，常驻北京)"

# 用户B：高消费用户，深圳常驻，日均消费 20000
call "profile" '{
  "userId": "USER_RICH_LI",
  "avgAmt30d": 20000.00,
  "commonCities": ["深圳","香港","广州"],
  "commonDevs": ["DEV_MACBOOK_PRO", "DEV_IPHONE_15"],
  "lastTransTs": 1719730500000,
  "lastCity": "深圳",
  "baseRiskLevel": "低危"
}' "写入画像: USER_RICH_LI (高消费用户，日均¥20000，常驻深圳)"

# 用户C：风险用户，设备环境差
call "profile" '{
  "userId": "USER_RISK_ZHAO",
  "avgAmt30d": 2000.00,
  "commonCities": ["成都"],
  "commonDevs": ["DEV_OLD_PHONE"],
  "lastTransTs": 1719720000000,
  "lastCity": "成都",
  "baseRiskLevel": "中危"
}' "写入画像: USER_RISK_ZHAO (低消费用户，日均¥2000，常驻成都)"

# ============================================================
# 第二步：模拟实时流处理路径 (Kafka → Spark Streaming → 展示端)
# 对应需求文档 2.3 节「实时风控引擎模块」
# 作用：写入实时交易流水，然后写入风控判定结果
# ============================================================
log "第二步：实时流处理 — 交易流水 + 风控告警接入 (模拟 Kafka → Spark Streaming)"

# --- 场景1：正常交易（低风险） ---
log "  场景1: 正常用户北京本地消费 — 预期: 低危/放行"

call "transaction" '{
  "transId": "TXN_20260630_NORMAL_001",
  "userId": "USER_NORMAL_WANG",
  "amount": 3500.00,
  "timestamp": 1719734500000,
  "city": "北京",
  "geoLocation": "116.40,39.90",
  "deviceId": "DEV_IPHONE_12",
  "networkType": "5G",
  "devScore": 92
}' "写入交易: USER_NORMAL_WANG 北京消费 ¥3500 (正常)"

call "alert" '{
  "alertId": "ALT_NORMAL_001",
  "transId": "TXN_20260630_NORMAL_001",
  "userId": "USER_NORMAL_WANG",
  "hitRules": "无",
  "amount": 3500.00,
  "finalScore": 10,
  "riskLevel": "低危",
  "alertLoc": "北京",
  "geoLocation": "116.40,39.90",
  "networkType": "5G",
  "devScore": 92,
  "action": "PASS",
  "alertTime": 1719734501000
}' "写入告警: 低危/放行 (评分10)"

# --- 场景2：金额突变（中风险） ---
log "  场景2: 正常用户突然大额消费 — 预期: 中危/核验"

call "transaction" '{
  "transId": "TXN_20260630_ALERT_002",
  "userId": "USER_NORMAL_WANG",
  "amount": 28000.00,
  "timestamp": 1719734600000,
  "city": "北京",
  "geoLocation": "116.40,39.90",
  "deviceId": "DEV_IPHONE_12",
  "networkType": "5G",
  "devScore": 90
}' "写入交易: USER_NORMAL_WANG 北京消费 ¥28000 (金额异常)"

call "alert" '{
  "alertId": "ALT_AMOUNT_002",
  "transId": "TXN_20260630_ALERT_002",
  "userId": "USER_NORMAL_WANG",
  "hitRules": "金额突变",
  "amount": 28000.00,
  "finalScore": 55,
  "riskLevel": "中危",
  "alertLoc": "北京",
  "geoLocation": "116.40,39.90",
  "networkType": "5G",
  "devScore": 90,
  "action": "VERIFY",
  "alertTime": 1719734601000
}' "写入告警: 中危/核验 (评分55)"

# --- 场景3：异地瞬移 + VPN环境（高风险） ---
log "  场景3: VPN + 异地瞬移 + 大额 — 预期: 高危/拦截"

call "transaction" '{
  "transId": "TXN_20260630_DANGER_003",
  "userId": "USER_RISK_ZHAO",
  "amount": 45000.00,
  "timestamp": 1719734700000,
  "city": "深圳",
  "geoLocation": "114.05,22.55",
  "deviceId": "DEV_UNKNOWN_X99",
  "networkType": "VPN",
  "devScore": 18
}' "写入交易: USER_RISK_ZHAO 深圳消费 ¥45000 (VPN+异地+大额)"

call "alert" '{
  "alertId": "ALT_DANGER_003",
  "transId": "TXN_20260630_DANGER_003",
  "userId": "USER_RISK_ZHAO",
  "hitRules": "金额突变;异地瞬移;环境风险",
  "amount": 45000.00,
  "finalScore": 95,
  "riskLevel": "高危",
  "alertLoc": "深圳",
  "geoLocation": "114.05,22.55",
  "networkType": "VPN",
  "devScore": 18,
  "action": "BLOCK",
  "alertTime": 1719734701000
}' "写入告警: 高危/拦截 (评分95)"

# --- 场景4：地理偏离（中风险） ---
log "  场景4: 常驻北京用户突然在广州交易 — 预期: 中危/核验"

call "transaction" '{
  "transId": "TXN_20260630_WARN_004",
  "userId": "USER_NORMAL_WANG",
  "amount": 6000.00,
  "timestamp": 1719734800000,
  "city": "广州",
  "geoLocation": "113.26,23.13",
  "deviceId": "DEV_IPHONE_12",
  "networkType": "4G",
  "devScore": 88
}' "写入交易: USER_NORMAL_WANG 广州消费 ¥6000 (地理偏离)"

call "alert" '{
  "alertId": "ALT_GEO_004",
  "transId": "TXN_20260630_WARN_004",
  "userId": "USER_NORMAL_WANG",
  "hitRules": "地理偏离",
  "amount": 6000.00,
  "finalScore": 35,
  "riskLevel": "中危",
  "alertLoc": "广州",
  "geoLocation": "113.26,23.13",
  "networkType": "4G",
  "devScore": 88,
  "action": "VERIFY",
  "alertTime": 1719734801000
}' "写入告警: 中危/核验 (评分35)"

# --- 场景5：设备环境风险 ---
log "  场景5: 设备安全分低 + VPN — 预期: 中危/核验"

call "transaction" '{
  "transId": "TXN_20260630_RISK_005",
  "userId": "USER_RICH_LI",
  "amount": 8000.00,
  "timestamp": 1719734900000,
  "city": "深圳",
  "geoLocation": "114.05,22.55",
  "deviceId": "DEV_UNKNOWN_Y88",
  "networkType": "VPN",
  "devScore": 35
}' "写入交易: USER_RICH_LI 深圳消费 ¥8000 (设备环境异常)"

call "alert" '{
  "alertId": "ALT_ENV_005",
  "transId": "TXN_20260630_RISK_005",
  "userId": "USER_RICH_LI",
  "hitRules": "环境风险",
  "amount": 8000.00,
  "finalScore": 45,
  "riskLevel": "中危",
  "alertLoc": "深圳",
  "geoLocation": "114.05,22.55",
  "networkType": "VPN",
  "devScore": 35,
  "action": "VERIFY",
  "alertTime": 1719734901000
}' "写入告警: 中危/核验 (评分45)"

# ============================================================
# 第三步：模拟指标聚合 (Spark Streaming 窗口聚合 → 前端大屏)
# ============================================================
log "第三步：指标快照 — 仪表盘实时数据 (模拟 Spark Streaming 窗口聚合)"

call "metrics" '{
  "snapshotTime": 1719735000000,
  "totalTransactions": 580,
  "passCount": 420,
  "verifyCount": 120,
  "blockCount": 40,
  "highRiskCount": 40,
  "mediumRiskCount": 120,
  "lowRiskCount": 420,
  "avgRiskScore": 38.5,
  "avgLatency": 320,
  "envRiskCount": 25,
  "amountRiskCount": 35,
  "teleportRiskCount": 8,
  "geoRiskCount": 55
}' "写入指标快照: 580笔交易, 拦截率6.9%, 平均延迟320ms"

# ============================================================
# 第四步：验证数据
# ============================================================
log "第四步：验证数据是否正确写入"

echo ""
echo "  --- 最近告警列表 (Top 5) ---"
curl -s http://localhost:8080/api/alert/recent?limit=5 | python3 -c "
import sys,json
data=json.load(sys.stdin)['data']
for i,a in enumerate(data):
    print(f'  {i+1}. [{a[\"riskLevel\"]}] {a[\"alertId\"]} | {a[\"userId\"]} | {a[\"hitRules\"]} | 评分{a[\"finalScore\"]} | {a[\"action\"]}')
" 2>/dev/null

echo ""
echo "  --- 仪表盘核心指标 ---"
curl -s http://localhost:8080/api/dashboard/overview | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
print(f'  交易总量: {d[\"totalTransactions\"]}  放行: {d[\"passCount\"]}  核验: {d[\"verifyCount\"]}  拦截: {d[\"blockCount\"]}')
print(f'  活跃用户: {d[\"activeUsers\"]}  平均风险分: {d[\"avgRiskScore\"]}  平均延迟: {d[\"avgLatency\"]}ms')
print(f'  风险分布: {[(i[\"name\"],i[\"value\"]) for i in d[\"riskLevelDistribution\"]]}')
" 2>/dev/null

echo ""
echo "============================================================"
echo "  模拟数据接入完成!"
echo "  成功: $PASS  失败: $FAIL"
echo "  打开浏览器访问: http://localhost:8081"
echo "============================================================"
