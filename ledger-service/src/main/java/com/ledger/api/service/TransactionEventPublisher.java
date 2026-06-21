package com.ledger.api.service;

import com.ledger.api.event.TransactionSettledEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class TransactionEventPublisher {

    private static final String TRANSACTION_SETTLED_TOPIC = "transaction-settled";

    private final KafkaTemplate<String, TransactionSettledEvent> kafkaTemplate;

    public TransactionEventPublisher(KafkaTemplate<String, TransactionSettledEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a transaction settlement event to Kafka.
     * This is called AFTER the transaction commits to the database.
     * If Kafka publishing fails, it does NOT roll back the database transaction.
     *
     * @param event The transaction settlement event to publish
     */
    public void publishTransactionSettlement(TransactionSettledEvent event) {
        String transactionId = event.getTransactionId().toString();
        String correlationId = MDC.get("X-Correlation-ID");

        try {
            // Use transaction ID as message key to ensure ordering
            Message<TransactionSettledEvent> message = MessageBuilder
                    .withPayload(event)
                    .setHeader(KafkaHeaders.TOPIC, TRANSACTION_SETTLED_TOPIC)
                    .setHeader(KafkaHeaders.MESSAGE_KEY, transactionId)
                    .setHeader("X-Correlation-ID", correlationId)
                    .build();

            kafkaTemplate.send(message);

            log.info("Published transaction settlement event. Transaction ID: {}, Topic: {}, Correlation ID: {}",
                    transactionId, TRANSACTION_SETTLED_TOPIC, correlationId);

        } catch (Exception e) {
            // Log but don't throw - don't roll back the database transaction
            // Kafka is for async notifications, not core business logic
            log.warn("Failed to publish transaction settlement event. Transaction ID: {}, Correlation ID: {}, Error: {}",
                    transactionId, correlationId, e.getMessage(), e);
        }
    }
}
