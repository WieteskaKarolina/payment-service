package com.example.fintech.account.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.service.AccountService;
import com.example.fintech.exception.GlobalExceptionHandler;
import com.example.fintech.user.exception.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        AccountController controller =
                new AccountController(accountService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        Account account = mock(Account.class);

        when(account.getId()).thenReturn(accountId);
        when(account.getCurrency()).thenReturn("PLN");
        when(account.getBalance()).thenReturn(BigDecimal.ZERO);

        when(accountService.createAccount(userId, "PLN"))
                .thenReturn(account);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        try {
            mockMvc.perform(
                            post("/api/accounts")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                    {
                                        "currency": "PLN"
                                    }
                                    """)
                    )
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(accountId.toString()))
                    .andExpect(jsonPath("$.currency").value("PLN"))
                    .andExpect(jsonPath("$.balance").value(0));

            verify(accountService)
                    .createAccount(userId, "PLN");

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldGetAccountsForUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();

        Account plnAccount = mock(Account.class);
        when(plnAccount.getId()).thenReturn(firstAccountId);
        when(plnAccount.getCurrency()).thenReturn("PLN");
        when(plnAccount.getBalance()).thenReturn(new BigDecimal("1500.00"));

        Account eurAccount = mock(Account.class);
        when(eurAccount.getId()).thenReturn(secondAccountId);
        when(eurAccount.getCurrency()).thenReturn("EUR");
        when(eurAccount.getBalance()).thenReturn(new BigDecimal("500.00"));

        when(accountService.getAccountsForUser(userId))
                .thenReturn(List.of(plnAccount, eurAccount));

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        try {
            mockMvc.perform(
                            get("/api/accounts")
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(firstAccountId.toString()))
                    .andExpect(jsonPath("$[0].currency").value("PLN"))
                    .andExpect(jsonPath("$[0].balance").value(1500.00))
                    .andExpect(jsonPath("$[1].id").value(secondAccountId.toString()))
                    .andExpect(jsonPath("$[1].currency").value("EUR"))
                    .andExpect(jsonPath("$[1].balance").value(500.00));

            verify(accountService)
                    .getAccountsForUser(userId);

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldReturnEmptyListWhenUserHasNoAccounts() throws Exception {
        UUID userId = UUID.randomUUID();

        when(accountService.getAccountsForUser(userId))
                .thenReturn(List.of());

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        try {
            mockMvc.perform(
                            get("/api/accounts")
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));

            verify(accountService)
                    .getAccountsForUser(userId);

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldRejectInvalidCurrency() throws Exception {
        UUID userId = UUID.randomUUID();

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        try {
            mockMvc.perform(
                            post("/api/accounts")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                {
                                    "currency": "PL"
                                }
                                """)
                    )
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(accountService);

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        UUID userId = UUID.randomUUID();

        when(accountService.createAccount(userId, "PLN"))
                .thenThrow(new UserNotFoundException("User not found"));

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);

        try {
            mockMvc.perform(
                            post("/api/accounts")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                {
                                    "currency": "PLN"
                                }
                                """)
                    )
                    .andExpect(status().isNotFound());

        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}