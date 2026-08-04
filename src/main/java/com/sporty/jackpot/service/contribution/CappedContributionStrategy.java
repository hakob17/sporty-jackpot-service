package com.sporty.jackpot.service.contribution;

import com.sporty.jackpot.model.CappedContribution;
import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Contribution is a percentage of the bet amount, clamped to the configured ceiling.
 *
 * <p>The percentage is rounded to cents first and the ceiling applied to that, so the cap is a hard
 * limit on the money that reaches the pool rather than on an unrounded intermediate.
 */
@Component
public class CappedContributionStrategy implements ContributionStrategy<CappedContribution> {

    @Override
    public Class<CappedContribution> configType() {
        return CappedContribution.class;
    }

    /**
     * The nominal percentage. It is what the rule takes whenever the cap does not bind; the capped
     * amount itself comes from {@link #contributionFor}.
     */
    @Override
    public BigDecimal percentageFor(Jackpot jackpot, CappedContribution config) {
        return config.percentage();
    }

    @Override
    public BigDecimal contributionFor(Jackpot jackpot, CappedContribution config, BigDecimal stakeAmount) {
        return Money.scaled(Money.percentageOf(stakeAmount, config.percentage())
                .min(config.maxContribution()));
    }
}
