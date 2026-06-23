package com.ledger.api.controller;

import com.ledger.api.dto.PostTransactionRequest;
import com.ledger.api.dto.TransactionResponse;
import com.ledger.api.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Transactions", description = "Transaction posting and management endpoints")
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
}
