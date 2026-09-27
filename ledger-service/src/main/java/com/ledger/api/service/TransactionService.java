package com.ledger.api.service;

import com.ledger.api.domain.*;
import com.ledger.api.dto.PostTransactionRequest;
import com.ledger.api.dto.TransactionResponse;
import com.ledger.api.event.TransactionSettledEvent;
import com.ledger.api.exception.AccountNotFoundException;
import com.ledger.api.exception.CurrencyMismatchException;
import com.ledger.api.exception.DuplicateTransactionException;
import com.ledger.api.exception.InsufficientBalanceException;
import com.ledger.api.exception.InvalidAccountException;
import com.ledger.api.exception.InvalidTransactionException;
import com.ledger.api.repository.AccountRepository;
import com.ledger.api.repository.JournalEntryRepository;
import com.ledger.api.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Slf4j
@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private final TransactionEventPublisher eventPublisher;

    public TransactionService(TransactionRepository transactionRepository,
                            JournalEntryRepository journalEntryRepository,
                            AccountRepository accountRepository,
                            TransactionEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.accountRepository = accountRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "balances", key = "#request.sourceAccountId"),
            @CacheEvict(value = "balances", key = "#request.destinationAccountId")
    })
    public TransactionResponse postTransfer(PostTransactionRequest request) {
        // 1. Check for duplicate (idempotency)
        transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .ifPresent(t -> {
                    throw new DuplicateTransactionException(
                            "Transaction with idempotency key '" + request.getIdempotencyKey() + "' already exists");
                });

        // 2. Validate: source and destination accounts exist
        Account sourceAccount = accountRepository.findById(request.getSourceAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Source account not found"));

        Account destinationAccount = accountRepository.findById(request.getDestinationAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Destination account not found"));

        // 3. Validate: accounts are active
        if (!sourceAccount.isActive()) {
            throw new InvalidAccountException("Source account is inactive");
        }
        if (!destinationAccount.isActive()) {
            throw new InvalidAccountException("Destination account is inactive");
        }

        // 4. Validate: currency matches source account
        if (!sourceAccount.getCurrency().equals(request.getCurrency())) {
            throw new CurrencyMismatchException(
                    "Transaction currency '" + request.getCurrency() +
                    "' does not match source account currency '" + sourceAccount.getCurrency() + "'");
        }

        // 5. Validate: amount > 0
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException("Amount must be greater than 0");
        }

        // 6. Check sufficient balance (DEBIT check)
        BigDecimal sourceBalance = journalEntryRepository.getAccountBalance(sourceAccount.getId());
        if (sourceBalance.compareTo(request.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Current: " + sourceBalance + ", Required: " + request.getAmount());
        }

        // 7. Create Transaction record with status PENDING
        Transaction transaction = new Transaction();
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setDescription(request.getDescription());
        transaction.setStatus(TransactionStatusType.PENDING);
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        transaction.setCorrelationId(UUID.randomUUID().toString());

        Transaction savedTransaction = transactionRepository.save(transaction);

        // 8. Create JournalEntry DEBIT on source account
        JournalEntry debitEntry = new JournalEntry();
        debitEntry.setTransaction(savedTransaction);
        debitEntry.setAccount(sourceAccount);
        debitEntry.setEntryType(EntryType.DEBIT);
        debitEntry.setAmount(request.getAmount());
        journalEntryRepository.save(debitEntry);

        // 9. Create JournalEntry CREDIT on destination account
        JournalEntry creditEntry = new JournalEntry();
        creditEntry.setTransaction(savedTransaction);
        creditEntry.setAccount(destinationAccount);
        creditEntry.setEntryType(EntryType.CREDIT);
        creditEntry.setAmount(request.getAmount());
        journalEntryRepository.save(creditEntry);

        // 10. Update transaction status to SETTLED
        savedTransaction.setStatus(TransactionStatusType.SETTLED);
        LocalDateTime settledAt = LocalDateTime.now();
        savedTransaction.setSettledAt(settledAt);
        transactionRepository.save(savedTransaction);

        // 11. Publish settlement event to Kafka
        // This is called AFTER the database transaction commits
        // If Kafka publishing fails, it won't roll back the database transaction
        publishSettlementEvent(savedTransaction, sourceAccount, destinationAccount, settledAt);

        // 12. Log transaction posted event (structured logging)
        logTransactionPosted(savedTransaction, sourceAccount, destinationAccount);

        return mapToResponse(savedTransaction);
    }

    private void publishSettlementEvent(Transaction transaction, Account sourceAccount, Account destinationAccount, LocalDateTime settledAt) {
        try {
            String correlationId = MDC.get("X-Correlation-ID");

            TransactionSettledEvent event = TransactionSettledEvent.fromTransaction(
                    transaction.getId(),
                    sourceAccount.getId(),
                    destinationAccount.getId(),
                    transaction.getAmount(),
                    transaction.getCurrency(),
                    transaction.getDescription(),
                    transaction.getStatus().toString(),
                    settledAt.atZone(java.time.ZoneId.systemDefault()).toInstant(),
                    correlationId
            );

            eventPublisher.publishTransactionSettlement(event);
            log.info("Transaction settlement event published to Kafka. Transaction ID: {}, Topic: transaction-settled",
                    transaction.getId());
        } catch (Exception e) {
            log.warn("Failed to publish transaction settlement event for transaction: {}, but continuing...",
                    transaction.getId(), e);
            // Don't throw - this is async notification, not critical to the transaction
        }
    }

    private void logTransactionPosted(Transaction transaction, Account sourceAccount, Account destinationAccount) {
        log.info("Transaction posted successfully. Transaction ID: {}, Amount: {}, Currency: {}, Source Account: {}, " +
                "Destination Account: {}, Status: {}, Description: {}",
                transaction.getId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                sourceAccount.getId(),
                destinationAccount.getId(),
                transaction.getStatus(),
                transaction.getDescription());
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setAmount(transaction.getAmount());
        response.setCurrency(transaction.getCurrency());
        response.setDescription(transaction.getDescription());
        response.setStatus(transaction.getStatus());
        response.setIdempotencyKey(transaction.getIdempotencyKey());
        response.setCreatedAt(transaction.getCreatedAt());
        response.setSettledAt(transaction.getSettledAt());

        if (transaction.getEntries() != null && !transaction.getEntries().isEmpty()) {
            for (JournalEntry entry : transaction.getEntries()) {
                if (entry.getEntryType() == EntryType.DEBIT) {
                    response.setSourceAccountId(entry.getAccount().getId());
                } else if (entry.getEntryType() == EntryType.CREDIT) {
                    response.setDestinationAccountId(entry.getAccount().getId());
                }
            }
        }
        return response;
    }

    // === Feature 11: Search, Filtering & Pagination ===

    /**
     * List all transactions with pagination
     */
    public Page<TransactionResponse> listTransactions(Pageable pageable) {
        return transactionRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    /**
     * Search transactions with optional filters
     */
    public Page<TransactionResponse> searchTransactions(
            TransactionStatusType status,
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String currency,
            Pageable pageable) {

        LocalDateTime startDate = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime endDate = toDate != null ? toDate.atTime(LocalTime.MAX) : null;

        Page<Transaction> transactions = transactionRepository.findWithFilters(
                status,
                startDate,
                endDate,
                minAmount,
                maxAmount,
                currency,
                pageable);

        return transactions.map(this::mapToResponse);
    }

    /**
     * Filter transactions by status
     */
    public Page<TransactionResponse> findByStatus(TransactionStatusType status, Pageable pageable) {
        return transactionRepository.findByStatus(status, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Filter transactions by date range
     */
    public Page<TransactionResponse> findByDateRange(LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        LocalDateTime startDate = fromDate.atStartOfDay();
        LocalDateTime endDate = toDate.atTime(LocalTime.MAX);
        return transactionRepository.findByCreatedAtBetween(startDate, endDate, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Filter transactions by amount range
     */
    public Page<TransactionResponse> findByAmountRange(BigDecimal minAmount, BigDecimal maxAmount, Pageable pageable) {
        return transactionRepository.findByAmountRange(minAmount, maxAmount, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Filter transactions by currency
     */
    public Page<TransactionResponse> findByCurrency(String currency, Pageable pageable) {
        return transactionRepository.findByCurrency(currency, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Get a single transaction by ID
     */
    public TransactionResponse getTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        return mapToResponse(transaction);
    }
}
