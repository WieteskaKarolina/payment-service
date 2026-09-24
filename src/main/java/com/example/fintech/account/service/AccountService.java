package com.example.fintech.account.service;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.exception.AccountNotFoundException;
import com.example.fintech.account.repository.AccountRepository;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.service.UserService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserService userService;

    public AccountService(
            AccountRepository accountRepository,
            UserService userService
    ) {
        this.accountRepository = accountRepository;
        this.userService = userService;
    }

    public Account getById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() ->
                        new AccountNotFoundException("Account not found"));
    }

    public List<Account> getAccountsForUser(UUID userId) {
        return accountRepository.findAllByUserId(userId);
    }

    public Account createAccount(
            UUID userId,
            String currency
    ) {
        User user = userService.getById(userId);

        Account account = new Account(
                user,
                currency,
                BigDecimal.ZERO
        );

        return accountRepository.save(account);
    }
}