package com.ledger.notification.consumer;

import com.ledger.notification.event.TransactionSettledEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Service
public class SettlementConsumer {

    private static final String TRANSACTION_SETTLED_TOPIC = "transaction-settled";
    private static final String CONSUMER_GROUP = "notification-service";

    /**
     * Listens to transaction settlement events from Kafka.
     * Retries 3 times with 1-second backoff if processing fails.
     * Failed messages go to Dead Letter Topic after retries exhausted.
     *
     * @param event The transaction settled event
     * @param correlationId Correlation ID from message header
     * @param ack Manual acknowledgment
     */
    @RetryableTopic(
            attempts = "4",  // 1 initial attempt + 3 retries
            backoff = @org.springframework.retry.annotation.Backoff(delay = 1000, multiplier = 1),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
            include = {Exception.class}
    )
    @KafkaListener(
            topics = TRANSACTION_SETTLED_TOPIC,
            groupId = CONSUMER_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTransactionSettlement(
            @Payload TransactionSettledEvent event,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey,
            @Header(value = "X-Correlation-ID", required = false) String correlationId,
            Acknowledgment ack) {

        // Set correlation ID in MDC for logging
        if (correlationId != null) {
            MDC.put("X-Correlation-ID", correlationId);
        }

        try {
            log.info("Received transaction settlement event. Transaction ID: {}, Amount: {}, From: {}, To: {}",
                    event.getTransactionId(),
                    event.getAmount(),
                    event.getFromAccountId(),
                    event.getToAccountId());

            // Simulate processing
            processTransactionSettlement(event);

            // Acknowledge the message
            if (ack != null) {
                ack.acknowledge();
            }

            log.info("Successfully processed transaction settlement. Transaction ID: {}, Correlation ID: {}",
                    event.getTransactionId(), correlationId);

        } catch (Exception e) {
            log.error("Error processing transaction settlement event. Transaction ID: {}, Correlation ID: {}, Error: {}",
                    event.getTransactionId(), correlationId, e.getMessage(), e);
            throw e;  // Throw to trigger retry
        } finally {
            MDC.clear();
        }
    }

    /**
     * Dead Letter Topic handler.
     * Called when a message fails after all retries are exhausted.
     * This is where we handle critical failures that need manual intervention.
     *
     * @param event The transaction settled event that failed
     * @param correlationId Correlation ID from message header
     */
    @DltHandler
    public void handleDlt(
            @Payload TransactionSettledEvent event,
            @Header(value = "X-Correlation-ID", required = false) String correlationId) {

        if (correlationId != null) {
            MDC.put("X-Correlation-ID", correlationId);
        }

        try {
            log.error("CRITICAL: Message sent to Dead Letter Topic. Transaction ID: {}, Amount: {}, Correlation ID: {}",
                    event.getTransactionId(),
                    event.getAmount(),
                    correlationId);

            // In production, send alert to ops team
            // Example: Send to Slack, PagerDuty, email, etc.
            alertOperations(event, correlationId);

            log.info("Alert sent to operations team for failed transaction settlement. Transaction ID: {}",
                    event.getTransactionId());

        } catch (Exception e) {
            log.error("Failed to handle Dead Letter Topic message. Transaction ID: {}, Error: {}",
                    event.getTransactionId(), e.getMessage(), e);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Simulates sending settlement notification (email, SMS, etc.)
     *
     * @param event The transaction settled event
     */
    private void processTransactionSettlement(TransactionSettledEvent event) {
        // Simulate sending email/notification
        log.debug("Simulating email notification for transaction settlement:");
        log.debug("  To: {} (account holder)", event.getFromAccountId());
        log.debug("  Subject: Transaction Settled");
        log.debug("  Body: Your transfer of {} {} to {} has been successfully settled",
                event.getAmount(), event.getCurrency(), event.getToAccountId());

        // In production, this would:
        // 1. Query database for user email
        // 2. Send email via SMTP
        // 3. Log the notification
        // 4. Record in notification_history table
    }

    /**
     * Sends alert to operations team when message processing fails repeatedly
     *
     * @param event The failed event
     * @param correlationId Correlation ID for tracing
     */
    private void alertOperations(TransactionSettledEvent event, String correlationId) {
        // In production, this would send to:
        // - Slack: #alerts channel
        // - PagerDuty: critical incident
        // - Email: ops-team@company.com
        // - Database: alerts table for tracking

        log.error("ALERT: Dead Letter Topic message. Details: txnId={}, amount={}, correlationId={}, topic=transaction-settled.DLT",
                event.getTransactionId(), event.getAmount(), correlationId);
    }
}
