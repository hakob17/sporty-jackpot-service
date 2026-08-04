package com.sporty.jackpot.service;

import com.sporty.jackpot.model.CappedContribution;
import com.sporty.jackpot.model.FixedContribution;
import com.sporty.jackpot.model.FixedReward;
import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.JackpotContribution;
import com.sporty.jackpot.model.VariableContribution;
import com.sporty.jackpot.dto.Bet;
import com.sporty.jackpot.repository.JackpotContributionRepository;
import com.sporty.jackpot.repository.JackpotRepository;
import com.sporty.jackpot.service.contribution.CappedContributionStrategy;
import com.sporty.jackpot.service.contribution.ContributionStrategies;
import com.sporty.jackpot.service.contribution.FixedContributionStrategy;
import com.sporty.jackpot.service.contribution.VariableContributionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JackpotContributionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:15:30Z");
    private static final UUID FIXED_JACKPOT = UUID.randomUUID();
    private static final UUID VARIABLE_JACKPOT = UUID.randomUUID();
    private static final UUID UNKNOWN_JACKPOT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final JackpotRepository jackpotRepository = mock(JackpotRepository.class);
    private final JackpotContributionRepository contributionRepository = mock(JackpotContributionRepository.class);

    private JackpotContributionService service;

    @BeforeEach
    void setUp() {
        ContributionStrategies strategies = new ContributionStrategies(
                List.of(new FixedContributionStrategy(), new VariableContributionStrategy(),
                        new CappedContributionStrategy()));
        service = new JackpotContributionService(jackpotRepository, contributionRepository, strategies,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(contributionRepository.save(any(JackpotContribution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Jackpot fixedJackpot() {
        return new Jackpot(FIXED_JACKPOT, "Fixed", new BigDecimal("1000.00"),
                new FixedContribution(new BigDecimal("5.00")),
                new FixedReward(new BigDecimal("10.00")));
    }

    private static Bet bet(UUID betId, UUID jackpotId, String amount) {
        return new Bet(betId, USER, jackpotId, new BigDecimal(amount));
    }

    private static Jackpot cappedJackpot(UUID jackpotId, String percentage, String maxContribution) {
        return new Jackpot(jackpotId, "Capped", new BigDecimal("1000.00"),
                new CappedContribution(new BigDecimal(percentage), new BigDecimal(maxContribution)),
                new FixedReward(new BigDecimal("10.00")));
    }

    @Test
    void contributesToTheMatchingJackpotAndGrowsThePool() {
        UUID betId = UUID.randomUUID();
        Jackpot jackpot = fixedJackpot();
        when(contributionRepository.findByBetId(betId)).thenReturn(Optional.empty());
        when(jackpotRepository.findByIdForUpdate(FIXED_JACKPOT)).thenReturn(Optional.of(jackpot));

        Optional<JackpotContribution> contribution = service.contribute(bet(betId, FIXED_JACKPOT, "200.00"));

        assertThat(contribution).isPresent();
        assertThat(contribution.get().getBetId()).isEqualTo(betId);
        assertThat(contribution.get().getUserId()).isEqualTo(USER);
        assertThat(contribution.get().getJackpotId()).isEqualTo(FIXED_JACKPOT);
        assertThat(contribution.get().getStakeAmount()).isEqualByComparingTo("200.00");
        assertThat(contribution.get().getContributionAmount()).isEqualByComparingTo("10.00");
        assertThat(contribution.get().getCurrentJackpotAmount()).isEqualByComparingTo("1010.00");
        assertThat(contribution.get().getCreatedAt()).isEqualTo(NOW);
        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("1010.00");
        verify(jackpotRepository).save(jackpot);
    }

    @Test
    void usesTheVariableConfigurationWhenTheJackpotIsConfiguredThatWay() {
        UUID betId = UUID.randomUUID();
        Jackpot jackpot = new Jackpot(VARIABLE_JACKPOT, "Variable", new BigDecimal("1000.00"),
                new VariableContribution(new BigDecimal("10.00"), new BigDecimal("2.00"),
                        new BigDecimal("1.00"), new BigDecimal("1000.00")),
                new FixedReward(new BigDecimal("10.00")));
        jackpot.contribute(new BigDecimal("2000.00")); // two completed steps -> 8%
        when(contributionRepository.findByBetId(betId)).thenReturn(Optional.empty());
        when(jackpotRepository.findByIdForUpdate(VARIABLE_JACKPOT)).thenReturn(Optional.of(jackpot));

        Optional<JackpotContribution> contribution = service.contribute(bet(betId, VARIABLE_JACKPOT, "100.00"));

        assertThat(contribution).isPresent();
        assertThat(contribution.get().getContributionAmount()).isEqualByComparingTo("8.00");
        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("3008.00");
    }

    @Test
    void makesNoContributionWhenNoJackpotMatches() {
        UUID betId = UUID.randomUUID();
        when(contributionRepository.findByBetId(betId)).thenReturn(Optional.empty());
        when(jackpotRepository.findByIdForUpdate(UNKNOWN_JACKPOT)).thenReturn(Optional.empty());

        Optional<JackpotContribution> contribution = service.contribute(bet(betId, UNKNOWN_JACKPOT, "100.00"));

        assertThat(contribution).isEmpty();
        verify(contributionRepository, never()).save(any());
        verify(jackpotRepository, never()).save(any());
    }

    @Test
    void redeliveredBetIsNotContributedTwice() {
        UUID betId = UUID.randomUUID();
        JackpotContribution existing = new JackpotContribution(betId, USER, FIXED_JACKPOT,
                new BigDecimal("200.00"), new BigDecimal("10.00"), new BigDecimal("1010.00"), NOW);
        when(contributionRepository.findByBetId(betId)).thenReturn(Optional.of(existing));

        Optional<JackpotContribution> contribution = service.contribute(bet(betId, FIXED_JACKPOT, "200.00"));

        assertThat(contribution).contains(existing);
        verify(jackpotRepository, never()).findByIdForUpdate(any());
        verify(contributionRepository, never()).save(any());
    }

    @Test
    void capsTheContributionForALargeStake() {
        UUID betId = UUID.randomUUID();
        UUID cappedJackpotId = UUID.randomUUID();
        Jackpot jackpot = cappedJackpot(cappedJackpotId, "10.00", "50.00");
        when(contributionRepository.findByBetId(betId)).thenReturn(Optional.empty());
        when(jackpotRepository.findByIdForUpdate(cappedJackpotId)).thenReturn(Optional.of(jackpot));

        Optional<JackpotContribution> contribution = service.contribute(bet(betId, cappedJackpotId, "900.00"));

        assertThat(contribution).isPresent();
        assertThat(contribution.get().getBetId()).isEqualTo(betId);
        assertThat(contribution.get().getUserId()).isEqualTo(USER);
        assertThat(contribution.get().getJackpotId()).isEqualTo(cappedJackpotId);
        assertThat(contribution.get().getStakeAmount()).isEqualByComparingTo("900.00");
        assertThat(contribution.get().getContributionAmount()).isEqualByComparingTo("50.00");
        assertThat(contribution.get().getCurrentJackpotAmount()).isEqualByComparingTo("1050.00");
        assertThat(contribution.get().getCreatedAt()).isEqualTo(NOW);
        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("1050.00");
        verify(jackpotRepository).save(jackpot);
    }

    @Test
    void aRedeliveredCappedBetIsNotContributedTwice() {
        UUID betId = UUID.randomUUID();
        UUID cappedJackpotId = UUID.randomUUID();
        JackpotContribution existing = new JackpotContribution(betId, USER, cappedJackpotId,
                new BigDecimal("900.00"), new BigDecimal("50.00"), new BigDecimal("1050.00"), NOW);
        when(contributionRepository.findByBetId(betId)).thenReturn(Optional.of(existing));

        Optional<JackpotContribution> contribution = service.contribute(bet(betId, cappedJackpotId, "900.00"));

        assertThat(contribution).contains(existing);
        verify(jackpotRepository, never()).findByIdForUpdate(any());
        verify(contributionRepository, never()).save(any());
    }
}
