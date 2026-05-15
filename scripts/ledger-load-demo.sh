#!/usr/bin/env bash
set -euo pipefail

# Runs Artillery-driven ledger immutability checks (B1-B4).
# Requires docker compose up (ledger on 8083, postgres on 5432).

cd "$(dirname "$0")/.."

echo "==> Waiting for ledger HTTP health"
deadline=$(( $(date +%s) + 120 ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  if curl -fsS http://localhost:8083/actuator/health 2>/dev/null | grep -q UP; then
    break
  fi
  sleep 2
done

cd tests/ledger-load

# Artillery's CLI pulls AWS helper code that expects @smithy/node-config-provider.
# Pin artillery to 2.0.21 — newer minors (e.g. 2.0.31) have shipped broken installs where
# the `run` command never registers and Oclif suggests the unrelated `dino` easter egg.
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
export ABCPAY_DB_URL="${ABCPAY_DB_URL:-postgresql://abcpay:abcpay@127.0.0.1:5432/abcpay}"
export ABCPAY_LEDGER_SEED_SIZE="${ABCPAY_LEDGER_SEED_SIZE:-20}"

# Non-interactive CLI: without this, a missing `run` command can prompt "Did you mean dino?"
# — dino is an easter egg, not the load-test runner.
export CI=1

echo "==> Running ledger B1-B4 Artillery suite"
echo "    LEDGER_URL=$ABCPAY_LEDGER_URL"
echo "    DB_URL=$ABCPAY_DB_URL (host must reach Postgres; same as compose port 5432)"
cat <<EOF

Tip: open the ledger Grafana dashboard while this runs (verify spikes):
  http://localhost:3000/d/immutable-ledger

EOF
exec ./node_modules/.bin/artillery run scenarios.yml
