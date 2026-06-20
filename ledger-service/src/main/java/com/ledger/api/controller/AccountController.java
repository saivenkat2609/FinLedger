package com.ledger.api.controller;

import com.ledger.api.domain.Account;
import com.ledger.api.dto.AccountResponse;
import com.ledger.api.dto.AccountType;
import com.ledger.api.dto.CreateAccountRequest;
import com.ledger.api.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request);
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable UUID id) {
        Account account = accountService.getAccountById(id);
        return mapToResponse(account);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse getAccountBalance(@PathVariable UUID id) {
        BigDecimal balance = accountService.getAccountBalance(id);
        return new BalanceResponse(id, balance);
    }

    @GetMapping
    public List<AccountResponse> getAccountsByType(
            @RequestParam(required = false) AccountType type,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<Account> accounts;
        if (activeOnly) {
            accounts = accountService.getActiveAccounts();
        } else if (type != null) {
            accounts = accountService.getAccountsByType(type);
        } else {
            accounts = accountService.getAllAccounts();
        }
        return accounts.stream().map(this::mapToResponse).toList();
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

    public static class BalanceResponse {
        private UUID accountId;
        private BigDecimal balance;

        public BalanceResponse(UUID accountId, BigDecimal balance) {
            this.accountId = accountId;
            this.balance = balance;
        }

        public UUID getAccountId() {
            return accountId;
        }

        public BigDecimal getBalance() {
            return balance;
        }
    }
}
