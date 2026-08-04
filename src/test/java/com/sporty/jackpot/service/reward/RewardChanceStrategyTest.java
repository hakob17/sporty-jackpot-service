package com.sporty.jackpot.service.reward;

import com.sporty.jackpot.model.FixedContribution;
import com.sporty.jackpot.model.FixedReward;
import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.RewardConfig;
import com.sporty.jackpot.model.VariableReward;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class RewardChanceStrategyTest {

    private static final UUID JACKPOT_ID = UUID.randomUUID();

    private final RewardChanceStrategies strategies = new RewardChanceStrategies(
            List.of(new FixedChanceRewardStrategy(), new VariableChanceRewardStrategy()));

    private static Jackpot jackpotWith(RewardConfig reward) {
        return new Jackpot(JACKPOT_ID, "Test", new BigDecimal("1000.00"),
                new FixedContribution(new BigDecimal("5.00")), reward);
    }

    private static Jackpot fixedJackpot() {
        return jackpotWith(new FixedReward(new BigDecimal("10.00")));
    }

    /** Starts at 1%, gains 2 points per 1000 of growth, guaranteed once the pool reaches 5000. */
    private static Jackpot variableJackpot() {
        return jackpotWith(new VariableReward(new BigDecimal("1.00"), new BigDecimal("2.00"),
                new BigDecimal("1000.00"), new BigDecimal("5000.00")));
    }

    @Test
    void fixedChanceIgnoresThePoolSize() {
        Jackpot jackpot = fixedJackpot();

        assertThat(strategies.chanceFor(jackpot)).isEqualByComparingTo("10.00");

        jackpot.contribute(new BigDecimal("9000.00"));
        assertThat(strategies.chanceFor(jackpot)).isEqualByComparingTo("10.00");
    }

    @Test
    void variableChanceStartsAtTheConfiguredChance() {
        assertThat(strategies.chanceFor(variableJackpot())).isEqualByComparingTo("1.00");
    }

    @Test
    void variableChanceGrowsWithThePool() {
        Jackpot jackpot = variableJackpot();
        jackpot.contribute(new BigDecimal("2500.00"));

        assertThat(strategies.chanceFor(jackpot)).isEqualByComparingTo("5.00");
    }

    @Test
    void variableChanceIsCertainOnceThePoolReachesTheLimit() {
        Jackpot jackpot = variableJackpot();
        jackpot.contribute(new BigDecimal("4000.00"));

        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("5000.00");
        assertThat(strategies.chanceFor(jackpot)).isEqualByComparingTo("100");
    }

    @Test
    void aConfigurationWithNoStrategyFailsLoudly() {
        RewardChanceStrategies incomplete = new RewardChanceStrategies(
                List.of(new FixedChanceRewardStrategy()));

        assertThatIllegalStateException()
                .isThrownBy(() -> incomplete.chanceFor(variableJackpot()))
                .withMessageContaining("VariableReward");
    }

    @Test
    void twoStrategiesForTheSameConfigurationFailAtStartup() {
        assertThatIllegalStateException().isThrownBy(() -> new RewardChanceStrategies(
                List.of(new FixedChanceRewardStrategy(), new FixedChanceRewardStrategy())));
    }
}
