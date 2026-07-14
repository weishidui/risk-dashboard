# risk-profile-mapreduce

本工程实现最新文档里的离线分析链路：Kafka 通过 Flume 落 HDFS ODS，MapReduce 完成 ODS -> DWD -> DWS，并把画像、收款方黑名单、共享设备标记写入 MySQL/Redis，供 Spark Streaming 实时风控查询。

## 已实现作业

| 命令 | 对应文档 | 输入 | 输出 | 额外动作 |
| --- | --- | --- | --- | --- |
| `mysql-to-ods` | ODS 同步 | MySQL `transaction_history` | ODS 原始 JSON | 无 Sqoop 时的内置同步，保留 `cancel_retry_count` |
| `mysql-table-to-ods` | 多表 ODS 同步 | 任意 MySQL 源表 | ODS 原始 JSON | 同步 `login_event`、`trader_user`、`trans_chain` 等源表 |
| `ods-to-dwd` | MR1 | ODS 原始 JSON | DWD 清洗 JSON | 过滤、标准化、补空、按 `trans_id` 去重 |
| `dwd-to-dws-user` | MR2 | DWD | `dws_user_transaction_daily` | 用户日汇总 |
| `dwd-to-dws-cpty` | MR3 | DWD | `dws_counterparty_daily` | 自动写 `counterparty_blacklist` 和 Redis `counterparty:{id}` |
| `dwd-to-dws-device` | MR4 | DWD | `dws_device_daily` | 自动写 Redis `device_shared:{device_id}` 和 `device_risk:{device_id}` |
| `dws-to-user-profile` | MR2-Ext | DWS 用户汇总 + MySQL 账户/登录/风险种子 | profile 输出 | 写满 MySQL `user_profile` 和 Redis `profile:{user_id}` 画像字段 |

说明：文档目标格式写的是 Parquet。为了保证 Hadoop 2.7.6 虚拟机无需额外 Parquet 依赖即可运行，当前 DWD/DWS 先使用一行一个 JSON 的 TextOutputFormat；字段、分层、聚合逻辑与文档一致。确认集群 parquet-hadoop 依赖后可以再切换输出格式。

## Windows 本地构建

```powershell
$env:MAVEN_OPTS='-Duser.home=E:\codex_data\tools'
$mvn='E:\codex_data\tools\apache-maven-3.9.11\bin\mvn.cmd'
$mvnArgs=@('-s','E:\codex_data\tools\maven-settings-aliyun.xml','-Dmaven.repo.local=E:\codex_data\tools\m2','-DskipTests','package')
& $mvn @mvnArgs
```

构建产物：

```text
target/risk-profile-mapreduce-1.0.0.jar
```

## 上传到 master0

把整个工程或 jar 放到 master0 的 `javacode` 目录，例如：

```bash
mkdir -p /home/master0/javacode/risk-profile-mapreduce
# 上传工程源码到 /home/master0/javacode/risk-profile-mapreduce
# 或至少上传 jar 到 /home/master0/javacode/risk-profile-mapreduce/risk-profile-mapreduce-1.0.0.jar
```

如果从 Windows 传 jar：

```powershell
scp C:\Users\b'b\OneDrive\文档\大数据\risk-profile-mapreduce\target\risk-profile-mapreduce-1.0.0.jar master0:/home/master0/javacode/risk-profile-mapreduce/
```

## master0 运行环境

```bash
export JAVA_HOME=/home/master0/jdk1.8.0_171
export HADOOP_HOME=/home/master0/hadoop-2.7.6
export PATH=$JAVA_HOME/bin:$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$PATH

JAR=/home/master0/javacode/risk-profile-mapreduce/risk-profile-mapreduce-1.0.0.jar
DT=$(date -d "yesterday" +%Y%m%d)
```

通用连接参数：

```bash
COMMON="-D offline.jdbc.url=jdbc:mysql://192.168.154.104:3306/risk_control?useUnicode=true\&characterEncoding=utf8\&useSSL=false \
-D offline.jdbc.user=root \
-D offline.jdbc.password=123456 \
-D offline.redis.enabled=true \
-D offline.redis.host=192.168.154.104 \
-D offline.redis.port=6379 \
-D offline.redis.password=123456 \
-D offline.dt=$DT"
```

## 分步运行

同步所有 MySQL 源表到 ODS：

```bash
cd /home/master0/javacode/risk-profile-mapreduce
./sync_all_mysql_tables_to_ods.sh $DT
```

MR1：ODS -> DWD

```bash
hadoop jar $JAR $COMMON ods-to-dwd \
  /user/hive/warehouse/risk_control.db/ods_trans_event/dt=$DT \
  /user/hive/warehouse/risk_control.db/dwd_trans_event/dt=$DT
```

MR2：DWD -> DWS 用户日汇总

```bash
hadoop jar $JAR $COMMON dwd-to-dws-user \
  /user/hive/warehouse/risk_control.db/dwd_trans_event/dt=$DT \
  /user/hive/warehouse/risk_control.db/dws_user_transaction_daily/dt=$DT
```

MR3：DWD -> DWS 收款方日汇总，并写黑名单

```bash
hadoop jar $JAR $COMMON dwd-to-dws-cpty \
  /user/hive/warehouse/risk_control.db/dwd_trans_event/dt=$DT \
  /user/hive/warehouse/risk_control.db/dws_counterparty_daily/dt=$DT
```

MR4：DWD -> DWS 设备日汇总，并写共享设备标记

```bash
hadoop jar $JAR $COMMON dwd-to-dws-device \
  /user/hive/warehouse/risk_control.db/dwd_trans_event/dt=$DT \
  /user/hive/warehouse/risk_control.db/dws_device_daily/dt=$DT
```

MR2-Ext：DWS 用户汇总 -> user_profile + Redis

```bash
hadoop jar $JAR $COMMON dws-to-user-profile \
  /user/hive/warehouse/risk_control.db/dws_user_transaction_daily/dt=$DT \
  /user/hive/warehouse/risk_control.db/ads_user_profile/dt=$DT
```

## 验证

HDFS：

```bash
hdfs dfs -ls /user/hive/warehouse/risk_control.db/dwd_trans_event/dt=$DT
hdfs dfs -cat /user/hive/warehouse/risk_control.db/dws_user_transaction_daily/dt=$DT/part-r-00000 | head
hdfs dfs -cat /user/hive/warehouse/risk_control.db/dws_counterparty_daily/dt=$DT/part-r-00000 | head
hdfs dfs -cat /user/hive/warehouse/risk_control.db/dws_device_daily/dt=$DT/part-r-00000 | head
```

MySQL：

```bash
mysql -h192.168.154.104 -uroot -p123456 -e "USE risk_control; SELECT COUNT(*) FROM user_profile; SELECT COUNT(*) FROM counterparty_blacklist;"
```

Redis：

```bash
redis-cli -h 192.168.154.104 -a 123456 --scan --pattern 'profile:*' | head
redis-cli -h 192.168.154.104 -a 123456 --scan --pattern 'counterparty:*' | head
redis-cli -h 192.168.154.104 -a 123456 --scan --pattern 'device_shared:*' | head
redis-cli -h 192.168.154.104 -a 123456 --scan --pattern 'device_risk:*' | head
```
