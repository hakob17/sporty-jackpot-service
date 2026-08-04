package com.sporty.jackpot.messaging;

import com.sporty.jackpot.dto.Bet;

/**
 * Publishes a bet for jackpot processing. Kept behind an interface so the service layer does not
 * depend on Kafka, and so the broker-less {@link LoopbackBetPublisher} can take its place.
 */
public interface BetPublisher {

    void publish(Bet bet);
}
