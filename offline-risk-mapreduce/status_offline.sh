#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DT="${1:-$(date +%Y%m%d)}"
WATCH_MODE="${2:-}"
LOG_DIR="$BASE_DIR/logs"
PID_FILE="$LOG_DIR/offline_${DT}.pid"
LOG_FILE="$LOG_DIR/offline_${DT}.log"

print_status() {
  if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo "RUNNING pid=$(cat "$PID_FILE") dt=$DT"
  else
    echo "NOT_RUNNING dt=$DT"
  fi

  if [[ -f "$LOG_FILE" ]]; then
    echo "--- last log lines ---"
    tail -n 20 "$LOG_FILE"
  else
    echo "No log file: $LOG_FILE"
  fi

  if command -v yarn >/dev/null 2>&1; then
    echo "--- YARN offline applications ---"
    yarn application -list -appTypes MAPREDUCE 2>/dev/null | grep -E 'risk-offline|application_' || true
  fi
}

if [[ "$WATCH_MODE" == "--watch" ]]; then
  while true; do
    clear
    echo "Offline analysis monitor: refresh every 5 seconds (Ctrl+C to exit)"
    echo "Updated: $(date '+%F %T')"
    echo
    print_status
    sleep 5
  done
fi

print_status
