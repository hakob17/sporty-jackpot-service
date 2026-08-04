package com.sporty.jackpot.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class JackpotConfigTest {

    @Test
    void aFixedContributionHoldsOnlyThePercentage() {
        FixedContribution config = new FixedContribution(new BigDecimal("5.00"));

        assertThat(config.percentage()).isEqualByComparingTo("5.00");
    }

    @Test
    void contributionPercentagesMustBeWithinRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FixedContribution(new BigDecimal("101")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FixedContribution(new BigDecimal("-1")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FixedContribution(null));
    }

    @Test
    void variableContributionFloorMustNotExceedTheStartPercentage() {
        assertThatIllegalArgumentException().isThrownBy(() -> new VariableContribution(
                new BigDecimal("2"), new BigDecimal("10"), BigDecimal.ONE, new BigDecimal("1000")))
                .withMessageContaining("minPercentage");
    }

    @Test
    void variableContributionNeedsAPositivePoolStep() {
        assertThatIllegalArgumentException().isThrownBy(() -> new VariableContribution(
                new BigDecimal("10"), new BigDecimal("2"), BigDecimal.ONE, BigDecimal.ZERO))
                .withMessageContaining("poolStep");
    }

    @Test
    void variableRewardNeedsAPositivePoolLimit() {
        assertThatIllegalArgumentException().isThrownBy(() -> new VariableReward(
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("1000"), BigDecimal.ZERO))
                .withMessageContaining("poolLimit");
    }

    @Test
    void rewardChanceMustBeWithinRange() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FixedReward(new BigDecimal("120")));
    }

    @Test
    void aConfigurationCarriesOnlyTheFieldsItsOwnShapeNeeds() {
        // The point of one record per shape: there is no field to leave null, so there is no way to
        // build a fixed contribution that carries a pool step.
        ContributionConfig fixed = new FixedContribution(new BigDecimal("5.00"));
        ContributionConfig variable = new VariableContribution(new BigDecimal("10.00"),
                new BigDecimal("2.00"), new BigDecimal("1.00"), new BigDecimal("1000.00"));

        assertThat(fixed).isInstanceOf(FixedContribution.class);
        assertThat(variable).isInstanceOf(VariableContribution.class);
        assertThat(fixed).isNotInstanceOf(VariableContribution.class);
    }

    @Test
    void cappedContributionRejectsANonPositivePercentage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CappedContribution(BigDecimal.ZERO, new BigDecimal("50.00")))
                .withMessageContaining("percentage must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CappedContribution(new BigDecimal("-1"), new BigDecimal("50.00")))
                .withMessageContaining("percentage");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CappedContribution(null, new BigDecimal("50.00")))
                .withMessageContaining("percentage");
    }

    @Test
    void cappedContributionRejectsAPercentageAbove100() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CappedContribution(new BigDecimal("101"), new BigDecimal("50.00")))
                .withMessageContaining("percentage");
    }

    @Test
    void cappedContributionRejectsANonPositiveCeiling() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CappedContribution(new BigDecimal("10.00"), BigDecimal.ZERO))
                .withMessageContaining("maxContribution");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CappedContribution(new BigDecimal("10.00"), new BigDecimal("-1")))
                .withMessageContaining("maxContribution");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CappedContribution(new BigDecimal("10.00"), null))
                .withMessageContaining("maxContribution");
    }

    @Test
    void cappedContributionAcceptsTheBoundaryValues() {
        assertThatNoException().isThrownBy(() ->
                new CappedContribution(new BigDecimal("100"), new BigDecimal("0.01")));
    }

    @Test
    void aCappedContributionHoldsOnlyItsOwnFields() {
        ContributionConfig capped = new CappedContribution(new BigDecimal("10.00"), new BigDecimal("50.00"));

        assertThat(capped).isInstanceOf(CappedContribution.class);
        assertThat(capped).isNotInstanceOf(VariableContribution.class);
        assertThat(capped).isNotInstanceOf(FixedContribution.class);
    }
}
