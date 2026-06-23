package com.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Detected discrepancy between expected (DB) and cached balance")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyResponse implements Serializable {

    @Schema(description = "Account ID with discrepancy", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID accountId;

    @Schema(description = "Account name", example = "Checking Account")
    private String accountName;

    @Schema(description = "Balance computed from journal entries (source of truth)", example = "5000.00")
    private BigDecimal expectedBalance;

    @Schema(description = "Balance cached in Redis (may be stale)", example = "4950.00", nullable = true)
    private BigDecimal cachedBalance;

    @Schema(description = "Difference between expected and cached (should be 0)", example = "50.00", nullable = true)
    private BigDecimal differenceAmount;

    @Schema(description = "Type of discrepancy (BALANCE_MISMATCH, MISSING_CACHE)", example = "BALANCE_MISMATCH")
    private String discrepancyType;
}
