package com.sporty.jackpot.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

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

    /**
     * A variable reward is 100% once the pool reaches its limit. Put that limit at or below the
     * amount the pool resets to and the jackpot pays out on every single bet, for ever — so the
     * combination is rejected even though each part is valid on its own.
     */
    @Test
    void rejectsARewardLimitThatTheStartingPoolAlreadyMeets() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Jackpot(JACKPOT_ID, "Test Jackpot", new BigDecimal("1000.00"),
                        new FixedContribution(new BigDecimal("10.00")),
                        new VariableReward(new BigDecimal("1.00"), new BigDecimal("1.00"),
                                new BigDecimal("500.00"), new BigDecimal("100.00"))))
                .withMessageContaining("poolLimit");

        // Equal is just as broken as below: the chance is 100% on the very first bet.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Jackpot(JACKPOT_ID, "Test Jackpot", new BigDecimal("1000.00"),
                        new FixedContribution(new BigDecimal("10.00")),
                        new VariableReward(new BigDecimal("1.00"), new BigDecimal("1.00"),
                                new BigDecimal("500.00"), new BigDecimal("1000.00"))));
    }

    @Test
    void acceptsARewardLimitAboveTheStartingPool() {
        Jackpot jackpot = new Jackpot(JACKPOT_ID, "Test Jackpot", new BigDecimal("1000.00"),
                new FixedContribution(new BigDecimal("10.00")),
                new VariableReward(new BigDecimal("1.00"), new BigDecimal("1.00"),
                        new BigDecimal("500.00"), new BigDecimal("1000.01")));

        assertThat(jackpot.getRewardConfig()).isInstanceOf(VariableReward.class);
    }

    @Test
    void reconfiguringIsCheckedAgainstThePoolToo() {
        Jackpot jackpot = jackpotWithPool("1000.00");
        jackpot.contribute(new BigDecimal("5000.00"));

        // The current pool is far past the limit, but what matters is the amount the pool resets to.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jackpot.reconfigure(new FixedContribution(new BigDecimal("10.00")),
                        new VariableReward(new BigDecimal("1.00"), new BigDecimal("1.00"),
                                new BigDecimal("500.00"), new BigDecimal("1000.00"))));

        // …and the original configuration is still in place.
        assertThat(jackpot.getRewardConfig()).isInstanceOf(FixedReward.class);
    }

    /** A fixed reward has no pool-dependent rule, so it opts out by not overriding the hook. */
    @Test
    void aFixedRewardIsUnaffectedByThePool() {
        RewardConfig config = new FixedReward(new BigDecimal("10.00"));

        assertThatNoException()
                .isThrownBy(() -> config.validateAgainstPool(new BigDecimal("999999.00")));
    }
}
