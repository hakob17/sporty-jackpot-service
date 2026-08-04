package com.sporty.jackpot.model;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Always the same percentage of the bet amount, whatever the pool holds.
 */
@Schema(name = "FixedContribution", description = "Always the same percentage of the bet amount")
public record FixedContribution(

        @Schema(description = "Percentage of every bet that goes into the pool", example = "5.00")
        BigDecimal percentage) implements ContributionConfig {

    public FixedContribution {
        Money.requirePercentage(percentage, "percentage");
    }
}
