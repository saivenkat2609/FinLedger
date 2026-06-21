package com.ledger.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationReport implements Serializable {

    private boolean balanced;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private BigDecimal differenceAmount;
    private Long transactionCount;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String reportTimestamp;
}
