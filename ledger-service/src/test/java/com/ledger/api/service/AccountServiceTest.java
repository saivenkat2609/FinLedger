package com.ledger.api.service;

import com.ledger.api.domain.Account;
import com.ledger.api.dto.AccountResponse;
import com.ledger.api.dto.AccountType;
import com.ledger.api.dto.CreateAccountRequest;
import com.ledger.api.exception.AccountAlreadyExistsException;
import com.ledger.api.exception.AccountNotFoundException;
import com.ledger.api.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private UUID accountId;
    private Account account;
    private CreateAccountRequest request;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();
        account = new Account();
        account.setId(accountId);
        account.setName("Checking Account");
        account.setType(AccountType.ASSET);
        account.setCurrency("USD");
        account.setActive(true);
        account.setCreatedAt(LocalDateTime.now());

        request = new CreateAccountRequest();
        request.setName("Checking Account");
        request.setType(AccountType.ASSET);
        request.setCurrency("USD");
    }

    @Test
    void testCreateAccountSuccess() {
        when(accountRepository.existsByNameAndCurrency(anyString(), anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(account);

        AccountResponse response = accountService.createAccount(request);

        assertNotNull(response);
        assertEquals("Checking Account", response.getName());
        assertEquals(AccountType.ASSET, response.getType());
        assertEquals("USD", response.getCurrency());
        assertTrue(response.isActive());
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void testCreateAccountDuplicate() {
        when(accountRepository.existsByNameAndCurrency(anyString(), anyString())).thenReturn(true);

        assertThrows(AccountAlreadyExistsException.class, () -> accountService.createAccount(request));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void testGetAccountByIdSuccess() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        Account result = accountService.getAccountById(accountId);

        assertNotNull(result);
        assertEquals(accountId, result.getId());
        assertEquals("Checking Account", result.getName());
    }

    @Test
    void testGetAccountByIdNotFound() {
        when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountService.getAccountById(accountId));
    }

    @Test
    void testGetAccountBalance() {
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.getAccountBalance(accountId)).thenReturn(new BigDecimal("1000.00"));

        BigDecimal balance = accountService.getAccountBalance(accountId);

        assertEquals(new BigDecimal("1000.00"), balance);
    }

    @Test
    void testGetAllAccounts() {
        Account account2 = new Account();
        account2.setId(UUID.randomUUID());
        account2.setName("Savings Account");
        account2.setType(AccountType.ASSET);
        account2.setCurrency("USD");
        account2.setActive(true);

        when(accountRepository.findAll()).thenReturn(List.of(account, account2));

        List<Account> accounts = accountService.getAllAccounts();

        assertEquals(2, accounts.size());
        verify(accountRepository, times(1)).findAll();
    }

    @Test
    void testGetAccountsByType() {
        when(accountRepository.findByType(AccountType.ASSET)).thenReturn(List.of(account));

        List<Account> accounts = accountService.getAccountsByType(AccountType.ASSET);

        assertEquals(1, accounts.size());
        assertEquals(AccountType.ASSET, accounts.get(0).getType());
    }

    @Test
    void testGetActiveAccounts() {
        when(accountRepository.findByIsActiveTrue()).thenReturn(List.of(account));

        List<Account> accounts = accountService.getActiveAccounts();

        assertEquals(1, accounts.size());
        assertTrue(accounts.get(0).isActive());
    }
}
