package com.sporty.jackpot.service.reward;

import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.RewardConfig;

import java.math.BigDecimal;

/**
 * Decides the chance that a contributing bet wins the jackpot pool, for one shape of configuration.
 *
 * <p>Resolved by the configuration record it declares — see {@link RewardChanceStrategies}.
 * Supporting a new configuration means adding a record and one more implementation of this
 * interface as a Spring component.
 *
 * @param <C> the configuration shape this strategy understands
 */
public interface RewardChanceStrategy<C extends RewardConfig> {

    /** The configuration record this strategy handles. */
    Class<C> configType();

    /** The current win chance for this jackpot, between 0 and 100. */
    BigDecimal chanceFor(Jackpot jackpot, C config);
}
