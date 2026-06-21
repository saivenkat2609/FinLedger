package com.ledger.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    /**
     * Error handler for Kafka consumers.
     * - Retries 3 times with 1 second backoff between each retry
     * - If all retries fail, publishes to Dead Letter Topic (DLT)
     * - DLT topic name: {original-topic}.DLT
     */
    @Bean
    public DefaultErrorHandler defaultErrorHandler(DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        FixedBackOff backOff = new FixedBackOff(1000L, 3);  // 1 second delay, 3 retries

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(deadLetterPublishingRecoverer, backOff);

        log.info("Kafka error handler configured: 3 retries with 1s backoff, DLT enabled");
        return errorHandler;
    }

    /**
     * Dead Letter Publishing Recoverer.
     * - Publishes failed messages to DLT topic
     * - DLT topic name: {original-topic}.DLT
     * - Preserves original message headers
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer() {
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate -> kafkaTemplate,
                (record, ex) -> {
                    log.error("Message failed after retries, publishing to DLT. Topic: {}, Key: {}, Error: {}",
                            record.topic(), record.key(), ex.getMessage());
                    return record.topic() + ".DLT";
                }
        );
    }

    private org.springframework.kafka.core.KafkaTemplate kafkaTemplate() {
        return null;  // Injected by Spring
    }
}
