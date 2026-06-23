package com.ledger.api.controller;

import com.ledger.api.dto.DiscrepancyResponse;
import com.ledger.api.dto.ReconciliationReport;
import com.ledger.api.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Tag(name = "Reconciliation", description = "Account reconciliation and balance verification endpoints")
@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Operation(
        summary = "Get reconciliation report",
        description = "Generates a reconciliation report verifying that total debits equal total credits for a date range. " +
                     "Returns an alert if books are not balanced."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reconciliation report generated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid date range"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/report")
    public ResponseEntity<ReconciliationReport> getReconciliationReport(
            @Parameter(description = "Start date (YYYY-MM-DD)", example = "2026-06-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date (YYYY-MM-DD)", example = "2026-06-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        log.info("Reconciliation report requested for period: {} to {}", from, to);

        ReconciliationReport report = reconciliationService.generateReconciliationReport(from, to);

        if (report.isBalanced()) {
            log.info("Books are balanced for period {} to {}", from, to);
        } else {
            log.warn("Books are NOT balanced for period {} to {}. Difference: {}",
                    from, to, report.getDifferenceAmount());
        }

        return ResponseEntity.ok(report);
    }

    @Operation(
        summary = "Find balance discrepancies",
        description = "Detects accounts where the cached balance diverges from the database-computed balance. " +
                     "Useful for identifying cache corruption or staleness."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Discrepancy check completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid date range"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/discrepancies")
    public ResponseEntity<List<DiscrepancyResponse>> getDiscrepancies(
            @Parameter(description = "Start date (YYYY-MM-DD)", example = "2026-06-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date (YYYY-MM-DD)", example = "2026-06-30")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        log.info("Discrepancy check requested for period: {} to {}", from, to);

        List<DiscrepancyResponse> discrepancies = reconciliationService.findDiscrepancies(from, to);

        if (discrepancies.isEmpty()) {
            log.info("No discrepancies found. All balances match cached values.");
        } else {
            log.warn("Found {} account discrepancies", discrepancies.size());
            discrepancies.forEach(d ->
                    log.warn("  - Account {}: expected={}, cached={}, difference={}",
                            d.getAccountId(), d.getExpectedBalance(), d.getCachedBalance(), d.getDifferenceAmount())
            );
        }

        return ResponseEntity.ok(discrepancies);
    }
}
