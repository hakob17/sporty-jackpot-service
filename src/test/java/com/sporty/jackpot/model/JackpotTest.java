package com.sporty.jackpot.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JackpotTest {

    private static final UUID JACKPOT_ID = UUID.randomUUID();

    private static Jackpot jackpotWithPool(String initialPool) {
        return new Jackpot(JACKPOT_ID, "Test Jackpot", new BigDecimal(initialPool),
                new FixedContribution(new BigDecimal("5.00")),
                new FixedReward(new BigDecimal("10.00")));
    }

    @Test
    void startsAtItsInitialPoolAmount() {
        Jackpot jackpot = jackpotWithPool("1000.00");

        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("1000.00");
        assertThat(jackpot.getInitialPoolAmount()).isEqualByComparingTo("1000.00");
        assertThat(jackpot.poolGrowth()).isEqualByComparingTo("0.00");
    }

    @Test
    void contributionGrowsThePool() {
        Jackpot jackpot = jackpotWithPool("1000.00");

        BigDecimal poolAfter = jackpot.contribute(new BigDecimal("25.50"));

        assertThat(poolAfter).isEqualByComparingTo("1025.50");
        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("1025.50");
        assertThat(jackpot.poolGrowth()).isEqualByComparingTo("25.50");
    }

    @Test
    void awardingHandsOutThePoolAndResetsItToTheInitialAmount() {
        Jackpot jackpot = jackpotWithPool("1000.00");
        jackpot.contribute(new BigDecimal("500.00"));

        BigDecimal awarded = jackpot.awardPool();

        assertThat(awarded).isEqualByComparingTo("1500.00");
        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("1000.00");
        assertThat(jackpot.poolGrowth()).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsNegativeContributions() {
        Jackpot jackpot = jackpotWithPool("1000.00");

        assertThatIllegalArgumentException().isThrownBy(() -> jackpot.contribute(new BigDecimal("-1.00")));
    }

    @Test
    void rejectsInvalidConstruction() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Jackpot(null, "Test Jackpot", BigDecimal.TEN,
                new FixedContribution(BigDecimal.ONE), new FixedReward(BigDecimal.ONE)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Jackpot(JACKPOT_ID, "Test Jackpot", new BigDecimal("-1"),
                new FixedContribution(BigDecimal.ONE), new FixedReward(BigDecimal.ONE)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Jackpot(JACKPOT_ID, "Test Jackpot", BigDecimal.TEN,
                null, new FixedReward(BigDecimal.ONE)));
    }
}
