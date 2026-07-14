#!/bin/bash
set -euo pipefail

BASE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DT=${1:-$(date -d "yesterday" +%Y%m%d)}

"$BASE/sync_all_mysql_tables_to_ods.sh" "$DT"
"$BASE/run_offline_jobs.sh" "$DT"

echo "All offline steps completed for dt=$DT"
