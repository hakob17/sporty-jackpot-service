---
name: add-messaging-flow
description: Use when adding a new Kafka topic, producer, or consumer to this service — covers config, serialization, the broker-less fallback, and Docker wiring.
---

# Adding a Kafka flow

Message payloads are records from `dto` serialized as JSON. Publishers and consumers live in
`messaging.kafka`; consumers delegate to a service immediately.

## Topic

Add the name under `app.kafka.topic.*` in `application.yml` and declare a `NewTopic` bean in
`config/KafkaTopicConfig.java` — it is created on startup, so a fresh broker just works.

## Publisher

Define an interface in `messaging` (like `BetPublisher`) and a `@Component` implementation in
`messaging.kafka` wrapping `KafkaTemplate<String, YourRecord>`. Services depend on the interface,
never on `KafkaTemplate`.

- Key messages by their aggregate id (e.g. `jackpotId`) so related messages keep partition ordering.
- Log the publish, and log success/failure in `whenComplete`.
- Annotate with `@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true",
  matchIfMissing = true)` and provide a broker-less counterpart (`havingValue = "false"`) that logs
  the payload and calls the handling service directly — see `LoopbackBetPublisher`. The service must
  stay usable with `mvn spring-boot:run -Dspring-boot.run.profiles=no-kafka`.

## Consumer

A `@Component` with
`@KafkaListener(topics = "${app.kafka.topic.your-topic}", groupId = "${spring.kafka.consumer.group-id}")`,
carrying the same `@ConditionalOnProperty` as the publisher, logging the payload and delegating to a
service in one line.

If the payload type differs from `Bet`, you cannot rely on the global
`spring.json.value.default.type` — give the listener its own container factory with a
`JsonDeserializer` for the new type, and keep the package inside `spring.json.trusted.packages`
(`com.sporty.jackpot.dto`).

## Rules

- Consumers must be idempotent — messages can be redelivered. Check state before acting (see
  `JackpotContributionService.contribute`: an already-contributed bet is skipped, never an error).
- Log every produce and consume at INFO, including the payload, so the pipeline is traceable with
  `docker compose logs -f app`.
- Unit-test the service the consumer delegates to (mock repository interfaces); don't write
  broker-dependent tests in `mvn test`.

## Verify

```bash
docker compose up -d --build app
docker compose logs -f app   # watch: Publishing -> Published -> Consumed -> handled
```

Trigger the flow with curl and confirm each hop appears in the logs in order.
