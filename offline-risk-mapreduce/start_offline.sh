#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FOLLOW_LOGS=true

if [[ "${1:-}" == "--detach" ]]; then
  FOLLOW_LOGS=false
  shift
fi

DT="${1:-$(date +%Y%m%d)}"
LOG_DIR="$BASE_DIR/logs"
PID_FILE="$LOG_DIR/offline_${DT}.pid"
LOG_FILE="$LOG_DIR/offline_${DT}.log"
RUNNER="$BASE_DIR/run_offline_30d_excluding_today.sh"

if [[ $# -gt 1 ]] || [[ ! "$DT" =~ ^[0-9]{8}$ ]]; then
  echo "Usage: $0 [--detach] [yyyyMMdd]" >&2
  exit 2
fi

if [[ ! -x "$RUNNER" ]]; then
  echo "Offline runner is not executable: $RUNNER" >&2
  exit 1
fi

mkdir -p "$LOG_DIR"

follow_log() {
  local task_pid="$1"
  touch "$LOG_FILE"
  echo "Following log: $LOG_FILE"
  echo "Press Ctrl+C to stop viewing logs; the offline task keeps running."
  tail -n 20 -F "$LOG_FILE" &
  local tail_pid=$!

  trap 'kill "$tail_pid" 2>/dev/null || true; echo "Stopped log viewing. Offline task is still running: pid=$task_pid"; exit 0' INT TERM
  while kill -0 "$task_pid" 2>/dev/null; do
    sleep 1
  done
  kill "$tail_pid" 2>/dev/null || true
  wait "$tail_pid" 2>/dev/null || true
  trap - INT TERM
  echo "Offline task process ended. Check final status with: $BASE_DIR/status_offline.sh $DT"
}

if [[ -f "$PID_FILE" ]]; then
  old_pid="$(cat "$PID_FILE")"
  if kill -0 "$old_pid" 2>/dev/null; then
    echo "Offline analysis is already running for dt=$DT, pid=$old_pid"
    echo "Log: $LOG_FILE"
    if [[ "$FOLLOW_LOGS" == true ]]; then
      follow_log "$old_pid"
    fi
    exit 0
  fi
  rm -f "$PID_FILE"
fi

export JAVA_HOME="${JAVA_HOME:-/home/master0/jdk1.8.0_171}"
export HADOOP_HOME="${HADOOP_HOME:-/home/master0/hadoop-2.7.6}"
export PATH="$JAVA_HOME/bin:$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$PATH"
export DB_URL="${DB_URL:-jdbc:mysql://192.168.125.104:3306/risk_control?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai}"
export DB_USER="${DB_USER:-root}"
export DB_PASSWORD="${DB_PASSWORD:-123456}"
export REDIS_HOST="${REDIS_HOST:-192.168.125.104}"
export REDIS_PORT="${REDIS_PORT:-6379}"
export REDIS_PASSWORD="${REDIS_PASSWORD:-123456}"
export HISTORY_HOST="${HISTORY_HOST:-192.168.125.100}"

history_ready() {
  jps -l | grep -q 'org.apache.hadoop.mapreduce.v2.hs.JobHistoryServer' \
    && (echo > "/dev/tcp/$HISTORY_HOST/10020") >/dev/null 2>&1
}

if ! history_ready; then
  "$HADOOP_HOME/sbin/mr-jobhistory-daemon.sh" start historyserver
  for _ in $(seq 1 30); do
    if history_ready; then
      break
    fi
    sleep 2
  done
fi

if ! history_ready; then
  echo "MapReduce JobHistory Server is not ready on $HISTORY_HOST:10020" >&2
  exit 1
fi

echo "MapReduce JobHistory Server is ready on $HISTORY_HOST:10020"
touch "$LOG_FILE"
nohup "$RUNNER" "$DT" >"$LOG_FILE" 2>&1 &
pid=$!
echo "$pid" >"$PID_FILE"
START_DATE="$(date -d "${DT:0:4}-${DT:4:2}-${DT:6:2} -30 days" +%F)"
END_DATE="${DT:0:4}-${DT:4:2}-${DT:6:2}"

echo "Offline analysis started: dt=$DT, pid=$pid"
echo "Window: [$START_DATE 00:00:00, $END_DATE 00:00:00)"
echo "Log: $LOG_FILE"
echo "Status: $BASE_DIR/status_offline.sh $DT"

if [[ "$FOLLOW_LOGS" == true ]]; then
  follow_log "$pid"
fi
