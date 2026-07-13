# risk-streaming 实时风控引擎

这个模块负责需求文档中的在线实时分析链路：

```text
Kafka trans-event
  -> Spark Streaming 微批处理
  -> Redis 查询 profile:{user_id} / counterparty:{counterparty_id}
  -> MySQL 兜底查询 user_profile / counterparty_blacklist / trader_user
  -> 60 条规则评分
  -> Redis 10 秒实时指标桶
  -> POST 大屏后端 /api/data/alert
  -> 大屏后端统一写 MySQL risk_alert / Redis / WebSocket
  -> MySQL trans_chain
```

## 1. 打包

建议在有 Maven 和 Spark 环境的机器上打包，例如 `master0` 或 `mrzk`：

```bash
cd ~/数据产生端/risk-streaming
mvn clean package -DskipTests
```

生成文件：

```text
target/risk-streaming-1.0.0.jar
```

如果代码在 Windows 编写，先把整个 `risk-streaming` 目录传到集群机器。

## 2. 启动

本地 Spark 测试：

```bash
spark-submit \
  --class com.risk.streaming.RiskStreamingApp \
  --master local[2] \
  target/risk-streaming-1.0.0.jar \
  --kafka-brokers 192.168.154.104:9092 \
  --kafka-topic trans-event \
  --kafka-group risk-streaming-test-001 \
  --kafka-offset-reset latest \
  --mysql-url "jdbc:mysql://192.168.154.104:3306/risk_control?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai" \
  --mysql-user root \
  --mysql-password 123456 \
  --redis-host 192.168.154.104 \
  --redis-port 6379 \
  --redis-password 123456 \
  --dashboard-alert-url http://192.168.154.113:8080/api/data/alert \
  --batch-ms 100
```

提交到 Spark 集群时把 `--master local[2]` 换成你的 Spark Master，例如：

```bash
--master spark://master0:7077
```

## 3. 产生测试数据

在 Windows 数据产生端运行：

```powershell
cd C:\Users\范书豪\Desktop\数据产生端
python src\mirrored_realtime_producer.py `
  --url http://192.168.154.104/api/transactions `
  --mysql-host 192.168.154.104 `
  --mysql-user root `
  --mysql-password 123456 `
  --rate 1 `
  --limit 10 `
  --high-risk-ratio 0.5 `
  --city-format city `
  --timeout 90
```

## 4. 验证 MySQL 结果

在 `mrzk` 的 MySQL 中查看：

```sql
USE risk_control;

SELECT COUNT(*) FROM risk_alert;

SELECT alert_id, trans_id, user_id, amount, risk_level, final_score, status, hit_rules, create_time
FROM risk_alert
ORDER BY id DESC
LIMIT 10;

SELECT chain_id, trans_id, user_id, counterparty_id, hop_order, chain_depth, is_loop, chain_type, create_time
FROM trans_chain
ORDER BY id DESC
LIMIT 10;
```

看到 `risk_alert` 新增记录，且 `hit_rules`、`final_score`、`risk_level` 有值，就说明：

```text
Kafka -> Spark Streaming -> 规则引擎 -> 大屏后端 /api/data/alert -> MySQL risk_alert
```

已经打通。

## 5. 说明

当前程序优先读 Redis：

```text
profile:{user_id}
counterparty:{counterparty_id}
```

如果 Redis 暂时没有离线画像数据，会自动从 MySQL 的 `user_profile`、`trader_user`、`trader_counterparty`、`counterparty_blacklist` 做兜底读取，方便当前阶段测试。

默认 `write-low-risk=true`。它会提交命中过规则且评分大于 0 的低危交易，便于在告警详情中查看低危规则；评分为 0、规则为“无”的正常交易不会写入 `risk_alert`。前端实时告警推送面板仍只显示高危/极度危险。正式展示时可以改为：

```bash
--write-low-risk false
```

设置为 `false` 后，低危规则记录不会提交，仅保留中危/高危/极度危险或硬拦截结果。

实时告警现在不由本程序直接 `INSERT risk_alert`。本程序只负责实时计算和写 `trans_chain`，风险告警统一提交给大屏后端，由大屏后端完成：

```text
写 MySQL risk_alert
写 Redis risk:alert:list
通过 WebSocket 推送前端
```

## 6. Dashboard 实时指标

每笔交易完成评分后都会更新 Redis 10 秒时间桶，即使评分为 0 的正常交易不写入 `risk_alert`，也会计入交易量、放行率和活跃用户。Key 的 TTL 为 48 小时：

```text
risk:rt:metric:10s:{bucket}  交易数、PASS/REVIEW/BLOCK、四级风险数、评分和、规则类别数
risk:rt:users:10s:{bucket}   HyperLogLog 活跃用户去重
risk:rt:city:10s:{bucket}    城市交易计数
risk:rt:rule:10s:{bucket}    单条规则命中计数
```

风控处置口径：

```text
score <= 40 且非硬规则      -> PASS
41 <= score <= 120          -> REVIEW
score > 120 或命中硬规则    -> BLOCK
```

dashboard 后端聚合最近 6 个 10 秒桶，提供近 60 秒指标给首页；每分钟把上一个完整分钟写入 MySQL `t_metrics`，用于 24H 趋势。Kafka 至少一次消费的重复消息通过 `risk:rt:dedup:{trans_id}` 做 48 小时去重。
