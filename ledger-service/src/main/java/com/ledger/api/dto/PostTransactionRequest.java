package com.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Request payload for posting a transaction between two accounts")
public class PostTransactionRequest {
    @Schema(description = "Source account ID (account to debit)", example = "123e4567-e89b-12d3-a456-426614174000")
    @NotNull(message = "Source account ID is required")
    private UUID sourceAccountId;

    @Schema(description = "Destination account ID (account to credit)", example = "987f6543-e89b-12d3-a456-426614174999")
    @NotNull(message = "Destination account ID is required")
    private UUID destinationAccountId;

    @Schema(description = "Transfer amount (must be > 0)", example = "500.50")
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Amount exceeds maximum limit")
    private BigDecimal amount;

    @Schema(description = "ISO 4217 currency code", example = "INR")
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    private String currency;

    @Schema(description = "Optional transaction description", example = "Payment for invoice #12345", maxLength = 500)
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Schema(description = "Idempotency key (prevents duplicate processing on retry)", example = "req-20260621-abc123")
    @NotBlank(message = "Idempotency key is required")
    @Size(min = 1, max = 255, message = "Idempotency key must be 1-255 characters")
    private String idempotencyKey;

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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
