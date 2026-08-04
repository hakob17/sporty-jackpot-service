package com.sporty.jackpot.messaging.kafka;

import com.sporty.jackpot.dto.Bet;
import com.sporty.jackpot.messaging.BetPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes bets to the {@code jackpot-bets} topic, keyed by jackpot id so all bets of one jackpot
 * stay on the same partition and are contributed in order.
 */
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaBetPublisher implements BetPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaBetPublisher.class);

    private final KafkaTemplate<String, Bet> kafkaTemplate;
    private final String topic;

    public KafkaBetPublisher(KafkaTemplate<String, Bet> kafkaTemplate,
                             @Value("${app.kafka.topic.jackpot-bets}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(Bet bet) {
        log.info("Publishing bet {} to topic {}: {}", bet.betId(), topic, bet);
        kafkaTemplate.send(topic, bet.jackpotId().toString(), bet).whenComplete((result, failure) -> {
            if (failure != null) {
                log.error("Failed to publish bet {} to topic {}", bet.betId(), topic, failure);
            } else {
                log.info("Published bet {} to {}-{}@{}", bet.betId(), topic,
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }
}
