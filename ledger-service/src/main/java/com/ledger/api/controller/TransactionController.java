package com.ledger.api.controller;

import com.ledger.api.domain.TransactionStatusType;
import com.ledger.api.dto.PostTransactionRequest;
import com.ledger.api.dto.TransactionResponse;
import com.ledger.api.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Transactions", description = "Transaction posting, search, and management endpoints")
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(
        summary = "Post a transfer between accounts",
        description = "Creates a double-entry transaction (DEBIT on source, CREDIT on destination). " +
                     "Atomically settles the transaction with idempotency protection. " +
                     "Publishes settlement event to Kafka asynchronously."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Transaction posted and settled successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body or business rule violation (e.g., insufficient balance)"),
        @ApiResponse(responseCode = "409", description = "Duplicate transaction (idempotency key already processed)"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse postTransfer(@Valid @RequestBody PostTransactionRequest request) {
        return transactionService.postTransfer(request);
    }

    // === GET endpoints: Literal paths BEFORE path variables ===

    @Operation(
        summary = "List all transactions",
        description = "Retrieve paginated list of all transactions. Returns transactions in descending order by creation time."
    )
    @ApiResponse(responseCode = "200", description = "List of transactions")
    @GetMapping
    public Page<TransactionResponse> listTransactions(
            @ParameterObject
            @PageableDefault(size = 20, page = 0, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return transactionService.listTransactions(pageable);
    }

    @Operation(
        summary = "Search and filter transactions",
        description = "Search transactions with optional filters for status, date range, amount range, and currency. " +
                     "All filters are optional. Results are paginated and sorted by creation date (newest first)."
    )
    @ApiResponse(responseCode = "200", description = "Filtered transactions")
    @GetMapping("/search")
    public Page<TransactionResponse> searchTransactions(
            @Parameter(description = "Transaction status filter")
            @RequestParam(required = false) TransactionStatusType status,

            @Parameter(description = "Start date (inclusive, YYYY-MM-DD)", example = "2026-01-01")
            @RequestParam(required = false) LocalDate fromDate,

            @Parameter(description = "End date (inclusive, YYYY-MM-DD)", example = "2026-12-31")
            @RequestParam(required = false) LocalDate toDate,

            @Parameter(description = "Minimum amount (inclusive)")
            @RequestParam(required = false) BigDecimal minAmount,

            @Parameter(description = "Maximum amount (inclusive)")
            @RequestParam(required = false) BigDecimal maxAmount,

            @Parameter(description = "Currency code (e.g., INR, USD)")
            @RequestParam(required = false) String currency,

            @ParameterObject
            @PageableDefault(size = 20, page = 0, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return transactionService.searchTransactions(status, fromDate, toDate, minAmount, maxAmount, currency, pageable);
    }

    @Operation(
        summary = "Filter transactions by status",
        description = "Get all transactions with a specific status (PENDING, SETTLED, REVERSED, FAILED)"
    )
    @ApiResponse(responseCode = "200", description = "Transactions with specified status")
    @GetMapping("/by-status")
    public Page<TransactionResponse> filterByStatus(
            @Parameter(description = "Transaction status", required = true)
            @RequestParam TransactionStatusType status,

            @ParameterObject
            @PageableDefault(size = 20, page = 0, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return transactionService.findByStatus(status, pageable);
    }

    @Operation(
        summary = "Filter transactions by date range",
        description = "Get all transactions within a specific date range"
    )
    @ApiResponse(responseCode = "200", description = "Transactions within date range")
    @GetMapping("/by-date-range")
    public Page<TransactionResponse> filterByDateRange(
            @Parameter(description = "Start date (inclusive, YYYY-MM-DD)", required = true)
            @RequestParam LocalDate fromDate,

            @Parameter(description = "End date (inclusive, YYYY-MM-DD)", required = true)
            @RequestParam LocalDate toDate,

            @ParameterObject
            @PageableDefault(size = 20, page = 0, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return transactionService.findByDateRange(fromDate, toDate, pageable);
    }

    @Operation(
        summary = "Filter transactions by amount range",
        description = "Get all transactions within a specific amount range"
    )
    @ApiResponse(responseCode = "200", description = "Transactions within amount range")
    @GetMapping("/by-amount-range")
    public Page<TransactionResponse> filterByAmountRange(
            @Parameter(description = "Minimum amount (inclusive)")
            @RequestParam BigDecimal minAmount,

            @Parameter(description = "Maximum amount (inclusive)")
            @RequestParam BigDecimal maxAmount,

            @ParameterObject
            @PageableDefault(size = 20, page = 0, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return transactionService.findByAmountRange(minAmount, maxAmount, pageable);
    }

    @Operation(
        summary = "Filter transactions by currency",
        description = "Get all transactions for a specific currency"
    )
    @ApiResponse(responseCode = "200", description = "Transactions for specified currency")
    @GetMapping("/by-currency")
    public Page<TransactionResponse> filterByCurrency(
            @Parameter(description = "Currency code (e.g., INR, USD)", required = true)
            @RequestParam String currency,

            @ParameterObject
            @PageableDefault(size = 20, page = 0, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return transactionService.findByCurrency(currency, pageable);
    }

    // === Path variables: AFTER all literal paths ===

    @Operation(
        summary = "Get a single transaction",
        description = "Retrieve details of a specific transaction by ID"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transaction found"),
        @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    @GetMapping("/{transactionId}")
    public TransactionResponse getTransaction(
            @Parameter(description = "Transaction ID", example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID transactionId) {
        return transactionService.getTransaction(transactionId);
    }
}
