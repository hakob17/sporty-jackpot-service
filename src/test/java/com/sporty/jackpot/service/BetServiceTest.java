package com.sporty.jackpot.service;

import com.sporty.jackpot.dto.Bet;
import com.sporty.jackpot.messaging.BetPublisher;
import com.sporty.jackpot.repository.JackpotRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BetServiceTest {

    private static final UUID JACKPOT = UUID.randomUUID();

    private final JackpotRepository jackpotRepository = mock(JackpotRepository.class);
    private final BetPublisher publisher = mock(BetPublisher.class);
    private final BetService service = new BetService(jackpotRepository, publisher);

    private static Bet betFor(UUID jackpotId) {
        return new Bet(UUID.randomUUID(), UUID.randomUUID(), jackpotId, new BigDecimal("200.00"));
    }

    @Test
    void handsTheBetToThePublisherWhenTheJackpotExists() {
        Bet bet = betFor(JACKPOT);
        when(jackpotRepository.existsById(JACKPOT)).thenReturn(true);

        service.publish(bet);

        verify(publisher).publish(bet);
    }

    @Test
    void refusesToPublishABetForAJackpotThatDoesNotExist() {
        UUID unknown = UUID.randomUUID();
        Bet bet = betFor(unknown);
        when(jackpotRepository.existsById(unknown)).thenReturn(false);

        assertThatThrownBy(() -> service.publish(bet))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknown.toString());

        // Nothing reaches the topic - the failure is at the edge, not in the consumer.
        verify(publisher, never()).publish(any());
    }
}
