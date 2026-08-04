package com.sporty.jackpot.service.contribution;

import com.sporty.jackpot.model.ContributionConfig;
import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.Money;

import java.math.BigDecimal;

/**
 * Turns a bet amount into a jackpot pool contribution, for one shape of configuration.
 *
 * <p>A strategy declares the config record it handles, and {@link ContributionStrategies} resolves
 * by that class — so the configuration record itself is the discriminator and there is no separate
 * type key to keep in step. Supporting a new configuration means adding a record and one more
 * implementation of this interface as a Spring component; nothing existing changes.
 *
 * @param <C> the configuration shape this strategy understands
 */
public interface ContributionStrategy<C extends ContributionConfig> {

    /** The configuration record this strategy handles. */
    Class<C> configType();

    /** The percentage of the bet amount this jackpot currently takes, between 0 and 100. */
    BigDecimal percentageFor(Jackpot jackpot, C config);

    /**
     * The amount this jackpot contributes for the given stake, rounded to the monetary scale.
     *
     * <p>The default is the percentage applied to the stake, which is the whole rule for every
     * percentage-shaped configuration. A rule whose result is not expressible as a percentage of the
     * stake — a per-bet ceiling, say — overrides this instead.
     */
    default BigDecimal contributionFor(Jackpot jackpot, C config, BigDecimal stakeAmount) {
        return Money.percentageOf(stakeAmount, percentageFor(jackpot, config));
    }
}
