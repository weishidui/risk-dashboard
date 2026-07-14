#!/usr/bin/env bash
set -euo pipefail

APP_HOME=/home/master0/risk-dashboard
JAVA_HOME=/home/master0/jdk1.8.0_171
JAR="$APP_HOME/risk-dashboard-backend-1.0.0.jar"
PID_FILE="$APP_HOME/risk-dashboard.pid"
LOG_DIR="$APP_HOME/logs"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "Java executable not found: $JAVA_HOME/bin/java" >&2
  exit 1
fi

if [[ ! -f "$JAR" ]]; then
  echo "Dashboard Jar not found: $JAR" >&2
  exit 1
fi

if [[ -f "$PID_FILE" ]] && ps -p "$(<"$PID_FILE")" >/dev/null 2>&1; then
  echo "risk-dashboard already running, pid=$(<"$PID_FILE")"
  exit 0
fi

rm -f "$PID_FILE"
mkdir -p "$LOG_DIR"

export SPRING_PROFILES_ACTIVE=prod
nohup "$JAVA_HOME/bin/java" -jar "$JAR" \
  --spring.profiles.active=prod \
  --server.port=8080 \
  --logging.file.path="$LOG_DIR" \
  --logging.file.name="$LOG_DIR/risk-dashboard.log" \
  >"$LOG_DIR/console.out" 2>"$LOG_DIR/console.err" &

echo $! >"$PID_FILE"
echo "risk-dashboard started, pid=$(<"$PID_FILE"), profile=prod"
echo "console log: $LOG_DIR/console.out"
echo "app log: $LOG_DIR/risk-dashboard.log"
