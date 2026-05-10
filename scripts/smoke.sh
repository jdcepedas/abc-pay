#!/usr/bin/env bash
set -euo pipefail

# Sends one valid signed POST through the gateway, verifying the full
# ASR-SEG-02 happy path: signature OK -> payment persisted -> ledger appended.
# Requires `python3` for hex hmac calc.

GATEWAY_URL="${ABCPAY_GATEWAY_URL:-http://localhost:8080}"
SECRET="${ABCPAY_SHARED_SECRET:-dev-shared-secret-change-me}"
TS=$(date +%s)
IDEM=$(uuidgen 2>/dev/null || python3 -c "import uuid; print(uuid.uuid4())")
BODY='{"amount":"123.45","currency":"COP","destinationAccount":"ACC-99999","reference":"smoke-test","sourceAccount":"ACC-12345"}'

SIG=$(python3 -c "
import hmac, hashlib, sys
secret = b'$SECRET'
msg = b'POST\n/api/payments\n$BODY\n$TS\n$IDEM'
print(hmac.new(secret, msg, hashlib.sha256).hexdigest())
")

echo "POST $GATEWAY_URL/api/payments"
curl -sS -X POST "$GATEWAY_URL/api/payments" \
  -H "Content-Type: application/json" \
  -H "X-Signature: $SIG" \
  -H "X-Timestamp: $TS" \
  -H "X-Idempotency-Key: $IDEM" \
  --data "$BODY" | python3 -m json.tool
echo
echo "GET $GATEWAY_URL ledger verify"
curl -sS "${ABCPAY_LEDGER_URL:-http://localhost:8083}/api/ledger/verify" | python3 -m json.tool
