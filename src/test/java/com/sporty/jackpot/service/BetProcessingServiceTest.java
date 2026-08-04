package com.sporty.jackpot.service;

import com.sporty.jackpot.dto.Bet;
import com.sporty.jackpot.model.JackpotContribution;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BetProcessingServiceTest {

    private final JackpotContributionService contributionService = mock(JackpotContributionService.class);
    private final JackpotRewardService rewardService = mock(JackpotRewardService.class);
    private final BetProcessingService service = new BetProcessingService(contributionService, rewardService);

    private static Bet bet(UUID jackpotId) {
        return new Bet(UUID.randomUUID(), UUID.randomUUID(), jackpotId, new BigDecimal("100.00"));
    }

    @Test
    void aContributingBetIsEvaluatedImmediately() {
        Bet bet = bet(UUID.randomUUID());
        JackpotContribution contribution = new JackpotContribution(bet.betId(), bet.userId(),
                bet.jackpotId(), bet.betAmount(), new BigDecimal("10.00"), new BigDecimal("110.00"),
                Instant.parse("2026-08-04T10:15:30Z"));
        when(contributionService.contribute(bet)).thenReturn(Optional.of(contribution));

        service.process(bet);

        // The draw is triggered by the pipeline, not by a client asking for it.
        verify(rewardService).evaluate(contribution);
    }

    @Test
    void aBetThatMatchedNoJackpotIsNotEvaluated() {
        Bet bet = bet(UUID.randomUUID());
        when(contributionService.contribute(bet)).thenReturn(Optional.empty());

        service.process(bet);

        // There is no pool for it to win, so there is nothing to draw for.
        verify(rewardService, never()).evaluate(any());
    }
}
