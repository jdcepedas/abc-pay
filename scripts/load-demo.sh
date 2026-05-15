#!/usr/bin/env bash
set -euo pipefail

# Boots the full stack (incl. Prometheus + Grafana) and starts an Artillery
# run that mixes valid and tampered traffic against the gateway. Open Grafana
# in another tab to see the live "valid vs invalid" panel update.

cd "$(dirname "$0")/.."

echo "==> Bringing up the stack with observability"
docker compose up -d --build

echo
echo "==> Waiting for the API gateway to report healthy"
deadline=$(( $(date +%s) + 180 ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  if curl -fsS http://localhost:8080/actuator/health 2>/dev/null | grep -q UP; then
    break
  fi
  sleep 2
done

cat <<EOF

Stack is up. Useful URLs:

  Grafana (integrity)   http://localhost:3000/d/asr-seg-02 (anonymous viewer enabled)
  Grafana (ledger)      http://localhost:3000/d/immutable-ledger
  Prometheus    http://localhost:9090

  Swagger UIs
    API gateway          http://localhost:8080/swagger-ui.html
    Signature validator  http://localhost:8081/swagger-ui.html
    Payments service     http://localhost:8082/swagger-ui.html
    Ledger service       http://localhost:8083/swagger-ui.html

  Health checks
    API gateway   http://localhost:8080/actuator/health
    Validator     http://localhost:8081/actuator/health
    Payments      http://localhost:8082/actuator/health
    Ledger        http://localhost:8083/actuator/health

EOF

cd tests/load

ART_VER=""
if [ -f node_modules/artillery/package.json ]; then
  ART_VER=$(node -p "require('./node_modules/artillery/package.json').version" 2>/dev/null || echo "")
fi
if [ ! -x node_modules/.bin/artillery ] || [ ! -d node_modules/@smithy/node-config-provider ] || [ "$ART_VER" != "2.0.21" ]; then
  echo "==> Installing Artillery 2.0.21 + smithy (one-time, may take ~30s)"
  rm -rf node_modules package-lock.json
  npm install --no-fund --no-audit
fi

echo "==> Starting Artillery run (Ctrl-C to stop)"
export CI=1
export ABCPAY_GATEWAY_URL="${ABCPAY_GATEWAY_URL:-http://localhost:8080}"
export ABCPAY_SHARED_SECRET="${ABCPAY_SHARED_SECRET:-dev-shared-secret-change-me}"
exec ./node_modules/.bin/artillery run scenarios.yml
