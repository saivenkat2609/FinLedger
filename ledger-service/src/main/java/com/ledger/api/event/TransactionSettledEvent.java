package com.ledger.api.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSettledEvent implements Serializable {

    @JsonProperty("transaction_id")
    private UUID transactionId;

    @JsonProperty("from_account_id")
    private UUID fromAccountId;

    @JsonProperty("to_account_id")
    private UUID toAccountId;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("description")
    private String description;

    @JsonProperty("status")
    private String status;

    @JsonProperty("settled_at")
    private Instant settledAt;

    @JsonProperty("correlation_id")
    private String correlationId;

    @JsonProperty("timestamp")
    private Instant timestamp;

    public static TransactionSettledEvent fromTransaction(
            UUID transactionId,
            UUID fromAccountId,
            UUID toAccountId,
            BigDecimal amount,
            String currency,
            String description,
            String status,
            Instant settledAt,
            String correlationId) {

        return TransactionSettledEvent.builder()
                .transactionId(transactionId)
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .currency(currency)
                .description(description)
                .status(status)
                .settledAt(settledAt)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build();
    }
}
