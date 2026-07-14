#!/bin/bash
set -euo pipefail

export JAVA_HOME=${JAVA_HOME:-/home/master0/jdk1.8.0_171}
export HADOOP_HOME=${HADOOP_HOME:-/home/master0/hadoop-2.7.6}
export PATH=$JAVA_HOME/bin:$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$PATH

BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR=$BASE/risk-profile-mapreduce-1.0.0.jar
if [ ! -f "$JAR" ]; then
  JAR=$BASE/target/risk-profile-mapreduce-1.0.0.jar
fi
if [ ! -f "$JAR" ]; then
  echo "JAR not found: $BASE/risk-profile-mapreduce-1.0.0.jar or $BASE/target/risk-profile-mapreduce-1.0.0.jar"
  exit 1
fi
DT=${1:?Usage: ./run_ads_risk_only.sh yyyyMMdd}
DB_URL=${DB_URL:-jdbc:mysql://192.168.125.104:3306/risk_control?useUnicode=true\&characterEncoding=utf8\&useSSL=false}
WINDOW_START=$(date -d "${DT:0:4}-${DT:4:2}-${DT:6:2} -30 days" '+%F 00:00:00')
WINDOW_END=$(date -d "${DT:0:4}-${DT:4:2}-${DT:6:2}" '+%F 00:00:00')
COMMON=(-D "offline.jdbc.url=$DB_URL" -D "offline.jdbc.user=root" -D "offline.jdbc.password=123456" -D "offline.dt=$DT" -D "dfs.replication=1")
BASE_HDFS=/user/hive/warehouse/risk_control.db
DWD=$BASE_HDFS/dwd_trans_event/dt=$DT
SEED=$BASE_HDFS/ods_history_risk_seed_log/dt=$DT/source=mysql
DETAIL=$BASE_HDFS/ads_transaction_risk_detail/dt=$DT
DASHBOARD=$BASE_HDFS/ads_risk_dashboard/dt=$DT
FLOW=$BASE_HDFS/ads_cross_region_risk_flow/dt=$DT

echo "step=ads-risk-detail"
hadoop jar "$JAR" ads-transaction-risk-detail "${COMMON[@]}" -D "offline.window.start=$WINDOW_START" -D "offline.window.end=$WINDOW_END" "$DWD" "$SEED" "$DETAIL"
echo "step=ads-risk-dashboard"
hadoop jar "$JAR" ads-risk-dashboard "${COMMON[@]}" -D "offline.window.start=$WINDOW_START" -D "offline.window.end=$WINDOW_END" "$DETAIL" "$DASHBOARD"
echo "step=ads-cross-region-flow"
hadoop jar "$JAR" ads-cross-region-flow "${COMMON[@]}" "$DETAIL" "$FLOW"
echo "step=ads-mysql"
hadoop jar "$JAR" ads-risk-to-mysql "${COMMON[@]}" "$DETAIL" "$DASHBOARD" "$FLOW"
echo "ADS risk outputs completed for dt=$DT"
