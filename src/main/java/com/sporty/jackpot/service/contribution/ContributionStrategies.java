package com.sporty.jackpot.service.contribution;

import com.sporty.jackpot.model.ContributionConfig;
import com.sporty.jackpot.model.Jackpot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the strategy for a jackpot's configuration and applies it.
 *
 * <p>Every {@link ContributionStrategy} bean on the classpath registers itself here, keyed by the
 * configuration record it declares — so a new configuration needs no change in this class. Two
 * strategies claiming the same record fail at startup rather than silently shadowing each other.
 */
@Component
public class ContributionStrategies {

    private final Map<Class<? extends ContributionConfig>, ContributionStrategy<?>> byConfig = new HashMap<>();

    public ContributionStrategies(List<ContributionStrategy<?>> strategies) {
        for (ContributionStrategy<?> strategy : strategies) {
            ContributionStrategy<?> previous = byConfig.put(strategy.configType(), strategy);
            if (previous != null) {
                throw new IllegalStateException("Two contribution strategies for " + strategy.configType());
            }
        }
    }

    /** The percentage of a stake this jackpot currently takes. */
    public BigDecimal percentageFor(Jackpot jackpot) {
        ContributionConfig config = jackpot.getContributionConfig();
        return strategyFor(config).percentageFor(jackpot, config);
    }

    /** The amount added to the pool for the given stake. */
    public BigDecimal contributionFor(Jackpot jackpot, BigDecimal stakeAmount) {
        ContributionConfig config = jackpot.getContributionConfig();
        return strategyFor(config).contributionFor(jackpot, config, stakeAmount);
    }

    /**
     * The cast is safe by construction: the map is keyed by the very class the strategy declared it
     * handles, so a strategy is only ever handed a config of its own type.
     */
    @SuppressWarnings("unchecked")
    private ContributionStrategy<ContributionConfig> strategyFor(ContributionConfig config) {
        ContributionStrategy<?> strategy = byConfig.get(config.getClass());
        if (strategy == null) {
            throw new IllegalStateException("No contribution strategy for " + config.getClass().getSimpleName());
        }
        return (ContributionStrategy<ContributionConfig>) strategy;
    }
}
