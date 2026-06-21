package com.ledger.api.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Slf4j
@Configuration
public class KafkaTopicConfig {

    // Main topics
    public static final String TRANSACTION_SETTLED_TOPIC = "transaction-settled";
    public static final String TRANSACTION_REVERSED_TOPIC = "transaction-reversed";

    @Bean
    public NewTopic transactionSettledTopic() {
        log.info("Creating Kafka topic: {}", TRANSACTION_SETTLED_TOPIC);
        return TopicBuilder.name(TRANSACTION_SETTLED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionReversedTopic() {
        log.info("Creating Kafka topic: {}", TRANSACTION_REVERSED_TOPIC);
        return TopicBuilder.name(TRANSACTION_REVERSED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Dead Letter Topics are auto-created by Spring Kafka when error handling is configured
    // Topics created automatically:
    // - transaction-settled.DLT (for failed messages)
    // - transaction-reversed.DLT (for failed messages)
}
