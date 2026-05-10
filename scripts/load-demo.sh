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

  Grafana       http://localhost:3000/d/asr-seg-02 (anonymous viewer enabled)
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

if [ ! -d tests/load/node_modules ]; then
  echo "==> Installing Artillery (one-time)"
  npm --prefix tests/load install --silent
fi

echo "==> Starting Artillery run (Ctrl-C to stop)"
ABCPAY_GATEWAY_URL="${ABCPAY_GATEWAY_URL:-http://localhost:8080}" \
ABCPAY_SHARED_SECRET="${ABCPAY_SHARED_SECRET:-dev-shared-secret-change-me}" \
  npx --prefix tests/load artillery run tests/load/scenarios.yml
