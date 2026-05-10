#!/usr/bin/env bash
set -euo pipefail

# Resets all observability state to give a clean dashboard before a demo run.
#
#   1) Recreates the four Spring Boot services so their Micrometer counters
#      drop back to zero.
#   2) Wipes the Prometheus TSDB volume so historical scrapes are gone.
#
# Postgres is intentionally preserved (the ledger keeps its hash chain).
# Grafana state is preserved too; the dashboard simply re-populates from
# Prometheus once new scrapes start arriving.

cd "$(dirname "$0")/.."

SERVICES=(api-gateway signature-validator payments-service ledger-service prometheus)

echo "==> Stopping ${SERVICES[*]}"
docker compose stop "${SERVICES[@]}"

echo "==> Removing the containers so the Prometheus volume can be detached"
docker compose rm -f "${SERVICES[@]}"

echo "==> Wiping Prometheus TSDB"
docker volume rm abcpay-prometheus-data 2>/dev/null || true

echo "==> Bringing the stack back up"
docker compose up -d

echo "==> Waiting for gateway and Prometheus to become ready"
deadline=$(( $(date +%s) + 90 ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  gateway_ok=0
  prom_ok=0
  curl -fsS http://localhost:8080/actuator/health 2>/dev/null | grep -q UP && gateway_ok=1
  curl -fsS http://localhost:9090/-/ready 2>/dev/null | grep -qi "ready" && prom_ok=1
  if [ "$gateway_ok" = "1" ] && [ "$prom_ok" = "1" ]; then
    break
  fi
  sleep 2
done

cat <<EOF

Reset complete. All metrics now start at zero.

  Grafana    http://localhost:3000/d/asr-seg-02
  Prometheus http://localhost:9090

Tip: in Grafana, set the time-range picker (top right) to "Last 5 minutes"
with auto-refresh 5s while running the demo so the panels animate cleanly.

EOF
