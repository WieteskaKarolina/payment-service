package com.example.fintech.account.controller;

import com.example.fintech.account.dto.AccountResponse;
import com.example.fintech.account.dto.CreateAccountRequest;
import com.example.fintech.account.entity.Account;
import com.example.fintech.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
    public AccountResponse createAccount(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateAccountRequest request
    ) {
        Account account = accountService.createAccount(
                UUID.fromString(userId),
                request.currency()
        );

        return new AccountResponse(
                account.getId(),
                account.getCurrency(),
                account.getBalance()
        );
    }

    @GetMapping
    public List<AccountResponse> getAccounts(
            @AuthenticationPrincipal String userId
    ) {
        return accountService.getAccountsForUser(UUID.fromString(userId))
                .stream()
                .map(account -> new AccountResponse(
                        account.getId(),
                        account.getCurrency(),
                        account.getBalance()
                ))
                .toList();
    }
}