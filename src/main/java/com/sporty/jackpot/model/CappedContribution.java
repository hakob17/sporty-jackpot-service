package com.sporty.jackpot.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * A percentage of the stake, but never more than {@code maxContribution} from a single bet.
 *
 * <p>Example: 10% capped at 50.00 — a stake of 100.00 contributes 10.00, a stake of 900.00
 * contributes 50.00 rather than 90.00. The cap keeps one very large stake from moving the pool
 * further in a single step than the pool's curve was designed for.
 */
@Schema(name = "CappedContribution",
        description = "A percentage of the bet amount, never more than a fixed ceiling per bet")
public record CappedContribution(

        @Schema(description = "Percentage of every bet that goes into the pool", example = "10.00")
        BigDecimal percentage,

        @Schema(description = "The most this rule contributes from a single bet", example = "50.00")
        BigDecimal maxContribution) implements ContributionConfig {

    public CappedContribution {
        Money.requirePercentage(percentage, "percentage");
        Money.requirePositive(percentage, "percentage");
        Money.requirePositive(maxContribution, "maxContribution");
    }
}
