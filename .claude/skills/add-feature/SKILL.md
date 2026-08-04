---
name: add-feature
description: Use when implementing a new feature, endpoint, or business flow in this service. Walks through the standard Spring layers in the right order.
---

# Adding a feature to the jackpot service

Work bottom-up: model/dto → repository → service → controller/messaging → config → tests →
end-to-end verify. Controllers and consumers stay thin; business logic lives in services, in
strategies and on the entities themselves.

## 1. Model & DTOs

- New persistent state → JPA entity in `model` (`@Entity`, `@Id`, protected no-arg constructor for
  JPA, public constructor for real use). Put behavior that belongs to the data on the entity (like
  `Jackpot.contribute`, `Jackpot.awardPool`) and let it enforce its own invariants by throwing.
- Per-jackpot settings → a record implementing the sealed `ContributionConfig` / `RewardConfig`
  interface, validating itself in its compact constructor and holding only the fields its own shape
  needs (see `VariableContribution`). Stored as a JSON column, so no schema change — and never a
  field that is null for some shapes.
- New request/response/message payloads → records in `dto`, with `jakarta.validation` annotations
  on anything a client sends. A DTO may double as the Kafka payload (see `Bet`).
- Amounts are `BigDecimal` and get rounded only through `Money`.

## 2. Repository

- Extend `JpaRepository` in `repository`; add derived query methods as needed
  (e.g. `findByBetId`, `findByJackpotIdOrderByCreatedAtDesc`).
- If the new flow read-modify-writes a row that another flow also touches, take it with
  `@Lock(LockModeType.PESSIMISTIC_WRITE)` like `JackpotRepository.findByIdForUpdate`.

## 3. Service

- Business logic goes in a `@Service` class in `service`, using constructor injection.
- Services depend on repositories, strategies and publisher interfaces — never on controllers or
  consumers.
- **A behaviour that varies per jackpot configuration is a strategy, not a branch**: add the enum
  constant and a `@Component` implementing `ContributionStrategy` / `RewardChanceStrategy`; the
  registry picks it up from the injected list.
- Anything triggered by messaging or retried by a client must be idempotent: check state before
  acting, skip already-processed work with an INFO log (see `JackpotContributionService.contribute`
  and the already-rewarded branch of `JackpotRewardService.evaluate`).
- Reject at the edge what the caller can act on — `BetService` refuses to publish a bet for a
  jackpot that does not exist, so a client gets a 404 rather than a 202 and silence. Keep the
  consumer's tolerant branch as well: the edge check races with deletion, and Kafka redelivers.
- `@Transactional` on methods that read-modify-write the database; timestamps come from the
  injected `Clock`.

## 4. Controller and/or messaging

- **REST**: controller in `controller`, `@Valid` on request bodies, delegate to the service, return
  DTOs/entities. 202 Accepted for fire-and-forget publishes. Map new failure modes in
  `ApiExceptionHandler` (`ResourceNotFoundException` → 404).
- **Kafka**: see `.claude/skills/add-messaging-flow/SKILL.md`. Keep publishers behind an interface
  so services stay broker-agnostic and unit-testable.

## 5. Configuration

- New topic names or tunables go in `application.yml` under `app.*`, injected with `@Value`.
- External endpoints use env-var placeholders with localhost defaults: `${SOME_HOST:localhost:1234}`.
  Add the env var to the `app` service in `docker-compose.yml`.
- Anything that must also work broker-less belongs behind `app.kafka.enabled` (see
  `LoopbackBetPublisher`).
- Seed data for local demos lives in `config/SampleDataLoader.java`.

## 6. Tests (required before done)

- Entity and config rules: pure JUnit tests (see `JackpotTest`, `JackpotConfigTest`).
- Strategies: pure JUnit tests covering the boundaries — floor, ceiling, pool limit (see
  `ContributionStrategyTest`, `RewardChanceStrategyTest`).
- Services: Mockito tests mocking the repository **interfaces** and `RandomProvider`, with the real
  strategy registries and a `Clock.fixed` (see `JackpotContributionServiceTest`). Always cover the
  idempotent replay.
- Controllers: `@WebMvcTest` with `@MockBean` services, including a validation failure.
- Run `mvn test` — the Docker image build skips tests, so this is the only gate.

## 7. Verify end-to-end

```bash
docker compose up -d --build app
docker compose logs -f app       # wait for "Started JackpotApplication"
```

Or without any infrastructure:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=no-kafka
```

Exercise the new feature with curl and confirm the full pipeline in the logs.
`GET /api/jackpots` shows the current pools. Update `README.md` (API table, examples) and
`CLAUDE.md` if the architecture map changed.
