package com.sporty.jackpot.service.contribution;

import com.sporty.jackpot.model.ContributionConfig;
import com.sporty.jackpot.model.Jackpot;

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
}
