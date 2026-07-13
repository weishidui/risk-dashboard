# t_metrics 指标快照表说明

## 1. 概述

`t_metrics` 是 Dashboard 仪表盘 **24H 趋势图** 的数据来源。它按小时存储指标快照，每条记录代表一个时间点的系统状态。

| 属性 | 说明 |
|------|------|
| 表名 | `t_metrics` |
| 数据库 | `risk_control` |
| 数据来源 | 定时任务从 `risk_alert` + `transaction_history` 聚合 |
| 用途 | Dashboard 24H 交易/告警趋势图、DataAnalysis 趋势图 |
| 更新频率 | 生产模式每分钟自动采集一条 |

---

## 2. 表结构

```sql
CREATE TABLE IF NOT EXISTS t_metrics (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_time       BIGINT    NOT NULL,          -- 快照时间戳(毫秒)
    total_transactions  BIGINT    DEFAULT 0,          -- 近1小时总交易量
    pass_count          BIGINT    DEFAULT 0,          -- 低危/放行数
    verify_count        BIGINT    DEFAULT 0,          -- 中危/核验数
    block_count         BIGINT    DEFAULT 0,          -- 高危+极度危险/拦截数
    high_risk_count     BIGINT    DEFAULT 0,          -- 高危+极度危险总数
    medium_risk_count   BIGINT    DEFAULT 0,          -- 中危总数
    low_risk_count      BIGINT    DEFAULT 0,          -- 低危总数
    avg_risk_score      DOUBLE    DEFAULT 0,          -- 平均风险评分
    env_risk_count      BIGINT    DEFAULT 0,          -- 环境风险命中数
    amount_risk_count   BIGINT    DEFAULT 0,          -- 金额风险命中数
    teleport_risk_count BIGINT    DEFAULT 0,          -- 异地瞬移命中数
    geo_risk_count      BIGINT    DEFAULT 0,          -- 地理偏离命中数
    avg_latency         DOUBLE    DEFAULT 0           -- 平均系统延迟(ms)
);
```

### 字段说明

| 字段 | 含义 | 计算来源 |
|------|------|----------|
| `snapshot_time` | 快照时间点 | 当前毫秒时间戳 |
| `total_transactions` | 近1小时总交易量 | `COUNT(*) FROM transaction_history WHERE 近1小时` |
| `pass_count` | 放行数 | `risk_level='低危'` 的告警数 |
| `verify_count` | 核验数 | `risk_level='中危'` 的告警数 |
| `block_count` | 拦截数 | `risk_level IN ('高危','极度危险')` 的告警数 |
| `high_risk_count` | 高危+极度危险 | 同上 |
| `medium_risk_count` | 中危 | 同上 |
| `low_risk_count` | 低危 | 同上 |
| `avg_risk_score` | 平均风险分 | `AVG(final_score)` |
| `avg_latency` | 平均延迟 | `AVG(click_duration)` 或默认 300 |
| `env_risk_count` | 环境规则命中 | `hit_rules` 含"环境"的告警数 |
| `amount_risk_count` | 金额规则命中 | `hit_rules` 含"金额"的告警数 |
| `teleport_risk_count` | 瞬移规则命中 | `hit_rules` 含"瞬移"的告警数 |
| `geo_risk_count` | 地理规则命中 | `hit_rules` 含"地理"的告警数 |

---

## 3. 数据是如何产生的

### 3.1 生产模式（prod）— 定时自动采集

后端 [MetricsServiceImpl.java](risk-dashboard-backend/src/main/java/com/finance/risk/dashboard/service/impl/MetricsServiceImpl.java) 中有一个定时任务：

```java
@Scheduled(fixedRate = 60000)  // 每60秒执行一次
public void snapshotMetrics() {
    // 开发模式跳过，生产模式才执行
    if (!"prod".equals(activeProfile)) return;

    // 1. 从 risk_alert 查询最近1小时统计数据
    List<AlertDao.RiskLevelCount> levels = alertDao.countByRiskLevel(since);
    List<AlertDao.RuleCount> rules = alertDao.countByHitRule(since);

    // 2. 从 transaction_history 查询交易量
    long total = transactionService.countByTimeRange(sinceTs, now);
    long pass = transactionService.countPassByDevScore(sinceTs, now);
    long block = transactionService.countBlockByDevScore(sinceTs, now);

    // 3. 计算平均值、分类计数
    double avgScore = (high * 90 + mid * 65 + low * 20) / (high + mid + low);
    Double avgLat = transactionService.avgClickDuration(sinceTs, now);

    // 4. 组装 MetricsSnapshot 对象并写入 t_metrics 表
    MetricsSnapshot snap = MetricsSnapshot.builder()
        .snapshotTime(now)
        .totalTransactions(total)
        .passCount(pass).verifyCount(verify).blockCount(block)
        .highRiskCount(high).mediumRiskCount(mid).lowRiskCount(low)
        .avgRiskScore(avgScore).avgLatency(avgLat)
        .envRiskCount(envRisk).amountRiskCount(amtRisk)
        .teleportRiskCount(teleRisk).geoRiskCount(geoRisk)
        .build();
    metricsDao.insert(snap);
}
```

### 3.2 开发模式（dev）— 启动时生成模拟数据

[DataInitializer.java](risk-dashboard-backend/src/main/java/com/finance/risk/dashboard/config/DataInitializer.java) 在开发模式启动时自动生成 25 条模拟快照（过去 24 小时，每小时一条）：

```java
private void initMetrics(long now) {
    // 检查是否有足够数据
    int existingCount = metricsDao.countByTimeRange(since24h, now);
    if (existingCount >= 20) return;  // 已有数据则跳过

    // 生成 25 个快照点（过去24h + 当前）
    for (int i = 24; i >= 0; i--) {
        long t = now - i * 3600000L;
        // 按比例生成模拟数据
        long total = 300 + RANDOM.nextInt(200);
        long pass = total * 74 / 100;
        long block = total * 8 / 100;
        // ...
        metricsDao.insert(m);
    }
}
```

### 3.3 数据流向图

```
┌─────────────────┐     ┌─────────────────┐
│  risk_alert      │     │transaction_history│
│  (告警结果表)     │     │  (交易流水表)      │
└────────┬────────┘     └────────┬────────┘
         │                       │
         │   @Scheduled(fixedRate=60000)
         │   每分钟查询最近1小时数据
         │                       │
         └───────────┬───────────┘
                     ▼
            ┌────────────────┐
            │  MetricsService │
            │  聚合 & 计算     │
            └───────┬────────┘
                    ▼
            ┌────────────────┐
            │   t_metrics     │   ← 写入一条新快照
            └───────┬────────┘
                    │
                    ▼
            ┌────────────────┐
            │  GET /api/      │
            │  metrics/trend  │   ← 前端每5秒拉取
            │  ?hours=24      │
            └───────┬────────┘
                    ▼
            ┌────────────────┐
            │  Dashboard 大屏  │
            │  24H 趋势折线图  │
            └────────────────┘
```

---

## 4. 前端如何使用

### 4.1 API 接口

| 接口 | 方法 | 说明 |
|------|:---:|------|
| `/api/metrics/latest` | GET | 最新一条快照（Dashboard 指标卡用） |
| `/api/metrics/trend?hours=24` | GET | 最近 N 小时趋势数据（趋势图用） |
| `/api/dashboard/overview` | GET | Dashboard 综合数据（含趋势+分布） |

### 4.2 前端页面

| 页面 | 图表 | 数据来源 |
|------|------|----------|
| Dashboard | 6 个指标卡片 | `metrics/latest` → 快照最新值 |
| Dashboard | 24H 交易与告警趋势 | `metrics/trend?hours=24` → 快照时间序列 |
| DataAnalysis | 24H 风险趋势对比线 | `metrics/trend?hours=24` → 快照时间序列 |
| DataAnalysis | 风险指数仪表盘 | `dashboard/overview` → avgRiskScore |

### 4.3 前端调用代码

```javascript
// Dashboard.vue
import { getDashboardData } from '@/api/metrics'

async fetchData() {
  const res = await getDashboardData()
  // res.data.transactionTrend  → 交易量趋势 [{time, value, timestamp}]
  // res.data.alertTrend       → 告警量趋势
  // res.data.blockRateTrend   → 拦截率趋势
  // res.data.totalTransactions → 最新总交易量
  // res.data.avgRiskScore     → 平均风险评分
}
```

---

## 5. 首次部署后如何快速产生数据

### 方案一：等定时任务自动产生（1-2 分钟）

启动 prod 模式后，`@Scheduled` 定时任务 1 分钟内执行第一次，随后每分钟一条。大约 24 小时后趋势图就能展示完整的全天曲线。

### 方案二：手动灌历史数据（立即生效）

执行以下 SQL 从已有 `risk_alert` 数据聚合生成历史快照：

```sql
INSERT INTO t_metrics (
    snapshot_time, total_transactions, pass_count, verify_count, block_count,
    high_risk_count, medium_risk_count, low_risk_count,
    avg_risk_score, avg_latency
)
SELECT 
    FLOOR(UNIX_TIMESTAMP(DATE_FORMAT(
        DATE_ADD(create_time, INTERVAL 1 HOUR), '%Y-%m-%d %H:00:00'
    )) * 1000) AS snapshot_time,
    COUNT(*) AS total,
    SUM(CASE WHEN risk_level = '低危' THEN 1 ELSE 0 END) AS pass_count,
    SUM(CASE WHEN risk_level = '中危' THEN 1 ELSE 0 END) AS verify_count,
    SUM(CASE WHEN risk_level IN ('高危','极度危险') THEN 1 ELSE 0 END) AS block_count,
    SUM(CASE WHEN risk_level IN ('高危','极度危险') THEN 1 ELSE 0 END) AS high_risk,
    SUM(CASE WHEN risk_level = '中危' THEN 1 ELSE 0 END) AS medium_risk,
    SUM(CASE WHEN risk_level = '低危' THEN 1 ELSE 0 END) AS low_risk,
    COALESCE(AVG(final_score), 0) AS avg_score,
    300 AS avg_latency
FROM risk_alert
WHERE create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)
GROUP BY DATE_FORMAT(DATE_ADD(create_time, INTERVAL 1 HOUR), '%Y-%m-%d %H:00:00')
ORDER BY snapshot_time
LIMIT 24;
```

执行完后无需重启，前端趋势图立即显示历史曲线。

### 方案三：用 simulate_data.sh 灌测试数据

在本地执行：

```bash
# 会 POST 一条 metrics 快照到后端
bash simulate_data.sh
```

然后重启后端，DataInitializer 检测到数据不足 20 条时，会自动生成 25 条模拟快照。

---

## 6. 排错

### 6.1 表不存在

**错误日志：**
```
Table 'risk_control.t_metrics' doesn't exist
```

**解决：**
```sql
CREATE TABLE IF NOT EXISTS t_metrics ( ... );
```

### 6.2 趋势图没有数据

**原因：**
- 生产模式刚启动，定时任务还没来得及产生快照
- `risk_alert` 表为空，没有告警数据可聚合

**排查：**
```sql
-- 检查是否有快照
SELECT COUNT(*), MIN(snapshot_time), MAX(snapshot_time) FROM t_metrics;

-- 检查是否有告警数据
SELECT COUNT(*) FROM risk_alert;
```

**解决：**
- 等 1-2 分钟让定时任务自动产生第一条
- 或手动执行方案二的 SQL 灌数据
- 或确认 `risk_alert` 表有数据

### 6.3 启动时报 t_metrics 插入失败

**错误日志：**
```
DataInitializer - t_metrics 表不存在或数据库异常，跳过指标初始化
```

这说明 DataInitializer 在启动时尝试写入演示数据的快照，但表不存在。**这是正常降级行为**，不影响启动。建表后重启即可。

---

## 7. 完整部署检查清单

生产模式启动后，按以下顺序检查：

```bash
# 1. 确认表存在
mysql -h 192.168.154.104 -u root -p123456 -e "SHOW TABLES" risk_control | grep t_metrics

# 2. 确认 sys_user 有 admin 账号
mysql -h 192.168.154.104 -u root -p123456 -e "SELECT * FROM sys_user WHERE username='admin'" risk_control

# 3. 启动服务
fuser -k 8080/tcp
nohup java -jar /home/master0/webcode/risk-dashboard-backend.jar --spring.profiles.active=prod > /home/master0/app.log 2>&1 &

# 4. 等 15 秒后验证
sleep 15
curl http://localhost:8080/api/dashboard/health

# 5. 等 2 分钟后检查快照是否产生
sleep 120
mysql -h 192.168.154.104 -u root -p123456 -e "SELECT COUNT(*) FROM t_metrics" risk_control
```
