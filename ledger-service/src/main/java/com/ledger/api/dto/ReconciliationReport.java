package com.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Reconciliation report verifying that all debits equal all credits for a date range")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationReport implements Serializable {

    @Schema(description = "Whether books are balanced (debits == credits)", example = "true")
    private boolean balanced;

    @Schema(description = "Sum of all debit entries in the period", example = "50000.00")
    private BigDecimal totalDebits;

    @Schema(description = "Sum of all credit entries in the period", example = "50000.00")
    private BigDecimal totalCredits;

    @Schema(description = "Difference between debits and credits (should be 0 if balanced)", example = "0.00")
    private BigDecimal differenceAmount;

    @Schema(description = "Count of settled/reversed transactions in period", example = "1234")
    private Long transactionCount;

    @Schema(description = "Start date of reconciliation period", example = "2026-06-01")
    private LocalDate fromDate;

    @Schema(description = "End date of reconciliation period", example = "2026-06-30")
    private LocalDate toDate;

    @Schema(description = "ISO timestamp when report was generated", example = "2026-06-21T10:15:23Z")
    private String reportTimestamp;
}
