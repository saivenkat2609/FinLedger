package com.ledger.api.controller;

import com.ledger.api.dto.DiscrepancyResponse;
import com.ledger.api.dto.ReconciliationReport;
import com.ledger.api.service.ReconciliationService;
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
@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/report")
    public ResponseEntity<ReconciliationReport> getReconciliationReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
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

    @GetMapping("/discrepancies")
    public ResponseEntity<List<DiscrepancyResponse>> getDiscrepancies(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
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
