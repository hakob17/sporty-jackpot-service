package com.sporty.jackpot.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * The chance to win starts low and grows by {@code increasePerStep} percentage points for every
 * completed {@code poolStep} of pool growth. Once the pool reaches {@code poolLimit} the chance is
 * 100%, so the pool can never grow beyond that limit without being awarded.
 *
 * <p>Example: start 1%, +1 point per 1000 of growth, limit 10000 — a pool that has grown by 4500
 * wins with a 5% chance, and a pool of 10000 always wins.
 */
@Schema(name = "VariableReward",
        description = "Starts low and grows as the pool grows; certain once the pool reaches its limit")
public record VariableReward(

        @Schema(description = "Starting chance as a percentage", example = "1.00")
        BigDecimal chancePercentage,

        @Schema(description = "Percentage points added per completed pool step", example = "2.00")
        BigDecimal increasePerStep,

        @Schema(description = "How much the pool must grow for one step", example = "1000.00")
        BigDecimal poolStep,

        @Schema(description = "Once the pool reaches this amount the chance is 100%", example = "20000.00")
        BigDecimal poolLimit) implements RewardConfig {

    public VariableReward {
        Money.requirePercentage(chancePercentage, "chancePercentage");
        Money.requireNotNegative(increasePerStep, "increasePerStep");
        Money.requirePositive(poolStep, "poolStep");
        Money.requirePositive(poolLimit, "poolLimit");
    }

    /**
     * The chance is 100% once the pool reaches {@code poolLimit}. A limit at or below the amount the
     * pool resets to would therefore be reached the moment the jackpot exists — and again after
     * every payout — so every bet would win the whole pool, forever.
     */
    @Override
    public void validateAgainstPool(BigDecimal initialPoolAmount) {
        if (poolLimit.compareTo(initialPoolAmount) <= 0) {
            throw new IllegalArgumentException(
                    "poolLimit must be greater than the jackpot's initialPoolAmount, otherwise the "
                            + "chance is 100% from the start and every bet wins the pool");
        }
    }
}
