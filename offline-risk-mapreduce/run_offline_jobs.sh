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
DT=${1:-$(date -d "yesterday" +%Y%m%d)}
DB_URL=${DB_URL:-jdbc:mysql://192.168.154.104:3306/risk_control?useUnicode=true\&characterEncoding=utf8\&useSSL=false}
DB_USER=${DB_USER:-root}
DB_PASSWORD=${DB_PASSWORD:-123456}
REDIS_HOST=${REDIS_HOST:-192.168.154.104}
REDIS_PORT=${REDIS_PORT:-6379}
REDIS_PASSWORD=${REDIS_PASSWORD:-123456}

COMMON=(
  -D "offline.jdbc.url=$DB_URL"
  -D "offline.jdbc.user=$DB_USER"
  -D "offline.jdbc.password=$DB_PASSWORD"
  -D "offline.redis.enabled=true"
  -D "offline.redis.host=$REDIS_HOST"
  -D "offline.redis.port=$REDIS_PORT"
  -D "offline.redis.password=$REDIS_PASSWORD"
  -D "offline.dt=$DT"
  -D "dfs.replication=1"
)

ODS=/user/hive/warehouse/risk_control.db/ods_trans_event/dt=$DT
DWD=/user/hive/warehouse/risk_control.db/dwd_trans_event/dt=$DT
DWS_USER=/user/hive/warehouse/risk_control.db/dws_user_transaction_daily/dt=$DT
DWS_CPTY=/user/hive/warehouse/risk_control.db/dws_counterparty_daily/dt=$DT
DWS_DEVICE=/user/hive/warehouse/risk_control.db/dws_device_daily/dt=$DT
DWS_USER_RISK=/user/hive/warehouse/risk_control.db/dws_user_risk_daily/dt=$DT
ADS_PROFILE=/user/hive/warehouse/risk_control.db/ads_user_profile/dt=$DT
ADS_RISK_DETAIL=/user/hive/warehouse/risk_control.db/ads_transaction_risk_detail/dt=$DT
ADS_RISK_DASHBOARD=/user/hive/warehouse/risk_control.db/ads_risk_dashboard/dt=$DT
ADS_RISK_FLOW=/user/hive/warehouse/risk_control.db/ads_cross_region_risk_flow/dt=$DT
ODS_USER=/user/hive/warehouse/risk_control.db/ods_trader_user/dt=$DT/source=mysql
ODS_LOGIN=/user/hive/warehouse/risk_control.db/ods_login_event/dt=$DT/source=mysql
ODS_RISK_SEED=/user/hive/warehouse/risk_control.db/ods_history_risk_seed_log/dt=$DT/source=mysql
WINDOW_START=$(date -d "${DT:0:4}-${DT:4:2}-${DT:6:2} -30 days" '+%F 00:00:00')
WINDOW_END=$(date -d "${DT:0:4}-${DT:4:2}-${DT:6:2}" '+%F 00:00:00')

echo "Running offline risk jobs for dt=$DT"
echo "JAR=$JAR"
echo "window=$WINDOW_START <= event_time < $WINDOW_END"

echo "step=ods-to-dwd"
hadoop jar "$JAR" ods-to-dwd "${COMMON[@]}" "$ODS" "$DWD"
echo "step=dws-user"
hadoop jar "$JAR" dwd-to-dws-user "${COMMON[@]}" "$DWD" "$DWS_USER"
echo "step=dws-counterparty"
hadoop jar "$JAR" dwd-to-dws-cpty "${COMMON[@]}" "$DWD" "$DWS_CPTY"
echo "step=dws-device"
hadoop jar "$JAR" dwd-to-dws-device "${COMMON[@]}" "$DWD" "$DWS_DEVICE"
echo "step=dws-risk"
hadoop jar "$JAR" history-risk-to-dws-user-risk "${COMMON[@]}" "$ODS" "$ODS_RISK_SEED" "$DWS_USER_RISK"
echo "step=ads-profile"
hadoop jar "$JAR" dws-to-user-profile "${COMMON[@]}" \
  -D "offline.profile.mysql.enabled=false" \
  -D "offline.redis.enabled=false" \
  "$DWS_USER" "$ODS_USER" "$ODS_LOGIN" "$DWS_USER_RISK" "$ADS_PROFILE"
echo "step=mysql-profile"
hadoop jar "$JAR" ads-user-profile-to-mysql "${COMMON[@]}" \
  -D "offline.profile.mysql.batch.size=500" \
  "$ADS_PROFILE"
echo "step=ads-risk-detail"
hadoop jar "$JAR" ads-transaction-risk-detail "${COMMON[@]}" \
  -D "offline.window.start=$WINDOW_START" \
  -D "offline.window.end=$WINDOW_END" \
  "$DWD" "$ODS_RISK_SEED" "$ADS_RISK_DETAIL"
echo "step=ads-risk-dashboard"
hadoop jar "$JAR" ads-risk-dashboard "${COMMON[@]}" \
  -D "offline.window.start=$WINDOW_START" \
  -D "offline.window.end=$WINDOW_END" \
  "$ADS_RISK_DETAIL" "$ADS_RISK_DASHBOARD"
echo "step=ads-cross-region-flow"
hadoop jar "$JAR" ads-cross-region-flow "${COMMON[@]}" \
  "$ADS_RISK_DETAIL" "$ADS_RISK_FLOW"
echo "step=ads-mysql"
hadoop jar "$JAR" ads-risk-to-mysql "${COMMON[@]}" \
  -D "offline.ads.mysql.batch.size=1000" \
  "$ADS_RISK_DETAIL" "$ADS_RISK_DASHBOARD" "$ADS_RISK_FLOW"

echo "Offline jobs completed for dt=$DT"
