package com.sporty.jackpot.service;

import com.sporty.jackpot.dto.JackpotRewardResult;
import com.sporty.jackpot.model.FixedContribution;
import com.sporty.jackpot.model.FixedReward;
import com.sporty.jackpot.model.Jackpot;
import com.sporty.jackpot.model.JackpotContribution;
import com.sporty.jackpot.model.JackpotEvaluation;
import com.sporty.jackpot.model.JackpotReward;
import com.sporty.jackpot.model.VariableReward;
import com.sporty.jackpot.repository.JackpotEvaluationRepository;
import com.sporty.jackpot.repository.JackpotRepository;
import com.sporty.jackpot.repository.JackpotRewardRepository;
import com.sporty.jackpot.service.reward.FixedChanceRewardStrategy;
import com.sporty.jackpot.service.reward.RandomProvider;
import com.sporty.jackpot.service.reward.RewardChanceStrategies;
import com.sporty.jackpot.service.reward.VariableChanceRewardStrategy;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JackpotRewardServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T10:15:30Z");
    private static final UUID JACKPOT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final JackpotRepository jackpotRepository = mock(JackpotRepository.class);
    private final JackpotEvaluationRepository evaluationRepository = mock(JackpotEvaluationRepository.class);
    private final JackpotRewardRepository rewardRepository = mock(JackpotRewardRepository.class);
    private final RandomProvider randomProvider = mock(RandomProvider.class);

    private JackpotRewardService service;

    @BeforeEach
    void setUp() {
        RewardChanceStrategies strategies = new RewardChanceStrategies(
                List.of(new FixedChanceRewardStrategy(), new VariableChanceRewardStrategy()));
        service = new JackpotRewardService(jackpotRepository, evaluationRepository, rewardRepository,
                strategies, randomProvider, Clock.fixed(NOW, ZoneOffset.UTC));
        when(evaluationRepository.save(any(JackpotEvaluation.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(rewardRepository.save(any(JackpotReward.class))).thenAnswer(i -> i.getArgument(0));
    }

    /** A jackpot with a flat 10% win chance whose pool has grown to 1500. */
    private Jackpot jackpotWithGrownPool() {
        Jackpot jackpot = new Jackpot(JACKPOT, "Test Jackpot", new BigDecimal("1000.00"),
                new FixedContribution(new BigDecimal("5.00")),
                new FixedReward(new BigDecimal("10.00")));
        jackpot.contribute(new BigDecimal("500.00"));
        return jackpot;
    }

    private JackpotContribution contribution(UUID betId) {
        return new JackpotContribution(betId, USER, JACKPOT, new BigDecimal("200.00"),
                new BigDecimal("10.00"), new BigDecimal("1500.00"), NOW);
    }

    private JackpotContribution givenNotYetEvaluated(UUID betId) {
        when(evaluationRepository.findByBetId(betId)).thenReturn(Optional.empty());
        return contribution(betId);
    }

    @Test
    void aWinningBetTakesTheWholePoolAndResetsItToTheInitialAmount() {
        UUID betId = UUID.randomUUID();
        Jackpot jackpot = jackpotWithGrownPool();
        when(jackpotRepository.findByIdForUpdate(JACKPOT)).thenReturn(Optional.of(jackpot));
        when(randomProvider.drawPercentage()).thenReturn(9.99);

        JackpotEvaluation evaluation = service.evaluate(givenNotYetEvaluated(betId));

        assertThat(evaluation.isWon()).isTrue();
        assertThat(evaluation.getAwardedAmount()).isEqualByComparingTo("1500.00");
        assertThat(evaluation.getChancePercentage()).isEqualByComparingTo("10.00");
        assertThat(evaluation.getDrawnValue()).isEqualByComparingTo("9.99");
        assertThat(evaluation.getCreatedAt()).isEqualTo(NOW);
        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("1000.00");
        verify(jackpotRepository).save(jackpot);
        verify(rewardRepository).save(any(JackpotReward.class));
    }

    @Test
    void aLosingBetLeavesThePoolAloneAndWritesNoReward() {
        UUID betId = UUID.randomUUID();
        Jackpot jackpot = jackpotWithGrownPool();
        when(jackpotRepository.findByIdForUpdate(JACKPOT)).thenReturn(Optional.of(jackpot));
        when(randomProvider.drawPercentage()).thenReturn(10.0);

        JackpotEvaluation evaluation = service.evaluate(givenNotYetEvaluated(betId));

        assertThat(evaluation.isWon()).isFalse();
        assertThat(evaluation.getAwardedAmount()).isEqualByComparingTo("0");
        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("1500.00");
        verify(rewardRepository, never()).save(any());
        verify(jackpotRepository, never()).save(any());
    }

    @Test
    void aLossIsRecorded_soItCanNeverBeRedrawn() {
        // The whole point of persisting losses: without this row, asking again would draw again,
        // and a patient client would win eventually for free.
        UUID betId = UUID.randomUUID();
        Jackpot jackpot = jackpotWithGrownPool();
        when(jackpotRepository.findByIdForUpdate(JACKPOT)).thenReturn(Optional.of(jackpot));
        when(randomProvider.drawPercentage()).thenReturn(10.0);

        JackpotEvaluation lost = service.evaluate(givenNotYetEvaluated(betId));

        verify(evaluationRepository).save(lost);
        assertThat(lost.isWon()).isFalse();
    }

    @Test
    void aBetThatWasAlreadyEvaluatedIsNotDrawnAgain() {
        UUID betId = UUID.randomUUID();
        JackpotEvaluation stored = JackpotEvaluation.lost(betId, USER, JACKPOT,
                new BigDecimal("10.00"), new BigDecimal("42.7"), NOW);
        when(evaluationRepository.findByBetId(betId)).thenReturn(Optional.of(stored));

        JackpotEvaluation evaluation = service.evaluate(contribution(betId));

        assertThat(evaluation).isSameAs(stored);
        verifyNoInteractions(randomProvider);
        verify(evaluationRepository, never()).save(any());
        verify(jackpotRepository, never()).save(any());
        verify(rewardRepository, never()).save(any());
    }

    @Test
    void aPoolAtItsLimitAlwaysWins() {
        UUID betId = UUID.randomUUID();
        Jackpot jackpot = new Jackpot(JACKPOT, "Test Jackpot", new BigDecimal("1000.00"),
                new FixedContribution(new BigDecimal("5.00")),
                new VariableReward(new BigDecimal("1.00"), new BigDecimal("2.00"),
                        new BigDecimal("1000.00"), new BigDecimal("2000.00")));
        jackpot.contribute(new BigDecimal("1000.00"));
        when(jackpotRepository.findByIdForUpdate(JACKPOT)).thenReturn(Optional.of(jackpot));
        when(randomProvider.drawPercentage()).thenReturn(99.999);

        JackpotEvaluation evaluation = service.evaluate(givenNotYetEvaluated(betId));

        assertThat(evaluation.isWon()).isTrue();
        assertThat(evaluation.getChancePercentage()).isEqualByComparingTo("100");
        assertThat(evaluation.getAwardedAmount()).isEqualByComparingTo("2000.00");
        assertThat(jackpot.getCurrentPoolAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void theResultIsARead_ofWhatWasAlreadyDecided() {
        UUID betId = UUID.randomUUID();
        when(evaluationRepository.findByBetId(betId)).thenReturn(Optional.of(
                JackpotEvaluation.won(betId, USER, JACKPOT, new BigDecimal("100"),
                        new BigDecimal("42.7"), new BigDecimal("1500.00"), NOW)));

        JackpotRewardResult result = service.resultFor(betId);

        assertThat(result.won()).isTrue();
        assertThat(result.betId()).isEqualTo(betId);
        assertThat(result.userId()).isEqualTo(USER);
        assertThat(result.jackpotRewardAmount()).isEqualByComparingTo("1500.00");
        assertThat(result.chancePercentage()).isEqualByComparingTo("100");
        assertThat(result.drawnValue()).isEqualByComparingTo("42.7");
        assertThat(result.createdAt()).isEqualTo(NOW);
        // Reading an outcome never draws.
        verifyNoInteractions(randomProvider);
    }

    @Test
    void askingForABetThatWasNeverEvaluatedIsNotFound() {
        UUID betId = UUID.randomUUID();
        when(evaluationRepository.findByBetId(betId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resultFor(betId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(betId.toString());
    }
}
