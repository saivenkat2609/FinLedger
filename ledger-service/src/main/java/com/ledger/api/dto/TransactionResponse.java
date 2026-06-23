package com.ledger.api.dto;

import com.ledger.api.domain.TransactionStatusType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Schema(description = "Response payload for a posted transaction")
public class TransactionResponse {
    @Schema(description = "Unique transaction ID", example = "456e7890-e89b-12d3-a456-426614174111")
    private UUID id;

    @Schema(description = "Source account ID (debited)", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID sourceAccountId;

    @Schema(description = "Destination account ID (credited)", example = "987f6543-e89b-12d3-a456-426614174999")
    private UUID destinationAccountId;

    @Schema(description = "Transfer amount", example = "500.50")
    private BigDecimal amount;

    @Schema(description = "Currency code", example = "INR")
    private String currency;

    @Schema(description = "Transaction description", example = "Payment for invoice #12345")
    private String description;

    @Schema(description = "Transaction status (PENDING, PROCESSING, SETTLED, REVERSED, FAILED)", example = "SETTLED")
    private TransactionStatusType status;

    @Schema(description = "Idempotency key used", example = "req-20260621-abc123")
    private String idempotencyKey;

    @Schema(description = "When transaction was created", example = "2026-06-21T10:15:23")
    private LocalDateTime createdAt;

    @Schema(description = "When transaction was settled", example = "2026-06-21T10:15:24")
    private LocalDateTime settledAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(UUID sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public void setDestinationAccountId(UUID destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TransactionStatusType getStatus() {
        return status;
    }

    public void setStatus(TransactionStatusType status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(LocalDateTime settledAt) {
        this.settledAt = settledAt;
    }
}
