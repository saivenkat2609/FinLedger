package com.ledger.api.service;

import com.ledger.api.domain.*;
import com.ledger.api.dto.PostTransactionRequest;
import com.ledger.api.dto.TransactionResponse;
import com.ledger.api.exception.AccountNotFoundException;
import com.ledger.api.exception.CurrencyMismatchException;
import com.ledger.api.exception.DuplicateTransactionException;
import com.ledger.api.exception.InsufficientBalanceException;
import com.ledger.api.exception.InvalidAccountException;
import com.ledger.api.exception.InvalidTransactionException;
import com.ledger.api.exception.InvalidStateTransitionException;
import com.ledger.api.repository.AccountRepository;
import com.ledger.api.repository.JournalEntryRepository;
import com.ledger.api.repository.TransactionRepository;
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

    public TransactionService(TransactionRepository transactionRepository,
                            JournalEntryRepository journalEntryRepository,
                            AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
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

        // 10. Validate state transition PENDING → SETTLED
        TransactionStateMachine.assertValidTransition(savedTransaction.getStatus(), TransactionStatusType.SETTLED);

        // 11. Update transaction status to SETTLED
        savedTransaction.setStatus(TransactionStatusType.SETTLED);
        savedTransaction.setSettledAt(LocalDateTime.now());
        transactionRepository.save(savedTransaction);

        return mapToResponse(savedTransaction);
    }

    @Transactional
    public TransactionResponse reverseTransaction(UUID transactionId) {
        // 1. Find original transaction
        Transaction originalTransaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new InvalidTransactionException("Transaction not found"));

        // 2. Verify status is SETTLED
        if (!originalTransaction.getStatus().equals(TransactionStatusType.SETTLED)) {
            throw new InvalidStateTransitionException(
                "Only SETTLED transactions can be reversed. Current status: " + originalTransaction.getStatus());
        }

        // 3. Validate state transition SETTLED → REVERSED
        TransactionStateMachine.assertValidTransition(originalTransaction.getStatus(), TransactionStatusType.REVERSED);

        // 4. Create new reversal Transaction record
        Transaction reversalTransaction = new Transaction();
        reversalTransaction.setAmount(originalTransaction.getAmount());
        reversalTransaction.setCurrency(originalTransaction.getCurrency());
        reversalTransaction.setDescription("Reversal of transaction " + originalTransaction.getId());
        reversalTransaction.setStatus(TransactionStatusType.PENDING);
        reversalTransaction.setIdempotencyKey(UUID.randomUUID().toString());
        reversalTransaction.setCorrelationId(originalTransaction.getCorrelationId());

        Transaction savedReversalTx = transactionRepository.save(reversalTransaction);

        // 5. Get original journal entries
        var originalEntries = journalEntryRepository.findByTransactionId(originalTransaction.getId());

        // 6. Create mirror JournalEntries (flip DEBIT ↔ CREDIT)
        for (JournalEntry originalEntry : originalEntries) {
            JournalEntry reversalEntry = new JournalEntry();
            reversalEntry.setTransaction(savedReversalTx);
            reversalEntry.setAccount(originalEntry.getAccount());
            reversalEntry.setAmount(originalEntry.getAmount());
            // Flip the entry type
            reversalEntry.setEntryType(
                originalEntry.getEntryType() == EntryType.DEBIT ? EntryType.CREDIT : EntryType.DEBIT
            );
            journalEntryRepository.save(reversalEntry);
        }

        // 7. Mark reversal transaction as SETTLED
        savedReversalTx.setStatus(TransactionStatusType.SETTLED);
        savedReversalTx.setSettledAt(LocalDateTime.now());
        transactionRepository.save(savedReversalTx);

        // 8. Mark original transaction as REVERSED
        originalTransaction.setStatus(TransactionStatusType.REVERSED);
        transactionRepository.save(originalTransaction);

        return mapToResponse(savedReversalTx);
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
