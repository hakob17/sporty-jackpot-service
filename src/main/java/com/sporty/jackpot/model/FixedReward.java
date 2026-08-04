package com.sporty.jackpot.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * The chance to win is always the same percentage, whatever the pool holds.
 */
@Schema(name = "FixedReward", description = "Always the same chance to win")
public record FixedReward(

        @Schema(description = "Chance to win as a percentage", example = "10.00")
        BigDecimal chancePercentage) implements RewardConfig {

    public FixedReward {
        Money.requirePercentage(chancePercentage, "chancePercentage");
    }
}
