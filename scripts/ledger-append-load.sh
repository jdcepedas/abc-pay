#!/usr/bin/env bash
set -euo pipefail

# Sustained HTTP append load against the ledger service (no DB tampering).
# Useful to exercise append throughput and Grafana "Ledger appends" panels.

cd "$(dirname "$0")/.."

deadline=$(( $(date +%s) + 120 ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  if curl -fsS http://localhost:8083/actuator/health 2>/dev/null | grep -q UP; then
    break
  fi
  sleep 2
done

cd tests/ledger-load

ART_VER=""
if [ -f node_modules/artillery/package.json ]; then
  ART_VER=$(node -p "require('./node_modules/artillery/package.json').version" 2>/dev/null || echo "")
fi
if [ ! -x node_modules/.bin/artillery ] || [ ! -d node_modules/@smithy/node-config-provider ] || [ "$ART_VER" != "2.0.21" ]; then
  echo "==> Installing ledger-load dependencies (Artillery 2.0.21 + pg + smithy)"
  rm -rf node_modules package-lock.json
  npm install --no-fund --no-audit
fi

export ABCPAY_LEDGER_URL="${ABCPAY_LEDGER_URL:-http://127.0.0.1:8083}"
export CI=1

echo "==> Append-only load (60s @ 8 req/s). Ctrl-C to stop early."
exec ./node_modules/.bin/artillery run append-load.yml
