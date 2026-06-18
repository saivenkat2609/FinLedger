package com.ledger.api.service;

import com.ledger.api.domain.*;
import com.ledger.api.dto.PostTransactionRequest;
import com.ledger.api.dto.TransactionResponse;
import com.ledger.api.exception.*;
import com.ledger.api.repository.AccountRepository;
import com.ledger.api.repository.JournalEntryRepository;
import com.ledger.api.repository.TransactionRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountRepository accountRepository;
    private static final int MAX_RETRIES = 3;

    public TransactionService(TransactionRepository transactionRepository,
                            JournalEntryRepository journalEntryRepository,
                            AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public TransactionResponse postTransfer(PostTransactionRequest request) {
        return postTransferWithRetry(request, 0);
    }

    private TransactionResponse postTransferWithRetry(PostTransactionRequest request, int attemptCount) {
        try {
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
            savedTransaction.setSettledAt(LocalDateTime.now());
            transactionRepository.save(savedTransaction);

            // 11. Return TransactionResponse
            return mapToResponse(savedTransaction);

        } catch (OptimisticLockingFailureException e) {
            if (attemptCount < MAX_RETRIES) {
                return postTransferWithRetry(request, attemptCount + 1);
            }
            throw new TransactionFailedException("Failed to post transaction after " + MAX_RETRIES + " retries due to concurrent modifications");
        }
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
}
