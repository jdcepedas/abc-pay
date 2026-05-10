#!/usr/bin/env bash
set -euo pipefail

# Runs the A1-A5 and B1-B4 scenarios against the local Docker stack.
# Requires the stack to be up: `docker compose up -d --build`.

cd "$(dirname "$0")/.."

export ABCPAY_GATEWAY_URL="${ABCPAY_GATEWAY_URL:-http://localhost:8080}"
export ABCPAY_LEDGER_URL="${ABCPAY_LEDGER_URL:-http://localhost:8083}"
export ABCPAY_SHARED_SECRET="${ABCPAY_SHARED_SECRET:-dev-shared-secret-change-me}"
export ABCPAY_JDBC_URL="${ABCPAY_JDBC_URL:-jdbc:postgresql://localhost:5432/abcpay}"
export ABCPAY_JDBC_USER="${ABCPAY_JDBC_USER:-abcpay}"
export ABCPAY_JDBC_PASSWORD="${ABCPAY_JDBC_PASSWORD:-abcpay}"
export ABCPAY_REPORT_DIR="${ABCPAY_REPORT_DIR:-reports}"

if command -v gradle >/dev/null 2>&1; then
  GRADLE=gradle
else
  GRADLE="docker run --rm --network host -v $PWD:/workspace -w /workspace gradle:8.10-jdk21 gradle"
fi

$GRADLE :tests:experiments:run --no-daemon
