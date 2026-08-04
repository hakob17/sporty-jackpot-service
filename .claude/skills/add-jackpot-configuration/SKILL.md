---
name: add-jackpot-configuration
description: Use whenever the way a jackpot contributes or rewards changes — a new contribution formula (tiered, time-based, capped, per-user), a new reward chance rule, or a change to the existing FIXED/VARIABLE behaviour. Use it even when the request sounds like "add a branch for X" or "make the percentage depend on Y", because this codebase expresses every such variation as a config record plus a strategy rather than an if. Also use it when adding a jackpot with settings the current configs cannot express.
---

# Adding a jackpot configuration

This service has exactly one designed extension point, and it is the one the assignment calls out:
"we want to support just two options, but have the option to add more configurations in future."

A configuration is **two things**: a record holding the values that rule needs, and a strategy that
computes with them. The record is stored as a JSON document on the jackpot and *is* the
discriminator — the strategy declares which record class it handles, and the registry resolves on
that class. There is no enum, no type key, and no schema to migrate.

## 1. Decide which extension point you are on

| The change is about… | Config interface | Strategy interface | Registry |
|---|---|---|---|
| how much of a stake goes into the pool | `ContributionConfig` | `ContributionStrategy<C>` | `ContributionStrategies` |
| the chance a contributing bet wins the pool | `RewardConfig` | `RewardChanceStrategy<C>` | `RewardChanceStrategies` |

If the change is neither — it alters *when* evaluation happens, adds a message flow, or adds an
endpoint — use `add-feature` instead.

## 2. Write the config record

In `model`, a record implementing the sealed interface, holding **only** the fields this rule needs:

```java
@Schema(name = "TieredContribution", description = "One percentage below a threshold, another above")
public record TieredContribution(BigDecimal threshold, BigDecimal lowPercentage,
                                 BigDecimal highPercentage) implements ContributionConfig {

    public TieredContribution {
        Money.requirePositive(threshold, "threshold");
        Money.requirePercentage(lowPercentage, "lowPercentage");
        Money.requirePercentage(highPercentage, "highPercentage");
    }
}
```

Then add it to the sealed interface's `permits` clause **and** its `@JsonSubTypes`:

```java
@JsonSubTypes.Type(value = TieredContribution.class, name = "TIERED")
```

Both are required, and the second is easy to forget. Jackson does not derive subtypes from
`permits` — verified against Jackson 2.17. A permitted-but-unregistered record still *serialises*,
so it would be written to the database and then fail to load with `known type ids = []`: a poison
row, found on the next read rather than at the write that caused it.
`ConfigSubtypeRegistrationTest` compares the two lists and fails the build if they diverge, so this
mistake costs you a red test rather than a production incident.

That `name` is the persisted discriminator and the value clients send as `"type"`. It is the one
string in the design — pick it as carefully as a column name, because stored documents carry it,
and renaming one breaks every row already written. The same test pins the existing names.

Rules that keep this honest:

- **Never add a field that is null for some shapes.** That is the union type this design exists to
  remove. A field that only applies sometimes means you want another record.
- **Validate in the compact constructor**, throwing `IllegalArgumentException` — a config that
  cannot be constructed wrongly is worth more than a strategy that defends itself on every call.
  Note the trade this makes: the constructor throws on the first bad value, so an API client sees
  one field named at a time rather than all of them.
- Reuse `Money.requirePercentage` / `requirePositive` / `requireNotNegative` so the messages match.

## 3. Write the strategy

A `@Component` in `service/contribution` or `service/reward`, generic over your record:

```java
@Component
public class TieredContributionStrategy implements ContributionStrategy<TieredContribution> {

    @Override
    public Class<TieredContribution> configType() {
        return TieredContribution.class;
    }

    @Override
    public BigDecimal percentageFor(Jackpot jackpot, TieredContribution config) {
        return jackpot.poolGrowth().compareTo(config.threshold()) < 0
                ? config.lowPercentage()
                : config.highPercentage();
    }
}
```

What makes a strategy fit this codebase:

- **Derive from `jackpot.poolGrowth()`, not the raw pool**, when the rule is "as the jackpot grows".
  Growth is `current - initial`, so the curve resets together with the pool when the jackpot is
  awarded — which is what makes a paid-out jackpot start over instead of staying at its end state.
- **Stay a pure function of the jackpot and its config.** No repository lookups, no randomness: that
  is what lets the whole rule be tested in three lines and keeps the draw itself in
  `JackpotRewardService`, where `RandomProvider` can be mocked. A strategy *may* take collaborators
  in its constructor (a `Clock`, say) — that is the reason strategies are beans and not methods on
  the config record.
- **Clamp at the edges**: a contribution percentage has a floor, a chance has a 100% ceiling, and a
  reward config with a pool limit must return exactly 100 at or above that limit.
- Rounding is the registry's job — `ContributionStrategies.contributionFor` applies `Money`, so a
  strategy returns a percentage and never touches money directly.

Nothing else changes. Do not touch the registries: they map `configType() -> strategy` from the
injected list, throw at startup if two strategies claim the same record, and throw with a clear
message if a config has no strategy.

## 4. Tests (the gate)

Add cases to `ContributionStrategyTest` / `RewardChanceStrategyTest`. Build the registry with the
real strategies, construct a `Jackpot` with your config, and use `jackpot.contribute(...)` to move
the pool. Assert with `isEqualByComparingTo` so `10.00` and `10.0` compare equal. Cover the
boundaries, because that is where these rules break:

- the value before any growth (a freshly reset jackpot),
- one step either side of a threshold, and exactly on it,
- the floor / ceiling / pool-limit case,
- a stake that rounds (e.g. 3.33% of 10.05).

Add a validation test per invariant in `JackpotConfigTest`. If a service now behaves differently,
add the case to `JackpotContributionServiceTest` or `JackpotRewardServiceTest`.

Run `mvn test` — the Docker image builds with `-DskipTests`, so this is the only gate.

## 5. Make it reachable and visible

- Seed a jackpot using the new configuration in `config/SampleDataLoader.java`, with an inline
  comment describing the curve in words.
- Update the configuration and seeded-jackpot tables in `README.md`.
- Add a Postman request creating a jackpot with the new shape, so the API surface is covered.

## 6. Verify it end to end

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=no-kafka
./scripts/smoke-test.sh
```

Then create a jackpot with the new config over the API, publish a bet against it, and confirm the
logged percentage matches the formula — the contribution log line prints the percentage, the config
record's class, the stake and the resulting pool, which is usually enough to spot an off-by-one in
the step arithmetic without a debugger.
