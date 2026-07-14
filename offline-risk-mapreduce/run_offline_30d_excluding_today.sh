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
DT=${1:-$(date +%Y%m%d)}
DB_URL=${DB_URL:-jdbc:mysql://192.168.125.104:3306/risk_control?useUnicode=true\&characterEncoding=utf8\&useSSL=false}
DB_USER=${DB_USER:-root}
DB_PASSWORD=${DB_PASSWORD:-123456}
ODS_TRANS=/user/hive/warehouse/risk_control.db/ods_trans_event/dt=$DT
CONTROL_DIR=$BASE/logs
PAUSE_FILE=$CONTROL_DIR/offline_${DT}.pause
CANCEL_FILE=$CONTROL_DIR/offline_${DT}.cancel

mkdir -p "$CONTROL_DIR"
rm -f "$PAUSE_FILE" "$CANCEL_FILE"

check_control() {
  if [ -f "$CANCEL_FILE" ]; then
    echo "task_state=CANCELED"
    echo "Offline analysis canceled at a safe checkpoint"
    exit 130
  fi
  if [ -f "$PAUSE_FILE" ]; then
    echo "task_state=PAUSED"
    echo "Offline analysis paused at a safe checkpoint"
    while [ -f "$PAUSE_FILE" ]; do
      if [ -f "$CANCEL_FILE" ]; then
        echo "task_state=CANCELED"
        echo "Offline analysis canceled while paused"
        exit 130
      fi
      sleep 2
    done
    echo "task_state=RUNNING"
    echo "Offline analysis resumed"
  fi
}

START_TS=$(date -d "${DT:0:4}-${DT:4:2}-${DT:6:2} -30 days" +%s)000
END_TS=$(date -d "${DT:0:4}-${DT:4:2}-${DT:6:2}" +%s)000
START_DATE=$(date -d "@$((START_TS / 1000))" +%F)
END_DATE=$(date -d "@$((END_TS / 1000))" +%F)

COMMON=(
  -D "offline.jdbc.url=$DB_URL"
  -D "offline.jdbc.user=$DB_USER"
  -D "offline.jdbc.password=$DB_PASSWORD"
  -D "offline.dt=$DT"
  -D "dfs.replication=1"
)

echo "Running 30-day offline analysis excluding analysis date"
echo "dt=$DT"
echo "range=$START_DATE 00:00:00 <= trans_timestamp < $END_DATE 00:00:00"
echo "start_ts=$START_TS"
echo "end_ts=$END_TS"

hdfs dfsadmin -safemode leave || true

check_control
echo "step=ods-clean"
echo "Cleaning transaction ODS partition: $ODS_TRANS"
hdfs dfs -rm -r -f "$ODS_TRANS"

check_control
echo "step=ods-transaction"
echo "Importing transaction_history 30-day window to ODS"
hadoop jar "$JAR" mysql-to-ods "${COMMON[@]}" \
  -D "offline.mysql.to.ods.sql=SELECT trans_id,user_id,amount,trans_timestamp,city,geo_location,device_id,network_type,dev_score,ip_address,os_type,os_version,screen_resolution,battery_level,root_jailbreak,sim_operator,user_agent,dns_server,wifi_ssid,trans_type,pay_channel,input_method,click_duration,note,page_url,counterparty_id,counterparty_name,counterparty_bank,login_session_id,login_fail_count,cancel_retry_count FROM transaction_history WHERE trans_timestamp >= $START_TS AND trans_timestamp < $END_TS ORDER BY id" \
  "$DT" "$ODS_TRANS/source=mysql_30d_excluding_today"

check_control
echo "step=ods-dimensions"
echo "Syncing dimension/source tables to ODS for dt=$DT"
for TABLE in \
  login_event \
  trader_user \
  trader_counterparty \
  counterparty_blacklist \
  trans_chain \
  history_risk_seed_log
do
  check_control
  hadoop jar "$JAR" mysql-table-to-ods "${COMMON[@]}" \
    "$DT" "$TABLE" "/user/hive/warehouse/risk_control.db/ods_${TABLE}/dt=$DT/source=mysql"
done

check_control
"$BASE/run_offline_jobs.sh" "$DT"

check_control
echo "30-day offline analysis completed for dt=$DT"
