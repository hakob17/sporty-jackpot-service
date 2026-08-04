package com.sporty.jackpot.service.contribution;

import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.VariableContribution;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Contribution starts at the configured percentage and drops by {@code decreasePerStep} percentage
 * points for every completed {@code poolStep} of pool growth, never below {@code minPercentage}.
 *
 * <p>Example: start 10%, -1% per 1000 of growth, floor 2% — a pool that has grown by 3500
 * contributes 7% of the bet amount.
 */
@Component
public class VariableContributionStrategy implements ContributionStrategy<VariableContribution> {

    @Override
    public Class<VariableContribution> configType() {
        return VariableContribution.class;
    }

    @Override
    public BigDecimal percentageFor(Jackpot jackpot, VariableContribution config) {
        BigDecimal completedSteps = jackpot.poolGrowth().divideToIntegralValue(config.poolStep());
        BigDecimal percentage = config.percentage().subtract(config.decreasePerStep().multiply(completedSteps));
        return percentage.max(config.minPercentage());
    }
}
