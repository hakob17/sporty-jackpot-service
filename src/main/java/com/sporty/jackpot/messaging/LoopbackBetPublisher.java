package com.sporty.jackpot.messaging;

import com.sporty.jackpot.dto.Bet;
import com.sporty.jackpot.service.BetProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stand-in for Kafka when {@code app.kafka.enabled=false}: logs the payload that would have been
 * published and hands the bet straight to the processing pipeline, so the whole use case can be
 * exercised without a broker.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "false")
public class LoopbackBetPublisher implements BetPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoopbackBetPublisher.class);

    private final BetProcessingService betProcessingService;

    public LoopbackBetPublisher(BetProcessingService betProcessingService) {
        this.betProcessingService = betProcessingService;
    }

    @Override
    public void publish(Bet bet) {
        log.info("Kafka disabled - bet payload that would go to jackpot-bets: {}", bet);
        betProcessingService.process(bet);
    }
}
