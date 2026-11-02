#!/usr/bin/env bash
# Runs the demo scenarios against a running stack and reports what happened.
# Usage:  ./scripts/demo.sh [all|happy|declined|nostock|outage]
#         RUNS=3 ./scripts/demo.sh happy        (repeat the happy path, handy for timing)
set -euo pipefail

ORDER_API="${ORDER_API:-http://localhost:8080}"
INVENTORY_API="${INVENTORY_API:-http://localhost:8081}"
PAYMENT_API="${PAYMENT_API:-http://localhost:8082}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-180}"
RUNS="${RUNS:-1}"
SCENARIO="${1:-all}"
DEMO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../docs/demo" && pwd)"
failures=0
FINAL_BODY=""

str_field() { grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4 || true; }
num_field() { grep -o "\"$1\":[0-9]*" | head -1 | cut -d: -f2 || true; }

wait_stack() {
  echo "Waiting for the services to be healthy..."
  local url deadline
  for url in "$ORDER_API" "$INVENTORY_API" "$PAYMENT_API"; do
    deadline=$(( $(date +%s) + 180 ))
    until curl -fsS "$url/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
      if [ "$(date +%s)" -gt "$deadline" ]; then echo "$url did not become healthy in time"; exit 1; fi
      sleep 2
    done
  done
}

place_order() {
  curl -fsS -X POST "$ORDER_API/api/orders" -H 'Content-Type: application/json' -d @"$DEMO_DIR/$1"
}

set_gateway() {
  curl -fsS -X PUT "$PAYMENT_API/api/admin/gateway" -H 'Content-Type: application/json' -d @"$DEMO_DIR/$1" > /dev/null
}

wait_final() {
  local id="$1" start now status last=""
  start=$(date +%s)
  while true; do
    FINAL_BODY="$(curl -fsS "$ORDER_API/api/orders/$id")"
    status="$(printf '%s' "$FINAL_BODY" | str_field status)"
    if [ "$status" != "$last" ]; then
      now=$(date +%s)
      printf '   %4ss  %s\n' "$((now - start))" "$status"
      last="$status"
    fi
    case "$status" in CONFIRMED|REJECTED|CANCELLED) return 0 ;; esac
    if [ $(( $(date +%s) - start )) -gt "$TIMEOUT_SECONDS" ]; then
      echo "   Order $id did not finish within ${TIMEOUT_SECONDS}s (last status: $last)"
      exit 1
    fi
    sleep 0.5
  done
}

show_stock() {
  local body
  body="$(curl -fsS "$INVENTORY_API/api/inventory/$1")"
  echo "   stock $1: available=$(printf '%s' "$body" | num_field available) reserved=$(printf '%s' "$body" | num_field reserved)"
}

show_payment() {
  local body
  if body="$(curl -fsS "$PAYMENT_API/api/payments/order/$1" 2>/dev/null)"; then
    echo "   payment: $(printf '%s' "$body" | str_field status) $(printf '%s' "$body" | str_field failureReason)"
  else
    echo "   payment: none recorded"
  fi
}

check() {
  local actual
  actual="$(printf '%s' "$FINAL_BODY" | str_field status)"
  if [ "$actual" = "$1" ]; then
    echo "   PASS: ended $actual"
  else
    echo "   FAIL: expected $1 but ended $actual"
    failures=$((failures + 1))
  fi
}

scenario_happy() {
  echo; echo "== Happy path: stock reserved, payment approved =="
  show_stock SKU-1001
  local order id
  order="$(place_order order-happy.json)"; id="$(printf '%s' "$order" | str_field id)"
  echo "   order $id accepted"
  wait_final "$id"
  show_stock SKU-1001
  show_payment "$id"
  check CONFIRMED
}

scenario_declined() {
  echo; echo "== Payment declined: stock is reserved, then released (compensation) =="
  show_stock SKU-1002
  local order id
  order="$(place_order order-declined.json)"; id="$(printf '%s' "$order" | str_field id)"
  echo "   order $id accepted"
  wait_final "$id"
  echo "   reason: $(printf '%s' "$FINAL_BODY" | str_field failureReason)"
  show_stock SKU-1002
  show_payment "$id"
  check CANCELLED
}

scenario_nostock() {
  echo; echo "== Not enough stock: rejected, payment is never requested =="
  local order id
  order="$(place_order order-no-stock.json)"; id="$(printf '%s' "$order" | str_field id)"
  echo "   order $id accepted"
  wait_final "$id"
  echo "   reason: $(printf '%s' "$FINAL_BODY" | str_field failureReason)"
  show_payment "$id"
  check REJECTED
}

scenario_outage() {
  echo; echo "== Payment gateway outage: the order recovers once the gateway is back =="
  trap 'set_gateway gateway-up.json || true' EXIT   # never leave the demo gateway broken
  set_gateway gateway-down.json
  echo "   payment gateway is now failing"
  local order id
  order="$(place_order order-happy.json)"; id="$(printf '%s' "$order" | str_field id)"
  echo "   order $id accepted"
  sleep 15
  set_gateway gateway-up.json
  echo "   payment gateway healed; waiting for the saga to re-request payment"
  wait_final "$id"
  show_payment "$id"
  check CONFIRMED
}

wait_stack

case "$SCENARIO" in
  all)      todo="happy declined nostock outage" ;;
  happy|declined|nostock|outage) todo="$SCENARIO" ;;
  *) echo "Unknown scenario '$SCENARIO'. Use: all | happy | declined | nostock | outage"; exit 2 ;;
esac

for name in $todo; do
  count=1
  [ "$name" = "happy" ] && count="$RUNS"
  for i in $(seq 1 "$count"); do
    [ "$count" -gt 1 ] && echo && echo "--- run $i of $count ---"
    "scenario_$name"
  done
done

echo
if [ "$failures" -eq 0 ]; then
  echo "All scenarios behaved as expected."
else
  echo "$failures scenario(s) did not behave as expected."
  exit 1
fi
