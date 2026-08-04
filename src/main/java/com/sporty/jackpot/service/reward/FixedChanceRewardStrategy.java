package com.sporty.jackpot.service.reward;

import com.sporty.jackpot.model.FixedReward;
import com.sporty.jackpot.model.Jackpot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * The chance to win is always the same percentage, whatever the pool holds.
 */
@Component
public class FixedChanceRewardStrategy implements RewardChanceStrategy<FixedReward> {

    @Override
    public Class<FixedReward> configType() {
        return FixedReward.class;
    }

    @Override
    public BigDecimal chanceFor(Jackpot jackpot, FixedReward config) {
        return config.chancePercentage();
    }
}
