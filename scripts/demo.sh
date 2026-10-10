#!/usr/bin/env bash
# End-to-end walkthrough of every scenario. Needs: curl, jq, and the stack running
# (docker compose up --build). Re-runnable: every run uses fresh STANs and keys.
set -euo pipefail

AUTH=${AUTH:-http://localhost:8081}
PAY=${PAY:-http://localhost:8082}
REP=${REP:-http://localhost:8084}
ISS=${ISS:-http://localhost:8083}
JSON='Content-Type: application/json'
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

command -v jq >/dev/null || { echo "Please install jq"; exit 1; }

step() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
note() { printf '\033[0;33m   %s\033[0m\n' "$*"; }
stan() { printf '%06d' $(( (RANDOM * 32768 + RANDOM) % 1000000 )); }
key()  { echo "demo-$(date +%s)-$RANDOM$RANDOM"; }

# pay <token> <idempotency-key> <stan> <amountMinor> <card> [entryMode] -> writes $TMP/code and $TMP/body
pay() {
  curl -s -o "$TMP/body" -w '%{http_code}' -X POST "$PAY/api/v1/payments" \
    -H "$JSON" -H "Authorization: Bearer $1" -H "Idempotency-Key: $2" \
    -d "{\"terminalId\":\"T0001\",\"stan\":\"$3\",\"amountMinor\":$4,\"currency\":\"INR\",\"cardNumber\":\"$5\",\"entryMode\":\"${6:-CHIP}\"}" \
    > "$TMP/code"
  echo "   HTTP $(cat "$TMP/code")  $(jq -c '{paymentId,status,responseCode,responseMessage,authCode,maskedPan} // .' "$TMP/body" 2>/dev/null || cat "$TMP/body")"
}

step "1. Authentication: terminal (client credentials) and users (password + refresh token)"
TERMINAL=$(curl -sf -X POST "$AUTH/auth/terminal-token" -H "$JSON" -d '{"terminalId":"T0001","secret":"Terminal@123"}' | jq -r .accessToken)
ADMIN=$(curl -sf -X POST "$AUTH/auth/login" -H "$JSON" -d '{"username":"admin","password":"Admin@123"}' | jq -r .accessToken)
AUDITOR=$(curl -sf -X POST "$AUTH/auth/login" -H "$JSON" -d '{"username":"auditor","password":"Auditor@123"}' | jq -r .accessToken)
MERCHANT_LOGIN=$(curl -sf -X POST "$AUTH/auth/login" -H "$JSON" -d '{"username":"merchant1","password":"Merchant@123"}')
MERCHANT=$(echo "$MERCHANT_LOGIN" | jq -r .accessToken)
MERCHANT2=$(curl -sf -X POST "$AUTH/auth/login" -H "$JSON" -d '{"username":"merchant2","password":"Merchant@123"}' | jq -r .accessToken)
note "terminal token claims: $(curl -sf "$AUTH/auth/me" -H "Authorization: Bearer $TERMINAL" | jq -c .)"

step "2. Successful chip payment (Rs 1,250.00)"
K1=$(key); S1=$(stan)
pay "$TERMINAL" "$K1" "$S1" 125000 4111111111111111
P1=$(jq -r .paymentId "$TMP/body")

step "3. Customer/terminal retries the SAME request -> stored result replayed, no second charge"
pay "$TERMINAL" "$K1" "$S1" 125000 4111111111111111
note "Idempotent-Replayed header + HTTP 200; issuer holds only one authorization"

step "4. Same Idempotency-Key, different amount -> rejected (422)"
pay "$TERMINAL" "$K1" "$S1" 999900 4111111111111111

step "5. Ten concurrent submissions of one payment (double-tap + retries)"
K5=$(key); S5=$(stan)
for i in $(seq 1 10); do
  curl -s -o /dev/null -w '%{http_code}\n' -X POST "$PAY/api/v1/payments" \
    -H "$JSON" -H "Authorization: Bearer $TERMINAL" -H "Idempotency-Key: $K5" \
    -d "{\"terminalId\":\"T0001\",\"stan\":\"$S5\",\"amountMinor\":50000,\"currency\":\"INR\",\"cardNumber\":\"5555555555554444\",\"entryMode\":\"CONTACTLESS\"}" \
    >> "$TMP/parallel" &
done
wait
note "status codes: $(sort "$TMP/parallel" | uniq -c | tr '\n' ' ')"
note "expect exactly one 201; the rest 200 (replayed) or 409 (first one still in flight)"

step "6. Declines: insufficient funds (51), expired card (54), bad card number"
pay "$TERMINAL" "$(key)" "$(stan)" 500000 4000000000000002
pay "$TERMINAL" "$(key)" "$(stan)" 10000 4000000000000069
pay "$TERMINAL" "$(key)" "$(stan)" 10000 4111111111111112

step "7. Issuer approves but the response is lost (timeout) -> automatic reversal"
pay "$TERMINAL" "$(key)" "$(stan)" 77700 4000000000000119
P7=$(jq -r .paymentId "$TMP/body")
note "waiting for the reversal job (store-and-forward)..."
for _ in $(seq 1 12); do
  ST=$(curl -s "$PAY/api/v1/payments/$P7" -H "Authorization: Bearer $ADMIN" | jq -r .status)
  [ "$ST" = "REVERSED" ] && break
  sleep 2
done
note "payment $P7 is now: $ST"
sleep 8   # let the slow issuer finish its late authorization
note "issuer balances (slow card must show held=0): $(curl -s "$ISS/issuer/accounts" | jq -c '.[] | select(.card|startswith("400000")) | {card,heldMinor}' | tr '\n' ' ')"

step "8. Authorization rules"
note "merchant2 reading merchant1's payment -> $(curl -s -o /dev/null -w '%{http_code}' "$PAY/api/v1/payments/$P1" -H "Authorization: Bearer $MERCHANT2") (expect 403)"
note "terminal trying to capture -> $(curl -s -o /dev/null -w '%{http_code}' -X POST "$PAY/api/v1/payments/$P1/capture" -H "Authorization: Bearer $TERMINAL") (expect 403)"
note "no token -> $(curl -s -o /dev/null -w '%{http_code}' "$PAY/api/v1/payments/$P1") (expect 401)"

step "9. Merchant voids one authorization and captures another"
K9=$(key); pay "$TERMINAL" "$K9" "$(stan)" 30000 4111111111111111
P9=$(jq -r .paymentId "$TMP/body")
note "void:    $(curl -s -X POST "$PAY/api/v1/payments/$P9/void" -H "Authorization: Bearer $MERCHANT" | jq -c '{status,responseMessage}')"
note "capture: $(curl -s -X POST "$PAY/api/v1/payments/$P1/capture" -H "Authorization: Bearer $MERCHANT" -H "$JSON" -d '{}' | jq -c '{status,capturedAmountMinor}')"
note "capture again (idempotent): $(curl -s -X POST "$PAY/api/v1/payments/$P1/capture" -H "Authorization: Bearer $MERCHANT" | jq -c '{status}')"

step "10. End-of-day batch close (ADMIN)"
curl -s -X POST "$PAY/api/v1/batches/close?merchantId=M1001" -H "Authorization: Bearer $ADMIN" | jq -c .

step "11. Reports"
sleep 3   # outbox relay + consumer lag
TODAY=$(TZ=Asia/Kolkata date +%F)
echo "   auditor summary (all merchants):"
curl -s "$REP/api/v1/reports/summary?from=$TODAY" -H "Authorization: Bearer $AUDITOR" | jq '{totalTransactions,approvedCount,approvedAmountMinor,approvalRatePercent,declinedCount,reversedCount,duplicatesBlocked}'
echo "   merchant1 summary (automatically scoped to M1001):"
curl -s "$REP/api/v1/reports/summary" -H "Authorization: Bearer $MERCHANT" | jq -c '{merchantId,totalTransactions,approvedCount}'
echo "   timeline of payment $P1:"
curl -s "$REP/api/v1/reports/transactions/$P1" -H "Authorization: Bearer $AUDITOR" | jq -c '.events[] | {eventType,status,sequence}'
echo "   CSV export (first lines):"
curl -s "$REP/api/v1/reports/transactions/export.csv?from=$TODAY" -H "Authorization: Bearer $ADMIN" | head -4

step "12. Refresh-token rotation and stolen-token detection"
R1=$(echo "$MERCHANT_LOGIN" | jq -r .refreshToken)
R2=$(curl -sf -X POST "$AUTH/auth/refresh" -H "$JSON" -d "{\"refreshToken\":\"$R1\"}" | jq -r .refreshToken)
note "rotated: got a new refresh token"
note "replaying the OLD token -> $(curl -s -X POST "$AUTH/auth/refresh" -H "$JSON" -d "{\"refreshToken\":\"$R1\"}" | jq -r .detail)"
note "new token now revoked too -> $(curl -s -o /dev/null -w '%{http_code}' -X POST "$AUTH/auth/refresh" -H "$JSON" -d "{\"refreshToken\":\"$R2\"}") (expect 401)"

printf '\n\033[1;32mDone.\033[0m\n'
