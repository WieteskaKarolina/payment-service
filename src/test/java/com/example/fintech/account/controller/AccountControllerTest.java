package com.example.fintech.account.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.service.AccountService;
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
}