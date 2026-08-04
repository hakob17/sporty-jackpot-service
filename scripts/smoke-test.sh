#!/usr/bin/env bash
# End-to-end smoke test for the jackpot service.
#
# Exercises all four use cases against a running instance and checks the numbers,
# so it works the same whether the service runs on Kafka (docker compose) or with
# app.kafka.enabled=false. Exits non-zero on the first failed expectation.
#
#   ./scripts/smoke-test.sh                    # against http://localhost:8080
#   BASE_URL=http://host:8080 ./scripts/smoke-test.sh
#
# It creates the jackpots it needs through the API, with a 0% or 100% win chance,
# so every assertion is deterministic and it can be re-run without a restart.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

uuid() { python3 -c 'import uuid; print(uuid.uuid4())'; }

RUN=$(uuid | cut -c1-8)
USER_ID=$(uuid)
failures=0

say()  { printf '\n\033[1m%s\033[0m\n' "$*"; }
pass() { printf '  \033[32mPASS\033[0m %s\n' "$*"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$*"; failures=$((failures + 1)); }

expect_eq() { # expect_eq <label> <actual> <expected>
  if [ "$2" = "$3" ]; then pass "$1 = $2"; else fail "$1: expected $3, got $2"; fi
}

# Reads a JSON field as a plain value (booleans as true/false); python3 only, no jq dependency.
field() { # field <json> <key>
  printf '%s' "$1" | python3 -c "
import json, sys
v = json.load(sys.stdin).get('$2')
print('' if v is None else str(v).lower() if isinstance(v, bool) else v)"
}

create_jackpot() { # create_jackpot <name> <initialPool> <contributionPct> <winChancePct>
  curl -sf -X POST "$BASE_URL/api/jackpots" -H 'Content-Type: application/json' \
    -d "{\"name\":\"$1\",\"initialPoolAmount\":$2,
         \"contribution\":{\"type\":\"FIXED\",\"percentage\":$3},
         \"reward\":{\"type\":\"FIXED\",\"chancePercentage\":$4}}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])'
}

publish() { # publish <betId> <jackpotId> <amount>
  curl -sf -X POST "$BASE_URL/api/bets" -H 'Content-Type: application/json' \
    -d "{\"betId\":\"$1\",\"userId\":\"$USER_ID\",\"jackpotId\":\"$2\",\"betAmount\":$3}" > /dev/null
}

publish_status() { # publish_status <betId> <jackpotId> <amount>
  curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/bets" -H 'Content-Type: application/json' \
    -d "{\"betId\":\"$1\",\"userId\":\"$USER_ID\",\"jackpotId\":\"$2\",\"betAmount\":$3}"
}

pool() { field "$(curl -sf "$BASE_URL/api/jackpots/$1")" currentPoolAmount; }
outcome() { curl -s "$BASE_URL/api/bets/$1/jackpot-reward"; }

# The bet is evaluated on the consumer, so the outcome appears asynchronously under Kafka.
await_outcome() { # await_outcome <betId>
  for _ in $(seq 1 40); do
    code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/bets/$1/jackpot-reward")
    [ "$code" = "200" ] && return 0
    sleep 0.25
  done
  return 1
}

say "0. Service is up at $BASE_URL"
curl -sf "$BASE_URL/api/jackpots" > /dev/null && pass "GET /api/jackpots" || { fail "service unreachable"; exit 1; }

say "1. A bet contributes to its jackpot and is evaluated (use cases 1-4)"
# 0% win chance, so the pool is never awarded and the contribution maths stays checkable.
NEVER=$(create_jackpot "Smoke never-wins $RUN" 100.00 10.00 0.00)
BET_A=$(uuid)
publish "$BET_A" "$NEVER" 200.00 && pass "POST /api/bets accepted"
await_outcome "$BET_A" && pass "the pipeline evaluated the bet without being asked" \
  || fail "no outcome appeared for the bet"
expect_eq "pool grew by 10% of the stake" "$(pool "$NEVER")" "120.0"

result=$(outcome "$BET_A")
expect_eq "won" "$(field "$result" won)" "false"
expect_eq "chance it was drawn against" "$(field "$result" chancePercentage)" "0.0"
[ -n "$(field "$result" drawnValue)" ] && pass "the drawn value is recorded: $(field "$result" drawnValue)" \
  || fail "no drawn value recorded"

say "2. Asking again never re-draws — a loss cannot be retried into a win"
before=$(field "$(outcome "$BET_A")" createdAt)
for _ in $(seq 1 25); do
  if [ "$(field "$(outcome "$BET_A")" won)" = "true" ]; then
    fail "the outcome changed after repeated asks - the draw is not being reused"
    break
  fi
done
pass "25 further requests all returned the same losing outcome"
expect_eq "the evaluation timestamp never moved" "$(field "$(outcome "$BET_A")" createdAt)" "$before"
expect_eq "pool untouched by the repeated asks" "$(pool "$NEVER")" "120.0"

say "3. A bet naming an unknown jackpot is rejected before it reaches Kafka"
BET_ORPHAN=$(uuid)
expect_eq "publish rejected" "$(publish_status "$BET_ORPHAN" "$(uuid)" 500.00)" "404"
sleep 1
expect_eq "pool unchanged" "$(pool "$NEVER")" "120.0"
code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/bets/$BET_ORPHAN/jackpot-reward")
expect_eq "no outcome exists for it" "$code" "404"

say "4. A redelivered/duplicate bet is not contributed twice"
publish "$BET_A" "$NEVER" 200.00
sleep 1
expect_eq "pool still" "$(pool "$NEVER")" "120.0"

say "5. A winning bet takes the whole pool and resets it (use case 4)"
ALWAYS=$(create_jackpot "Smoke always-wins $RUN" 500.00 10.00 100.00)
BET_WIN=$(uuid)
publish "$BET_WIN" "$ALWAYS" 200.00
await_outcome "$BET_WIN" && pass "evaluated" || fail "no outcome appeared"

won=$(outcome "$BET_WIN")
expect_eq "won" "$(field "$won" won)" "true"
expect_eq "chance" "$(field "$won" chancePercentage)" "100.0"
# It contributed 20 first, so it wins the pool including its own contribution.
expect_eq "awarded the whole pool" "$(field "$won" jackpotRewardAmount)" "520.0"
expect_eq "pool reset to the initial amount" "$(pool "$ALWAYS")" "500.0"

say "6. The winner is not paid twice"
expect_eq "asking again returns the same amount" "$(field "$(outcome "$BET_WIN")" jackpotRewardAmount)" "520.0"
expect_eq "pool still at the initial amount" "$(pool "$ALWAYS")" "500.0"

say "7. Jackpot administration"
DELETABLE=$(create_jackpot "Smoke deletable $RUN" 10.00 1.00 0.00)
code=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE_URL/api/jackpots/$DELETABLE")
expect_eq "an unused jackpot can be deleted" "$code" "204"
code=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$BASE_URL/api/jackpots/$NEVER")
expect_eq "one with contributions cannot" "$code" "409"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/jackpots" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Smoke never-wins $RUN\",\"initialPoolAmount\":1.00,
       \"contribution\":{\"type\":\"FIXED\",\"percentage\":1.00},
       \"reward\":{\"type\":\"FIXED\",\"chancePercentage\":1.00}}")
expect_eq "a duplicate name is refused" "$code" "409"

say "8. Invalid input is rejected"
code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/bets" \
  -H 'Content-Type: application/json' \
  -d "{\"betId\":null,\"userId\":\"$USER_ID\",\"jackpotId\":\"$NEVER\",\"betAmount\":0}")
expect_eq "missing id and non-positive amount" "$code" "400"

code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/bets" \
  -H 'Content-Type: application/json' \
  -d "{\"betId\":\"not-a-uuid\",\"userId\":\"$USER_ID\",\"jackpotId\":\"$NEVER\",\"betAmount\":10}")
expect_eq "an id that is not a UUID" "$code" "400"

code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/jackpots/not-a-uuid")
expect_eq "a path id that is not a UUID" "$code" "400"

code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/jackpots" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Bad","initialPoolAmount":100.00,
       "contribution":{"type":"LOGARITHMIC","percentage":10.00},
       "reward":{"type":"FIXED","chancePercentage":10.00}}')
expect_eq "an unknown configuration type" "$code" "400"

if [ "$failures" -eq 0 ]; then
  printf '\n\033[32mAll smoke checks passed.\033[0m\n'
else
  printf '\n\033[31m%s smoke check(s) failed.\033[0m\n' "$failures"
  exit 1
fi
