#!/usr/bin/env bash
set -euo pipefail

THRESHOLD=${1:-80}
REPORT_FILE="target/site/jacoco/jacoco.xml"

if [[ ! -f "$REPORT_FILE" ]]; then
  echo "[ERROR] Coverage report not found: $REPORT_FILE"
  exit 1
fi

COVERAGE=$(xmllint --xpath "string(//report/counter[@type='INSTRUCTION']/@covered)" "$REPORT_FILE")
MISSED=$(xmllint --xpath "string(//report/counter[@type='INSTRUCTION']/@missed)" "$REPORT_FILE")

TOTAL=$((COVERAGE + MISSED))
PERCENT=$((COVERAGE * 100 / TOTAL))

echo "Coverage: $PERCENT% (Threshold: $THRESHOLD%)"

if (( PERCENT < THRESHOLD )); then
  echo "[ERROR] Coverage below threshold"
  exit 1
fi

echo "[INFO] Coverage OK"
