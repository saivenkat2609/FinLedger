package com.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyResponse implements Serializable {

    private UUID accountId;
    private String accountName;
    private BigDecimal expectedBalance;
    private BigDecimal cachedBalance;
    private BigDecimal differenceAmount;
    private String discrepancyType; // BALANCE_MISMATCH, MISSING_CACHE, etc.
}
