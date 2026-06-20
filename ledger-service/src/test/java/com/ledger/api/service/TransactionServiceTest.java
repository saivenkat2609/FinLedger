package com.ledger.api.service;

import com.ledger.api.domain.*;
import com.ledger.api.dto.PostTransactionRequest;
import com.ledger.api.dto.TransactionResponse;
import com.ledger.api.exception.*;
import com.ledger.api.repository.AccountRepository;
import com.ledger.api.repository.JournalEntryRepository;
import com.ledger.api.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private JournalEntryRepository journalEntryRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransactionService transactionService;

    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private Account sourceAccount;
    private Account destinationAccount;
    private PostTransactionRequest request;

    @BeforeEach
    void setUp() {
        sourceAccountId = UUID.randomUUID();
        destinationAccountId = UUID.randomUUID();

        sourceAccount = new Account();
        sourceAccount.setId(sourceAccountId);
        sourceAccount.setName("Checking Account");
        sourceAccount.setType(AccountType.ASSET);
        sourceAccount.setCurrency("USD");
        sourceAccount.setActive(true);

        destinationAccount = new Account();
        destinationAccount.setId(destinationAccountId);
        destinationAccount.setName("Savings Account");
        destinationAccount.setType(AccountType.ASSET);
        destinationAccount.setCurrency("USD");
        destinationAccount.setActive(true);

        request = new PostTransactionRequest();
        request.setSourceAccountId(sourceAccountId);
        request.setDestinationAccountId(destinationAccountId);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("USD");
        request.setDescription("Transfer");
        request.setIdempotencyKey("idempotency-key-123");
    }

    @Test
    void testPostTransferSuccess() {
        // Arrange
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(journalEntryRepository.getAccountBalance(sourceAccountId)).thenReturn(new BigDecimal("500.00"));

        Transaction savedTransaction = new Transaction();
        savedTransaction.setId(UUID.randomUUID());
        savedTransaction.setStatus(TransactionStatusType.SETTLED);
        savedTransaction.setCreatedAt(LocalDateTime.now());
        savedTransaction.setSettledAt(LocalDateTime.now());
        savedTransaction.setEntries(new ArrayList<>());

        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(journalEntryRepository.save(any(JournalEntry.class))).thenReturn(new JournalEntry());

        // Act
        TransactionResponse response = transactionService.postTransfer(request);

        // Assert
        assertNotNull(response);
        assertEquals(TransactionStatusType.SETTLED, response.getStatus());
        assertEquals(new BigDecimal("100.00"), response.getAmount());
        assertEquals("USD", response.getCurrency());
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(journalEntryRepository, times(2)).save(any(JournalEntry.class));
    }

    @Test
    void testPostTransferDuplicateIdempotencyKey() {
        // Arrange
        Transaction existingTransaction = new Transaction();
        existingTransaction.setId(UUID.randomUUID());
        when(transactionRepository.findByIdempotencyKey(request.getIdempotencyKey()))
                .thenReturn(Optional.of(existingTransaction));

        // Act & Assert
        assertThrows(DuplicateTransactionException.class, () -> transactionService.postTransfer(request));
        verify(accountRepository, never()).findById(any());
    }

    @Test
    void testPostTransferSourceAccountNotFound() {
        // Arrange
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> transactionService.postTransfer(request));
    }

    @Test
    void testPostTransferDestinationAccountNotFound() {
        // Arrange
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(AccountNotFoundException.class, () -> transactionService.postTransfer(request));
    }

    @Test
    void testPostTransferSourceAccountInactive() {
        // Arrange
        sourceAccount.setActive(false);
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));

        // Act & Assert
        assertThrows(InvalidAccountException.class, () -> transactionService.postTransfer(request));
    }

    @Test
    void testPostTransferDestinationAccountInactive() {
        // Arrange
        destinationAccount.setActive(false);
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));

        // Act & Assert
        assertThrows(InvalidAccountException.class, () -> transactionService.postTransfer(request));
    }

    @Test
    void testPostTransferCurrencyMismatch() {
        // Arrange
        request.setCurrency("EUR");
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));

        // Act & Assert
        assertThrows(CurrencyMismatchException.class, () -> transactionService.postTransfer(request));
    }

    @Test
    void testPostTransferZeroAmount() {
        // Arrange
        request.setAmount(BigDecimal.ZERO);
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));

        // Act & Assert
        assertThrows(InvalidTransactionException.class, () -> transactionService.postTransfer(request));
    }

    @Test
    void testPostTransferNegativeAmount() {
        // Arrange
        request.setAmount(new BigDecimal("-50.00"));
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));

        // Act & Assert
        assertThrows(InvalidTransactionException.class, () -> transactionService.postTransfer(request));
    }

    @Test
    void testPostTransferInsufficientBalance() {
        // Arrange
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(journalEntryRepository.getAccountBalance(sourceAccountId)).thenReturn(new BigDecimal("50.00"));

        // Act & Assert
        assertThrows(InsufficientBalanceException.class, () -> transactionService.postTransfer(request));
    }

    @Test
    void testPostTransferOptimisticLockRetry() {
        // Arrange
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(journalEntryRepository.getAccountBalance(sourceAccountId)).thenReturn(new BigDecimal("500.00"));

        Transaction savedTransaction = new Transaction();
        savedTransaction.setId(UUID.randomUUID());
        savedTransaction.setStatus(TransactionStatusType.SETTLED);
        savedTransaction.setCreatedAt(LocalDateTime.now());
        savedTransaction.setSettledAt(LocalDateTime.now());
        savedTransaction.setEntries(new ArrayList<>());

        // First call throws OptimisticLockException, second succeeds
        when(transactionRepository.save(any(Transaction.class)))
                .thenThrow(new OptimisticLockingFailureException("Lock failed"))
                .thenReturn(savedTransaction);
        when(journalEntryRepository.save(any(JournalEntry.class))).thenReturn(new JournalEntry());

        // Act & Assert - Should retry and succeed
        assertThrows(TransactionFailedException.class, () -> transactionService.postTransfer(request));
    }

    @Test
    void testPostTransferCreatesDebitAndCreditEntries() {
        // Arrange
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(journalEntryRepository.getAccountBalance(sourceAccountId)).thenReturn(new BigDecimal("500.00"));

        Transaction savedTransaction = new Transaction();
        savedTransaction.setId(UUID.randomUUID());
        savedTransaction.setStatus(TransactionStatusType.SETTLED);
        savedTransaction.setCreatedAt(LocalDateTime.now());
        savedTransaction.setSettledAt(LocalDateTime.now());
        savedTransaction.setEntries(new ArrayList<>());

        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(journalEntryRepository.save(any(JournalEntry.class))).thenReturn(new JournalEntry());

        ArgumentCaptor<JournalEntry> entryCaptor = ArgumentCaptor.forClass(JournalEntry.class);

        // Act
        transactionService.postTransfer(request);

        // Assert
        verify(journalEntryRepository, times(2)).save(entryCaptor.capture());
        var entries = entryCaptor.getAllValues();

        JournalEntry debitEntry = entries.get(0);
        JournalEntry creditEntry = entries.get(1);

        assertEquals(EntryType.DEBIT, debitEntry.getEntryType());
        assertEquals(sourceAccountId, debitEntry.getAccount().getId());
        assertEquals(new BigDecimal("100.00"), debitEntry.getAmount());

        assertEquals(EntryType.CREDIT, creditEntry.getEntryType());
        assertEquals(destinationAccountId, creditEntry.getAccount().getId());
        assertEquals(new BigDecimal("100.00"), creditEntry.getAmount());
    }

    @Test
    void testPostTransferSetsTransactionStatusToSettled() {
        // Arrange
        when(transactionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(journalEntryRepository.getAccountBalance(sourceAccountId)).thenReturn(new BigDecimal("500.00"));

        Transaction savedTransaction = new Transaction();
        savedTransaction.setId(UUID.randomUUID());
        savedTransaction.setStatus(TransactionStatusType.SETTLED);
        savedTransaction.setCreatedAt(LocalDateTime.now());
        savedTransaction.setSettledAt(LocalDateTime.now());
        savedTransaction.setEntries(new ArrayList<>());

        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(journalEntryRepository.save(any(JournalEntry.class))).thenReturn(new JournalEntry());

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);

        // Act
        transactionService.postTransfer(request);

        // Assert
        verify(transactionRepository, times(2)).save(transactionCaptor.capture());
        var transactions = transactionCaptor.getAllValues();

        Transaction finalTransaction = transactions.get(1);
        assertEquals(TransactionStatusType.SETTLED, finalTransaction.getStatus());
        assertNotNull(finalTransaction.getSettledAt());
    }
}
