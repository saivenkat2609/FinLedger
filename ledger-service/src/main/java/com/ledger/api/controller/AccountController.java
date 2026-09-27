package com.ledger.api.controller;

import com.ledger.api.domain.Account;
import com.ledger.api.dto.AccountResponse;
import com.ledger.api.dto.AccountType;
import com.ledger.api.dto.CreateAccountRequest;
import com.ledger.api.service.AccountService;
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
import java.util.List;
import java.util.UUID;

@Tag(name = "Accounts", description = "Account management and search endpoints")
@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Create a new account", description = "Creates a new financial account with specified details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Account created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request body or account already exists"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request);
    }

    // === GET endpoints: Literal paths BEFORE path variables ===

    @Operation(
        summary = "List all accounts",
        description = "Retrieve paginated list of all accounts. Results are sorted by name."
    )
    @ApiResponse(responseCode = "200", description = "List of accounts")
    @GetMapping
    public Page<AccountResponse> listAccounts(
            @ParameterObject
            @PageableDefault(size = 20, page = 0, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return accountService.listAccounts(pageable);
    }

    @Operation(
        summary = "Search and filter accounts",
        description = "Search accounts with optional filters for type, currency, status, and name. " +
                     "All filters are optional. Results are paginated and sorted by name."
    )
    @ApiResponse(responseCode = "200", description = "Filtered accounts")
    @GetMapping("/search")
    public Page<AccountResponse> searchAccounts(
            @Parameter(description = "Filter by account type (ASSET, LIABILITY, INCOME, EXPENSE)")
            @RequestParam(required = false) AccountType type,

            @Parameter(description = "Filter by currency code (e.g., INR, USD)")
            @RequestParam(required = false) String currency,

            @Parameter(description = "Return only active accounts (default: false)")
            @RequestParam(required = false, defaultValue = "false") boolean onlyActive,

            @Parameter(description = "Search by account name (contains)")
            @RequestParam(required = false) String search,

            @ParameterObject
            @PageableDefault(size = 20, page = 0, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return accountService.searchAccounts(type, currency, onlyActive, search, pageable);
    }

    @Operation(
        summary = "Search accounts by name",
        description = "Search accounts where name contains the search term (case-insensitive)"
    )
    @ApiResponse(responseCode = "200", description = "Accounts matching search term")
    @GetMapping("/search/by-name")
    public Page<AccountResponse> searchByName(
            @Parameter(description = "Search term to match against account names", required = true)
            @RequestParam String search,

            @ParameterObject
            @PageableDefault(size = 20, page = 0, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return accountService.searchByName(search, pageable);
    }

    @Operation(
        summary = "Filter accounts by type",
        description = "Get all accounts of a specific type (ASSET, LIABILITY, INCOME, EXPENSE)"
    )
    @ApiResponse(responseCode = "200", description = "Accounts of specified type")
    @GetMapping("/by-type")
    public Page<AccountResponse> filterByType(
            @Parameter(description = "Account type", required = true)
            @RequestParam AccountType type,

            @ParameterObject
            @PageableDefault(size = 20, page = 0, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return accountService.findByType(type, pageable);
    }

    @Operation(
        summary = "Filter accounts by currency",
        description = "Get all accounts for a specific currency"
    )
    @ApiResponse(responseCode = "200", description = "Accounts for specified currency")
    @GetMapping("/by-currency")
    public Page<AccountResponse> filterByCurrency(
            @Parameter(description = "Currency code (e.g., INR, USD)", required = true)
            @RequestParam String currency,

            @ParameterObject
            @PageableDefault(size = 20, page = 0, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return accountService.findByCurrency(currency, pageable);
    }

    @Operation(
        summary = "List active accounts only",
        description = "Get all active accounts"
    )
    @ApiResponse(responseCode = "200", description = "Active accounts")
    @GetMapping("/active")
    public Page<AccountResponse> filterActiveAccounts(
            @ParameterObject
            @PageableDefault(size = 20, page = 0, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return accountService.findActiveAccounts(pageable);
    }

    // === Path variables: AFTER all literal paths ===

    @Operation(summary = "Get account details", description = "Retrieves details of a specific account by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Account found"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/{id}")
    public AccountResponse getAccount(
            @Parameter(description = "Account ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID id) {
        Account account = accountService.getAccountById(id);
        return mapToResponse(account);
    }

    @Operation(summary = "Get account balance", description = "Retrieves the current balance of an account (cached, <50ms)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Balance retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/{id}/balance")
    public BalanceResponse getAccountBalance(
            @Parameter(description = "Account ID", example = "123e4567-e89b-12d3-a456-426614174000")
            @PathVariable UUID id) {
        BigDecimal balance = accountService.getAccountBalance(id);
        return new BalanceResponse(id, balance);
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
