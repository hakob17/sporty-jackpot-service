package com.sporty.jackpot.service.contribution;

import com.sporty.jackpot.model.FixedContribution;
import com.sporty.jackpot.model.Jackpot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Contribution is always the same percentage of the bet amount.
 */
@Component
public class FixedContributionStrategy implements ContributionStrategy<FixedContribution> {

    @Override
    public Class<FixedContribution> configType() {
        return FixedContribution.class;
    }

    @Override
    public BigDecimal percentageFor(Jackpot jackpot, FixedContribution config) {
        return config.percentage();
    }
}
