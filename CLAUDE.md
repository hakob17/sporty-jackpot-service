# CLAUDE.md

Jackpot Contribution and Reward Service — a Spring Boot 3 / Java 17 backend that receives bets,
contributes them to a matching jackpot pool and evaluates them for a jackpot reward.

## Commands

```bash
mvn test                          # run unit tests (no brokers, no database needed)
mvn -B verify                     # full build + tests (what CI runs)
mvn spring-boot:run -Dspring-boot.run.profiles=no-kafka   # run with no infrastructure at all
docker compose up -d --build      # build and run the whole stack (Kafka + app)
docker compose up -d --build app  # rebuild only the app after code changes
docker compose logs -f app        # follow the app logs (whole pipeline is logged)
# Swagger UI http://localhost:8080  |  Kafka UI http://localhost:8081
docker compose down               # stop everything
```

Quick end-to-end smoke test once the stack is up:

```bash
curl http://localhost:8080/api/jackpots                    # seeded jackpots
BET=$(uuidgen); USER=$(uuidgen); JP=33333333-3333-3333-3333-333333333333
curl -X POST http://localhost:8080/api/bets -H "Content-Type: application/json" \
  -d "{\"betId\":\"$BET\",\"userId\":\"$USER\",\"jackpotId\":\"$JP\",\"betAmount\":1000.00}"
curl http://localhost:8080/api/jackpots/$JP               # pool grew to its 200 limit
curl http://localhost:8080/api/bets/$BET/jackpot-reward   # the outcome, already decided
```

## Message flow

REST `POST /api/bets` (404 if the jackpot does not exist — nothing is published) → Kafka topic
`jackpot-bets` → `BetConsumer` → match the jackpot by `jackpotId` (H2 in-memory DB) → contribution
strategy decides the amount → pool grows and a `JackpotContribution` row is written.

…and then, in the same transaction and under the same lock, `JackpotRewardService` draws once: the
outcome is written to `jackpot_evaluations` (win **or** loss), and on a win the whole pool is paid
out as a `JackpotReward` and the pool resets to the jackpot's initial amount.

REST `GET /api/bets/{betId}/jackpot-reward` is a **read** of that stored outcome.

## Architecture (standard Spring layering)

Package root: `com.sporty.jackpot`

| Package | Contents |
|---------|----------|
| `controller` | REST controllers (`BetController`, `JackpotController`) + `ApiExceptionHandler` — thin, delegate to services |
| `service` | Business logic (`BetService`, `BetProcessingService` — contributes then evaluates, in one transaction —, `JackpotContributionService`, `JackpotRewardService`, `JackpotService` — the last one also administers jackpots) |
| `service.contribution` | `ContributionStrategy<C>` + `ContributionStrategies` registry, resolved by the config record's class |
| `service.reward` | `RewardChanceStrategy<C>` + registry, `RandomProvider` |
| `repository` | Spring Data JPA repositories |
| `model` | JPA entities plus the config records (`FixedContribution`, `VariableContribution`, `FixedReward`, `VariableReward`) behind sealed interfaces, stored as JSON columns; the entity owns its rules (`Jackpot.contribute`, `Jackpot.awardPool`, `Jackpot.poolGrowth`) |
| `dto` | Request/response/message payloads — records with `@Schema` docs; `Bet` is also the Kafka payload. `JackpotRequest` takes the `ContributionConfig`/`RewardConfig` records directly, so each shape validates exactly its own fields in its own compact constructor — there is no parallel request hierarchy to keep in sync |
| `messaging` | `BetPublisher` interface, `LoopbackBetPublisher` (no-broker fallback) |
| `messaging.kafka` | `KafkaBetPublisher`, `BetConsumer` |
| `config` | Spring wiring: Kafka topic bean, `Clock` bean, sample data seeding, OpenAPI info, `/` → Swagger redirect |

Layering rule: controller → service → repository/messaging. Controllers never touch repositories
or publishers directly; the consumer delegates to a service immediately.

## Working on this repo (skills, agents, commands)

This project is set up to be worked on with an agent. Reach for the piece that matches the task
instead of rediscovering the conventions each time:

| Skill (`.claude/skills/`) | Use it when |
|---|---|
| `add-jackpot-configuration` | The contribution or reward rules change — **the main extension point**. A new formula is a new strategy component, never an `if` in a service. |
| `add-feature` | Any other feature, endpoint or business flow — walks the Spring layers in order. |
| `add-messaging-flow` | A new Kafka topic, producer or consumer, including the broker-less fallback. |
| `verify-service` | Running the service and proving a change works: both run modes plus `./scripts/smoke-test.sh`. |
| `debug-pipeline` | Something misbehaves at runtime — maps each symptom to its usual cause. |

| Agent (`.claude/agents/`) | Use it when |
|---|---|
| `jackpot-reviewer` | Reviewing a change against the layering, strategy, idempotency, concurrency and money rules. Read-only. |
| `test-author` | Writing or extending tests in the house style. |

| Command (`.claude/commands/`) | Use it when |
|---|---|
| `/pr-ready` | Full delivery gate: tests → smoke test → review → docs, with an honest verdict. |
| `/demo` | Walking the four use cases live against a running instance. |

`.claude/settings.json` allowlists the `mvn`, `docker compose`, smoke-test and localhost-`curl`
commands this workflow needs, and adds a `PostToolUse` hook that reminds you to run `mvn test`
whenever `src/main/java` changes — the Docker image builds with `-DskipTests`, so that reminder is
protecting the only automated gate.

`scripts/smoke-test.sh` is the executable definition of "it works": it drives all four use cases and
asserts the amounts, not just the status codes. Extend it whenever a new flow becomes part of the
contract.

### On GitHub

Labelling an issue `claude` runs `.github/workflows/claude.yml` — a four-stage pipeline, each stage
on the model that fits it:

| Stage | Model | Does |
|---|---|---|
| `plan` | `claude-opus-5` | Reads the issue, `CLAUDE.md` and the skills; posts an implementation plan (including a "Tests to write" section) as an issue comment |
| `implement` | `claude-sonnet-5` | Implements the plan on `claude/issue-<N>`, updates the docs, opens the PR — deliberately writes **no** tests |
| `test` | `claude-haiku-4-5` | Writes the tests from that section in the house style, runs `mvn -B test`, pushes them onto the same branch |
| `review` | `claude-opus-5` | Reviews the PR against the project rules and fails the job unless it emits `CLAUDE-REVIEW-VERDICT: APPROVE` |

Splitting implement from test is the point: the agent that wrote the code doesn't get to decide its
own tests are sufficient, and a failing test is left failing rather than weakened. The review stage
runs even when the test stage fails (`if: always()`), so a PR with missing or red tests is blocked
with an explanation rather than silently skipped. `.github/workflows/claude-review.yml` applies the
same review to any human PR; make either `review` job a required status check to gate merges.

Both workflows need the `ANTHROPIC_API_KEY` repository secret; the optional `CLAUDE_GH_PAT` secret
makes bot-created PRs trigger CI and the standalone review (PRs opened with the default Actions
token do not trigger other workflows). Model IDs live in the workflow's `env:` block — change them
in one place.

## Adding a new feature

Follow the skill in `.claude/skills/add-feature/SKILL.md`. Summary: model/dto first, then
repository, then service (with the business logic), then controller and/or messaging, then config,
then tests for every layer you touched, then verify with `mvn test` and the smoke test above.

For a change to how a jackpot contributes or rewards, use
`.claude/skills/add-jackpot-configuration/SKILL.md` instead — that is the designed extension point.
For a new Kafka flow, see `.claude/skills/add-messaging-flow/SKILL.md`.

## Conventions

- Java 17, Spring Boot 3.3, constructor injection only (no `@Autowired` on fields, no Lombok).
- Records for DTOs, messages and configuration; JPA entities are mutable classes with a protected
  no-arg constructor for JPA and a public constructor for real use.
- **A new jackpot configuration is a new strategy, never an `if`**: write a record implementing
  `ContributionConfig` / `RewardConfig` that validates itself in its compact constructor, add it to
  the interface's `@JsonSubTypes`, and write a `@Component` implementing
  `ContributionStrategy<YourRecord>` returning `YourRecord.class` from `configType()`. The
  registries build themselves from the injected `List<...>` and resolve by that class — no enum, no
  type key, no schema change, and nothing existing modified.
- Config records are **sealed** and hold only the fields their own rule needs. Never add a field
  that is null for some shapes — that is the union type the JSON storage exists to avoid.
- Validation (`jakarta.validation`, e.g. `@NotBlank`) goes on request DTOs and is enforced with
  `@Valid` in controllers; entities and configs enforce their own invariants by throwing
  (`IllegalArgumentException`/`IllegalStateException`), and `ApiExceptionHandler` maps those to
  `ProblemDetail` responses.
- Every business id (bet, user, jackpot) is a `UUID` — in the API, on the Kafka payload and in the
  database. Never a free-form String.
- Money is `BigDecimal`, rounded in one place only — `Money.scaled` / `Money.percentageOf`
  (2 decimals, HALF_UP). Never use `double` for amounts.
- Timestamps come from the injected `Clock` (see `ClockConfig`), never `Instant.now()` directly,
  so tests can pin the time.
- Keep `BetPublisher` an interface so services are unit-testable and the broker-less
  `LoopbackBetPublisher` can replace the Kafka one via `app.kafka.enabled`.
- Topic names and other settings go in `application.yml` under `app.*` and are injected with
  `@Value`; externalize infra endpoints via env vars with localhost defaults.
- Log every message produced/consumed and every contribution/reward decision at INFO with enough
  context to trace the pipeline.
- Jackpot names are unique. Enforce it the same way as `betId`: check in the service so the error
  is good, and keep the database constraint as the backstop for the race.
- Contribution and evaluation must stay idempotent per `betId`: Kafka redelivers and clients retry,
  so re-processing is skipped, never an error.
- **The draw belongs to the pipeline, never to a request.** A bet is evaluated once, right after it
  contributes, and every evaluation is persisted — win *or* loss. Moving the draw back into an
  endpoint would let a client choose when to claim and retry a loss until it wins.
- **Validate at the edge, stay tolerant at the consumer.** Reject what the caller can act on before
  publishing (`BetService` 404s on an unknown jackpot, so the topic never carries an unprocessable
  message), and still keep the consumer's skip branches — the edge check races with deletion and
  Kafka redelivers. Never replace a consumer-side guard with an edge check.

## Testing

- Service unit tests mock the repository interfaces and `RandomProvider` (plain Mockito, no Spring
  context) and use the *real* strategy registries — see `JackpotContributionServiceTest`,
  `JackpotRewardServiceTest`.
- Strategies and entity rules get their own pure tests — see `ContributionStrategyTest`,
  `RewardChanceStrategyTest`, `JackpotTest`, `JackpotConfigTest`.
- Controllers use `@WebMvcTest` with `@MockBean` services — see `BetControllerTest`.
- Mock interfaces wherever possible; mocking concrete classes needs bytecode instrumentation that
  breaks on new JDKs (hence the pinned `byte-buddy.version` in `pom.xml`).
- No broker-dependent tests in `mvn test`; end-to-end verification is done against the Docker
  Compose stack or the `no-kafka` profile.

## Gotchas

- The Docker image builds with `-DskipTests`; always run `mvn test` yourself before considering a
  change done.
- `byte-buddy.version` is pinned above the version Boot 3.3.5 manages — without it Mockito cannot
  mock concrete classes (e.g. `@MockBean` services) on JDK 21+ toolchains.
- Kafka topic `jackpot-bets` is created by the `KafkaTopicConfig` bean on startup.
- `app.kafka.enabled=false` (profile `no-kafka`) swaps the Kafka publisher *and* disables the
  consumer and topic bean; the loopback publisher then calls the contribution service directly.
- A new config record must be added to **both** the sealed interface's `permits` clause and its
  `@JsonSubTypes`. Jackson does not read `permits`; an unregistered record serialises fine and then
  fails to deserialise, so it is written to the database and explodes on the next read.
  `ConfigSubtypeRegistrationTest` fails the build if the two lists disagree.
- H2 is in-memory: all jackpots reset to the seeded state on every app restart (seed data lives in
  `config/SampleDataLoader.java`). The seeded ids are fixed: `11111111-1111-1111-1111-111111111111` (fixed),
  `22222222-2222-2222-2222-222222222222` (variable), `33333333-3333-3333-3333-333333333333` (demo).
- Contributions are the only record of a bet — there is no `bets` table; a bet whose jackpot does
  not exist is not persisted and cannot be evaluated for a reward.
