package com.ledger.api.service;

import com.ledger.api.domain.Account;
import com.ledger.api.dto.AccountResponse;
import com.ledger.api.dto.AccountType;
import com.ledger.api.dto.CreateAccountRequest;
import com.ledger.api.exception.AccountAlreadyExistsException;
import com.ledger.api.exception.AccountNotFoundException;
import com.ledger.api.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        if (accountRepository.existsByNameAndCurrency(request.getName(), request.getCurrency())) {
            throw new AccountAlreadyExistsException(
                    "Account with name '" + request.getName() + "' and currency '" + request.getCurrency() + "' already exists");
        }

        Account account = new Account();
        account.setName(request.getName());
        account.setType(request.getType());
        account.setCurrency(request.getCurrency());
        account.setActive(true);

        Account savedAccount = accountRepository.save(account);

        return mapToResponse(savedAccount);
    }

    public Account getAccountById(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found with id: " + accountId));
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public BigDecimal getAccountBalance(UUID accountId) {
        getAccountById(accountId);
        return accountRepository.getAccountBalance(accountId);
    }

    public List<Account> getAccountsByType(AccountType type) {
        return accountRepository.findByType(type);
    }

    public List<Account> getActiveAccounts() {
        return accountRepository.findByIsActiveTrue();
    }
    private AccountResponse mapToResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setName(account.getName());
        response.setType(account.getType());
        response.setCurrency(account.getCurrency());
        response.setActive(account.isActive());
        return response;
    }
}
