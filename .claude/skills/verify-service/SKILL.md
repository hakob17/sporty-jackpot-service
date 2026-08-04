---
name: verify-service
description: Use to run the jackpot service and prove it actually works — starting it locally or in Docker, running the end-to-end smoke test, checking that a change behaves correctly at runtime, or demoing the four use cases. Use it whenever the request is "run it", "does this work", "check the pipeline", "show me the flow", or before reporting any behaviour change as done, since `mvn test` alone never touches Kafka, JPA or the HTTP layer.
---

# Running and verifying the service

`mvn test` covers the rules; it does not cover wiring — the Kafka round trip, the JPA mapping, the
JSON contract, the pessimistic lock. Those only fail at runtime, so a behaviour change is not done
until it has been exercised against a running instance.

## Pick the cheapest mode that proves the point

**No broker (seconds, no Docker).** The Kafka publisher is behind an interface; with
`app.kafka.enabled=false` a loopback publisher logs the payload and calls the contribution service
directly. Everything except the Kafka hop itself behaves identically.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=no-kafka
```

**Full stack (proves the Kafka hop).** Needed when you touched a producer, consumer, serializer,
topic or anything in `application.yml` under `spring.kafka`.

```bash
docker compose up -d --build
docker compose logs -f app        # wait for "Started JackpotApplication"
```

Rebuild only the app after code changes with `docker compose up -d --build app`; stop everything
with `docker compose down`.

H2 is in-memory, so every restart gives you the seeded jackpots back — a restart is the cheapest way
to reset state between runs, and the smoke test below relies on that.

## Run the smoke test

```bash
./scripts/smoke-test.sh                          # against http://localhost:8080
BASE_URL=http://host:8080 ./scripts/smoke-test.sh
```

It drives all four use cases and asserts the numbers rather than just the status codes: the pool
grows by the configured percentage, an unknown jackpot contributes nothing and cannot be evaluated
(404), a redelivered bet is not counted twice, a pool at its limit pays out and resets, a replayed
winner is not paid twice, and an invalid bet is rejected. It waits for the pool to change rather
than sleeping blindly, so it works the same on Kafka and in loopback mode. Exit code is non-zero if
any expectation failed.

Start from a freshly started app — the script expects the seeded demo-jackpot pool at its initial
100.00.

## Read the logs, not just the responses

Every hop logs at INFO with enough context to follow one bet end to end:

```bash
docker compose logs app | grep -E "Publishing|Published|Consumed|contributed|WON|did not win"
```

The contribution line prints the percentage, the strategy type, the stake and the resulting pool;
the reward line prints the draw against the chance. If a number looks wrong, that line usually tells
you which of the two it was — the formula or the state it was applied to.

## When something does not behave

Switch to the `debug-pipeline` skill; it maps the common symptoms (no contribution, unexpected 404,
a percentage that will not change) onto their usual causes.

## Before calling it done

- `mvn test` passes.
- The smoke test passes in whichever mode covers what you changed.
- New behaviour was exercised specifically, with curl, and confirmed in the logs — the smoke test
  only knows about the flows that existed when it was written.
