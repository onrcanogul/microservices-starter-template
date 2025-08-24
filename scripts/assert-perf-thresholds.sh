#!/usr/bin/env bash
set -euo pipefail

P95_LIMIT_MS=250
ERROR_RATE_LIMIT=0.5

while [[ $# -gt 0 ]]; do
  case $1 in
    --p95-ms) P95_LIMIT_MS="$2"; shift 2 ;;
    --error-rate) ERROR_RATE_LIMIT="$2"; shift 2 ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

REPORT_JSON="target/gatling/*/js/stats.json"

if ! ls $REPORT_JSON 1> /dev/null 2>&1; then
  echo "[ERROR] Gatling stats.json not found"
  exit 1
fi

FILE=$(ls $REPORT_JSON | head -n 1)

P95=$(jq '.stats.percentiles3.total' "$FILE")
ERRORS=$(jq '.stats.numberOfRequests.ko' "$FILE")
TOTAL=$(jq '.stats.numberOfRequests.total' "$FILE")
ERROR_RATE=$(echo "$ERRORS * 100 / $TOTAL" | bc -l)

echo "Perf results: p95=${P95}ms, errorRate=${ERROR_RATE}%"
if (( $(echo "$P95 > $P95_LIMIT_MS" | bc -l) )); then
  echo "[ERROR] p95 latency too high"
  exit 1
fi

if (( $(echo "$ERROR_RATE > $ERROR_RATE_LIMIT" | bc -l) )); then
  echo "[ERROR] Error rate too high"
  exit 1
fi

echo "[INFO] Perf thresholds OK"
