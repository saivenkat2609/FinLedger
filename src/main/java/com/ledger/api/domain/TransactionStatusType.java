package com.ledger.api.domain;

public enum TransactionStatusType {
    PENDING,      // Initial state
    PROCESSING,   // Being processed
    SETTLED,      // Successfully settled
    FAILED,       // Failed to process
    REVERSED      // Reversed/Cancelled
}
