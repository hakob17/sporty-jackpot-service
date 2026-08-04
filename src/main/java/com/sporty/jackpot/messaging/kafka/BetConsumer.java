package com.sporty.jackpot.messaging.kafka;

import com.sporty.jackpot.dto.Bet;
import com.sporty.jackpot.service.BetProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Use case 2: listens to {@code jackpot-bets} and hands each bet to the processing pipeline,
 * which contributes it and then evaluates it for the reward.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class BetConsumer {

    private static final Logger log = LoggerFactory.getLogger(BetConsumer.class);

    private final BetProcessingService betProcessingService;

    public BetConsumer(BetProcessingService betProcessingService) {
        this.betProcessingService = betProcessingService;
    }

    @KafkaListener(topics = "${app.kafka.topic.jackpot-bets}", groupId = "${spring.kafka.consumer.group-id}")
    public void onBet(Bet bet) {
        log.info("Consumed bet from jackpot-bets: {}", bet);
        betProcessingService.process(bet);
    }
}
