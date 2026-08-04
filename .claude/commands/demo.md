---
description: Walk through the four jackpot use cases live against a running service
argument-hint: "[jackpot UUID, defaults to the demo jackpot]"
allowed-tools: Bash(mvn *), Bash(docker compose *), Bash(curl http://localhost:8080/*), Bash(./scripts/smoke-test.sh*), Read
---

Demonstrate the four use cases end to end against a running instance, narrating what each step
proves. Use jackpot $ARGUMENTS if given, otherwise the demo jackpot
(`33333333-3333-3333-3333-333333333333`) — its pool starts at 100.00, takes a flat 10% of every
stake and is guaranteed to pay out once it reaches 200.00, which makes the win deterministic
instead of a coin flip.

Start the service first if it is not up (`mvn spring-boot:run -Dspring-boot.run.profiles=no-kafka`
is enough unless the Kafka hop itself is what you want to show; the Docker stack shows the real
producer/consumer log lines; Kafka UI on :8081 shows the message on the topic). Every id is a UUID —
generate fresh ones with `uuidgen`, since contribution is idempotent per bet id and a reused id is
silently skipped.

Show, in order, with the actual request and response for each:

1. **Publish a bet** — `POST /api/bets` returns 202; the payload goes to the `jackpot-bets` topic.
2. **Consume and contribute** — `GET /api/jackpots/{id}` shows the pool grown by the configured
   percentage, and `GET /api/jackpots/{id}/contributions` shows the stored contribution with the
   stake, the contribution amount and the pool at that moment.
3. **Push the pool to its limit** with one larger bet, so the variable reward chance reaches 100%.
4. **Evaluate the reward** — `POST /api/bets/{betId}/jackpot-reward` returns the win and the amount,
   and the jackpot's pool is back at its initial value.

Then show one thing that is easy to get wrong and this service gets right: evaluate the same winning
bet again and point out that it returns the stored reward without paying twice or touching the pool.

If the Docker stack is running, finish with the log trace for one bet id
(`Publishing → Published → Consumed → contributed`) — it makes the pipeline visible in a way the
HTTP responses cannot.
