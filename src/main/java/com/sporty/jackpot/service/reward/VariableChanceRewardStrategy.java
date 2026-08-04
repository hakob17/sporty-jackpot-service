package com.sporty.jackpot.service.reward;

import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.Money;
import com.sporty.jackpot.model.VariableReward;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The chance to win starts low and grows by {@code increasePerStep} percentage points for every
 * completed {@code poolStep} of pool growth. Once the pool reaches {@code poolLimit} the chance is
 * 100%, so the pool can never grow beyond that limit without being awarded.
 *
 * <p>Example: start 1%, +1% per 1000 of growth, limit 10000 — a pool that has grown by 4500 wins
 * with a 5% chance, and a pool of 10000 always wins.
 */
@Component
public class VariableChanceRewardStrategy implements RewardChanceStrategy<VariableReward> {

    @Override
    public Class<VariableReward> configType() {
        return VariableReward.class;
    }

    @Override
    public BigDecimal chanceFor(Jackpot jackpot, VariableReward config) {
        if (jackpot.getCurrentPoolAmount().compareTo(config.poolLimit()) >= 0) {
            return Money.HUNDRED;
        }
        BigDecimal completedSteps = jackpot.poolGrowth().divideToIntegralValue(config.poolStep());
        BigDecimal chance = config.chancePercentage().add(config.increasePerStep().multiply(completedSteps));
        return chance.min(Money.HUNDRED);
    }
}
