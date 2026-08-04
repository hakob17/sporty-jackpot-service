---
name: debug-pipeline
description: Use when the jackpot service misbehaves at runtime — a published bet never reaches the pool, the pool does not grow or grows by the wrong amount, a reward evaluation returns 404 or never wins, the app will not start, or Kafka logs look wrong. Use it for any "why didn't this work", "nothing happened", "the number is off" or "it says not found" report against a running instance, before changing any code.
---

# Debugging the bet pipeline

Almost every symptom here is one of a handful of causes, and the logs already say which. Read them
before changing code: every hop logs at INFO with the payload and the numbers, so the failing step
is usually visible without a debugger.

```bash
docker compose logs app | grep -E "Publishing|Published|Consumed|contributed|no jackpot|already|WON|did not win"
```

Running locally instead, the same lines go to the console. Follow one bet id through:
`Publishing … → Published … → Consumed … → contributed …`. Whichever arrow is missing tells you
which layer to look at.

## Symptom → cause

**"Publishing" but never "Consumed"** — the message left the app and did not come back. Check the
broker is reachable (`docker compose ps`; the app logs will be full of connection warnings), that
the consumer bean exists (it is `@ConditionalOnProperty(app.kafka.enabled)`, so a stray `no-kafka`
profile disables it while the REST layer keeps accepting bets), and that producer and consumer use
the same topic name from `app.kafka.topic.jackpot-bets`.

**"Consumed" but a deserialization error instead** — the consumer trusts
`spring.json.trusted.packages: com.sporty.jackpot.dto` and defaults every payload to `Bet` via
`spring.json.value.default.type`. A new payload type on the same listener needs its own container
factory; see the `add-messaging-flow` skill.

**"No jackpot X for bet Y - no contribution made"** — the safety net firing. `POST /api/bets` now
404s on an unknown jackpot, so this line means the message was already on the topic when the jackpot
went away (or it was published by something other than the API). Nothing is persisted for such a
bet, which is also why evaluating it later returns 404. If you expected the bet to contribute, check
the id against `GET /api/jackpots`.

**A publish returns 404 instead of 202** — by design: the jackpot in the body does not exist.
`GET /api/jackpots` lists the seeded ones with their names. Nothing was published.

**"Bet X already contributed … skipping"** — also by design: contribution is idempotent per `betId`.
Re-running a demo with the same bet ids after a restart works (H2 is wiped), but reusing an id
within one run is a no-op. Use fresh ids.

**Pool grows by the wrong amount** — the contribution log line prints the percentage, the strategy
type, the stake and the resulting pool. If the percentage is wrong, the strategy is wrong; test it
directly in `ContributionStrategyTest` rather than through the pipeline. Remember variable curves
key off `jackpot.poolGrowth()` (`current - initial`), so a jackpot that has just paid out is back at
its starting percentage — that is intended, not a bug.

**The outcome endpoint returns 404** — the bet has no evaluation. Either it never matched a jackpot, Either it never matched a
jackpot (see above), or it was never published, or the app was restarted since (H2 is in-memory, so
contributions do not survive a restart while the seeded jackpots come back).

**It never wins** — read the draw in the log line: `drew 12.57 against a 1.00% VARIABLE chance`. A
1% chance losing repeatedly is arithmetic, not a bug. For a deterministic win use the demo jackpot
(`33333333-3333-3333-3333-333333333333`), whose variable chance reaches 100% once its pool hits the
200.00 limit — push it there with one large bet. In tests, mock `RandomProvider` instead of hoping.

**The outcome never changes however often you ask** — by design. The draw happens once, on the
consumer, and `GET` reads the stored row. If you were expecting a fresh draw, you are thinking of
the old behaviour, which let a loss be retried into a win.

**App will not start** — read the first exception, not the last. Two strategies claiming the same
type fail at startup, on purpose; a missing `@Component` on a new strategy instead surfaces later as
`No contribution strategy for type X` at first use.

## Inspecting state directly

`GET /api/jackpots`, `/api/jackpots/{id}` and `/api/jackpots/{id}/contributions` cover most
questions. For anything else, the H2 console is at <http://localhost:8080/h2-console> (JDBC URL
`jdbc:h2:mem:jackpotdb`, user `sa`, empty password) with tables `jackpots`, `jackpot_contributions`
and `jackpot_rewards`.

## After you find it

Reproduce it as a failing unit test first — the strategies and services are all testable without a
broker, so a pipeline bug that cannot be expressed as a test usually means the wiring is at fault,
not the logic. Then fix, run `mvn test`, and re-verify with the `verify-service` skill.
