package com.sporty.jackpot.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Starts at {@code percentage} and loses {@code decreasePerStep} points for every completed
 * {@code poolStep} of pool growth, never below {@code minPercentage}.
 *
 * <p>Example: start 10%, -1 point per 1000 of growth, floor 2% — a pool that has grown by 3500
 * contributes 7% of the bet amount.
 */
@Schema(name = "VariableContribution",
        description = "Starts high and drops at a fixed rate as the pool grows, down to a floor")
public record VariableContribution(

        @Schema(description = "Starting percentage", example = "10.00")
        BigDecimal percentage,

        @Schema(description = "The percentage never drops below this", example = "2.00")
        BigDecimal minPercentage,

        @Schema(description = "Percentage points removed per completed pool step", example = "1.00")
        BigDecimal decreasePerStep,

        @Schema(description = "How much the pool must grow for one step", example = "1000.00")
        BigDecimal poolStep) implements ContributionConfig {

    public VariableContribution {
        Money.requirePercentage(percentage, "percentage");
        Money.requirePercentage(minPercentage, "minPercentage");
        if (minPercentage.compareTo(percentage) > 0) {
            throw new IllegalArgumentException("minPercentage must not exceed percentage");
        }
        Money.requireNotNegative(decreasePerStep, "decreasePerStep");
        Money.requirePositive(poolStep, "poolStep");
    }
}
