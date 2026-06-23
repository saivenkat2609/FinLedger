package com.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Response payload for account details")
public class AccountResponse {
    @Schema(description = "Unique account ID", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "Account name", example = "Checking Account")
    private String name;

    @Schema(description = "Account type (ASSET, LIABILITY, INCOME, EXPENSE)", example = "ASSET")
    private AccountType type;

    @Schema(description = "Currency code", example = "INR")
    private String currency;

    @Schema(description = "Whether account is active", example = "true")
    private boolean isActive;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }
}
