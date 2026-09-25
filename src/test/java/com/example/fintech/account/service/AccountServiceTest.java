package com.example.fintech.account.service;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.exception.AccountNotFoundException;
import com.example.fintech.account.repository.AccountRepository;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private AccountService accountService;

    @Test
    void shouldReturnAccountById() {
        UUID accountId = UUID.randomUUID();

        Account account = new Account(
                null,
                "PLN",
                new BigDecimal("1000.00")
        );

        when(accountRepository.findById(accountId))
                .thenReturn(Optional.of(account));

        Account result = accountService.getById(accountId);

        assertSame(account, result);

        verify(accountRepository).findById(accountId);
    }

    @Test
    void shouldThrowExceptionWhenAccountDoesNotExist() {
        UUID accountId = UUID.randomUUID();

        when(accountRepository.findById(accountId))
                .thenReturn(Optional.empty());

        assertThrows(
                AccountNotFoundException.class,
                () -> accountService.getById(accountId)
        );

        verify(accountRepository).findById(accountId);
    }

    @Test
    void shouldReturnAccountsForUser() {
        UUID userId = UUID.randomUUID();

        List<Account> accounts = List.of(
                new Account(null, "PLN", new BigDecimal("1000.00")),
                new Account(null, "EUR", new BigDecimal("500.00"))
        );

        when(accountRepository.findAllByUserId(userId))
                .thenReturn(accounts);

        List<Account> result =
                accountService.getAccountsForUser(userId);

        assertEquals(accounts, result);

        verify(accountRepository).findAllByUserId(userId);
    }

    @Test
    void shouldCreateAccountForExistingUser() {
        UUID userId = UUID.randomUUID();

        User user = new User(
                "test@example.com",
                "hashed-password",
                "Test",
                "User",
                "USER"
        );

        when(userService.getById(userId))
                .thenReturn(user);

        Account savedAccount = new Account(
                user,
                "PLN",
                BigDecimal.ZERO
        );

        when(accountRepository.save(any(Account.class)))
                .thenReturn(savedAccount);

        Account result =
                accountService.createAccount(userId, "PLN");

        assertSame(savedAccount, result);

        verify(userService).getById(userId);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void shouldDepositMoneyIntoAccount() {
        UUID accountId = UUID.randomUUID();

        Account account = new Account(
                null,
                "PLN",
                new BigDecimal("100.00")
        );

        when(accountRepository.findByIdForUpdate(accountId))
                .thenReturn(Optional.of(account));

        Account result = accountService.deposit(
                accountId,
                new BigDecimal("50.00")
        );

        assertSame(account, result);
        assertEquals(
                new BigDecimal("150.00"),
                result.getBalance()
        );

        verify(accountRepository).findByIdForUpdate(accountId);
    }
}