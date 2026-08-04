package com.sporty.jackpot.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Creates the {@code jackpot-bets} topic on startup so the pipeline works on a fresh broker.
 */
@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Bean
    public NewTopic jackpotBetsTopic(@Value("${app.kafka.topic.jackpot-bets}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }
}
