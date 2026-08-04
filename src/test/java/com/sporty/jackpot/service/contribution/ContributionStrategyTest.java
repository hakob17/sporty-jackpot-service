package com.sporty.jackpot.service.contribution;

import com.sporty.jackpot.model.CappedContribution;
import com.sporty.jackpot.model.ContributionConfig;
import com.sporty.jackpot.model.FixedContribution;
import com.sporty.jackpot.model.FixedReward;
import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.VariableContribution;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ContributionStrategyTest {

    private static final UUID JACKPOT_ID = UUID.randomUUID();

    private final ContributionStrategies strategies = new ContributionStrategies(
            List.of(new FixedContributionStrategy(), new VariableContributionStrategy(),
                    new CappedContributionStrategy()));

    private static Jackpot jackpotWith(ContributionConfig contribution) {
        return new Jackpot(JACKPOT_ID, "Test", new BigDecimal("1000.00"), contribution,
                new FixedReward(new BigDecimal("10.00")));
    }

    private static Jackpot fixedJackpot(String percentage) {
        return jackpotWith(new FixedContribution(new BigDecimal(percentage)));
    }

    /** Starts at 10%, loses 1 point per 1000 of growth, never below 2%. */
    private static Jackpot variableJackpot() {
        return jackpotWith(new VariableContribution(new BigDecimal("10.00"), new BigDecimal("2.00"),
                new BigDecimal("1.00"), new BigDecimal("1000.00")));
    }

    private static Jackpot cappedJackpot(String percentage, String maxContribution) {
        return jackpotWith(new CappedContribution(new BigDecimal(percentage),
                new BigDecimal(maxContribution)));
    }

    @Test
    void fixedTakesTheSamePercentageWhateverThePoolHolds() {
        Jackpot jackpot = fixedJackpot("5.00");

        assertThat(strategies.contributionFor(jackpot, new BigDecimal("200.00"))).isEqualByComparingTo("10.00");

        jackpot.contribute(new BigDecimal("50000.00"));
        assertThat(strategies.percentageFor(jackpot)).isEqualByComparingTo("5.00");
        assertThat(strategies.contributionFor(jackpot, new BigDecimal("200.00"))).isEqualByComparingTo("10.00");
    }

    @Test
    void fixedRoundsTheContributionToCents() {
        assertThat(strategies.contributionFor(fixedJackpot("3.33"), new BigDecimal("10.05")))
                .isEqualByComparingTo("0.33");
    }

    @Test
    void variableStartsAtTheConfiguredPercentage() {
        Jackpot jackpot = variableJackpot();

        assertThat(strategies.percentageFor(jackpot)).isEqualByComparingTo("10.00");
        assertThat(strategies.contributionFor(jackpot, new BigDecimal("100.00"))).isEqualByComparingTo("10.00");
    }

    @Test
    void variableDropsOnePointPerCompletedPoolStep() {
        Jackpot jackpot = variableJackpot();
        jackpot.contribute(new BigDecimal("3500.00"));

        assertThat(strategies.percentageFor(jackpot)).isEqualByComparingTo("7.00");
        assertThat(strategies.contributionFor(jackpot, new BigDecimal("100.00"))).isEqualByComparingTo("7.00");
    }

    @Test
    void variableNeverDropsBelowItsFloor() {
        Jackpot jackpot = variableJackpot();
        jackpot.contribute(new BigDecimal("100000.00"));

        assertThat(strategies.percentageFor(jackpot)).isEqualByComparingTo("2.00");
    }

    @Test
    void theStrategyIsResolvedFromTheConfigurationRecordItself() {
        // No enum and no separate key: the config's own class is the discriminator.
        assertThat(strategies.percentageFor(fixedJackpot("5.00"))).isEqualByComparingTo("5.00");
        assertThat(strategies.percentageFor(variableJackpot())).isEqualByComparingTo("10.00");
    }

    @Test
    void aConfigurationWithNoStrategyFailsLoudly() {
        ContributionStrategies incomplete = new ContributionStrategies(List.of(new FixedContributionStrategy()));

        assertThatIllegalStateException()
                .isThrownBy(() -> incomplete.percentageFor(variableJackpot()))
                .withMessageContaining("VariableContribution");
    }

    @Test
    void twoStrategiesForTheSameConfigurationFailAtStartup() {
        assertThatIllegalStateException().isThrownBy(() -> new ContributionStrategies(
                List.of(new FixedContributionStrategy(), new FixedContributionStrategy())));
    }

    @Test
    void cappedTakesTheFullPercentageBelowTheCeiling() {
        assertThat(strategies.contributionFor(cappedJackpot("10.00", "50.00"), new BigDecimal("100.00")))
                .isEqualByComparingTo("10.00");
    }

    @Test
    void cappedContributesExactlyTheCeilingAtTheBoundary() {
        assertThat(strategies.contributionFor(cappedJackpot("10.00", "50.00"), new BigDecimal("500.00")))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void cappedClampsAStakeAboveTheCeiling() {
        assertThat(strategies.contributionFor(cappedJackpot("10.00", "50.00"), new BigDecimal("900.00")))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void cappedRoundsThroughMoney() {
        assertThat(strategies.contributionFor(cappedJackpot("10.00", "50.00"), new BigDecimal("33.33")))
                .isEqualByComparingTo("3.33");
    }

    @Test
    void cappedRoundsTheCapAsWell() {
        BigDecimal contribution = strategies.contributionFor(cappedJackpot("10.00", "0.05"), new BigDecimal("33.33"));
        assertThat(contribution).isEqualByComparingTo("0.05");
        assertThat(contribution.scale()).isEqualTo(2);
    }

    @Test
    void cappedPercentageIsTheNominalOne() {
        Jackpot jackpot = cappedJackpot("10.00", "50.00");
        assertThat(strategies.percentageFor(jackpot)).isEqualByComparingTo("10.00");

        jackpot.contribute(new BigDecimal("50000.00"));
        assertThat(strategies.percentageFor(jackpot)).isEqualByComparingTo("10.00");
    }

    @Test
    void cappedIgnoresPoolGrowth() {
        Jackpot jackpot = cappedJackpot("10.00", "50.00");
        jackpot.contribute(new BigDecimal("50000.00"));

        assertThat(strategies.contributionFor(jackpot, new BigDecimal("900.00"))).isEqualByComparingTo("50.00");
    }

    @Test
    void aCappedConfigurationIsResolvedFromItsOwnRecord() {
        ContributionStrategies incomplete = new ContributionStrategies(
                List.of(new FixedContributionStrategy()));

        assertThatIllegalStateException()
                .isThrownBy(() -> incomplete.contributionFor(cappedJackpot("10.00", "50.00"), new BigDecimal("100.00")))
                .withMessageContaining("CappedContribution");
    }

    @Test
    void theDefaultAmountHookIsUnchangedForPercentageRules() {
        assertThat(strategies.contributionFor(fixedJackpot("3.33"), new BigDecimal("10.05")))
                .isEqualByComparingTo("0.33");
        assertThat(strategies.contributionFor(variableJackpot(), new BigDecimal("100.00")))
                .isEqualByComparingTo("10.00");
    }
}
