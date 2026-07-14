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

COMMON=(
  -D "offline.jdbc.url=$DB_URL"
  -D "offline.jdbc.user=$DB_USER"
  -D "offline.jdbc.password=$DB_PASSWORD"
  -D "offline.dt=$DT"
  -D "dfs.replication=1"
)

echo "Syncing all MySQL source tables to HDFS ODS for dt=$DT"

hadoop jar "$JAR" mysql-to-ods "${COMMON[@]}" \
  "$DT" "/user/hive/warehouse/risk_control.db/ods_trans_event/dt=$DT/source=sqoop"

for TABLE in \
  login_event \
  trader_user \
  trader_counterparty \
  counterparty_blacklist \
  trans_chain \
  history_risk_seed_log
do
  hadoop jar "$JAR" mysql-table-to-ods "${COMMON[@]}" \
    "$DT" "$TABLE" "/user/hive/warehouse/risk_control.db/ods_${TABLE}/dt=$DT/source=mysql"
done

echo "All MySQL ODS sync completed for dt=$DT"
