package com.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for creating a new account")
public class CreateAccountRequest {
    @Schema(description = "Account name", example = "Checking Account", minLength = 1, maxLength = 255)
    @NotBlank(message = "Account name is required")
    @Size(min = 1, max = 255, message = "Account name must be between 1 and 255 characters")
    private String name;

    @Schema(description = "Account type (ASSET, LIABILITY, INCOME, EXPENSE)", example = "ASSET")
    @NotNull(message = "Account type is required")
    private AccountType type;

    @Schema(description = "ISO 4217 currency code", example = "INR", minLength = 3, maxLength = 3)
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters (e.g., USD, INR)")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency code must be 3 uppercase letters (e.g., USD)")
    private String currency;

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
}
