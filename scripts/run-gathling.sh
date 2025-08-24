#!/usr/bin/env bash
set -euo pipefail

echo "▶[INFO] Running Gatling tests..."
./mvnw -q -B gatling:test

REPORT_DIR=$(find target/gatling -type d -name "*-simulation" | sort | tail -n 1)
echo "Gatling report at: $REPORT_DIR/index.html"