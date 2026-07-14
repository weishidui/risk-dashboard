#!/bin/bash
set -euo pipefail

export JAVA_HOME=${JAVA_HOME:-/home/master0/jdk1.8.0_171}
export HADOOP_HOME=${HADOOP_HOME:-/home/master0/hadoop-2.7.6}
export SQOOP_HOME=${SQOOP_HOME:-/home/master0/sqoop-1.4.7}
export PATH=$JAVA_HOME/bin:$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$SQOOP_HOME/bin:$PATH

DT=${1:-$(date -d "yesterday" +%Y%m%d)}
DB_URL=${DB_URL:-jdbc:mysql://192.168.154.104:3306/risk_control?useUnicode=true\&characterEncoding=utf8\&useSSL=false}
DB_USER=${DB_USER:-root}
DB_PASSWORD=${DB_PASSWORD:-123456}
ODS_BASE=/user/hive/warehouse/risk_control.db/ods_trans_event/dt=$DT
ODS=$ODS_BASE/source=sqoop
SQOOP_BIN=${SQOOP_BIN:-sqoop}

echo "Syncing MySQL transaction_history to HDFS ODS with Sqoop for dt=$DT"
echo "ODS=$ODS"

hdfs dfs -rm -r -f "$ODS"

"$SQOOP_BIN" import \
  --connect "$DB_URL" \
  --username "$DB_USER" \
  --password "$DB_PASSWORD" \
  --query "SELECT JSON_OBJECT(
      'trans_id', trans_id,
      'user_id', user_id,
      'amount', amount,
      'trans_timestamp', trans_timestamp,
      'timestamp', trans_timestamp,
      'city', city,
      'geo_location', geo_location,
      'device_id', device_id,
      'network_type', network_type,
      'dev_score', dev_score,
      'ip_address', ip_address,
      'os_type', os_type,
      'os_version', os_version,
      'screen_resolution', screen_resolution,
      'battery_level', battery_level,
      'root_jailbreak', root_jailbreak,
      'sim_operator', sim_operator,
      'user_agent', user_agent,
      'dns_server', dns_server,
      'wifi_ssid', wifi_ssid,
      'trans_type', trans_type,
      'pay_channel', pay_channel,
      'input_method', input_method,
      'click_duration', click_duration,
      'note', note,
      'page_url', page_url,
      'counterparty_id', counterparty_id,
      'counterparty_name', counterparty_name,
      'counterparty_bank', counterparty_bank,
      'login_session_id', login_session_id,
      'login_fail_count', login_fail_count,
      'cancel_retry_count', cancel_retry_count,
      'dt', '$DT'
    ) AS json_line
    FROM transaction_history
    WHERE \$CONDITIONS" \
  --target-dir "$ODS" \
  --as-textfile \
  --num-mappers 1 \
  --dfs-replication 1 \
  --null-string '' \
  --null-non-string ''

echo "ODS line count:"
hdfs dfs -cat "$ODS"/* | wc -l
echo "Sync completed for dt=$DT"
