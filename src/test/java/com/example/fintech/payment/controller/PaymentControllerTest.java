package com.example.fintech.payment.controller;

import com.example.fintech.account.exception.AccountNotFoundException;
import com.example.fintech.payment.dto.CreatePaymentRequest;
import com.example.fintech.payment.entity.Payment;
import com.example.fintech.payment.exception.CurrencyMismatchException;
import com.example.fintech.payment.exception.InsufficientBalanceException;
import com.example.fintech.payment.service.PaymentService;
import com.example.fintech.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        PaymentController controller =
                new PaymentController(paymentService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreatePayment() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        Payment payment = mock(Payment.class);

        when(payment.getId()).thenReturn(paymentId);
        when(payment.getSourceAccount()).thenReturn(
                mock(com.example.fintech.account.entity.Account.class)
        );
        when(payment.getDestinationAccount()).thenReturn(
                mock(com.example.fintech.account.entity.Account.class)
        );
        when(payment.getSourceAccount().getId())
                .thenReturn(sourceAccountId);
        when(payment.getDestinationAccount().getId())
                .thenReturn(destinationAccountId);
        when(payment.getAmount())
                .thenReturn(new BigDecimal("100.00"));
        when(payment.getCurrency()).thenReturn("PLN");
        when(payment.getStatus()).thenReturn("COMPLETED");

        when(paymentService.createPayment(
                eq(userId),
                any(CreatePaymentRequest.class)
        )).thenReturn(payment);

        setAuthentication(userId);

        mockMvc.perform(
                        post("/api/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "destinationAccountId": "%s",
                                            "amount": 100.00,
                                            "currency": "PLN"
                                        }
                                        """.formatted(destinationAccountId))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id")
                        .value(paymentId.toString()))
                .andExpect(jsonPath("$.sourceAccountId")
                        .value(sourceAccountId.toString()))
                .andExpect(jsonPath("$.destinationAccountId")
                        .value(destinationAccountId.toString()))
                .andExpect(jsonPath("$.amount")
                        .value(100))
                .andExpect(jsonPath("$.currency")
                        .value("PLN"))
                .andExpect(jsonPath("$.status")
                        .value("COMPLETED"));

        verify(paymentService).createPayment(
                eq(userId),
                any(CreatePaymentRequest.class)
        );

        clearAuthentication();
    }

    @Test
    void shouldRejectInvalidRequest() throws Exception {
        UUID userId = UUID.randomUUID();

        setAuthentication(userId);

        mockMvc.perform(
                        post("/api/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "destinationAccountId": "%s",
                                            "amount": -100.00,
                                            "currency": "PLN"
                                        }
                                        """.formatted(UUID.randomUUID()))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);

        clearAuthentication();
    }

    @Test
    void shouldReturnBadRequestWhenCurrencyIsInvalid() throws Exception {
        UUID userId = UUID.randomUUID();

        setAuthentication(userId);

        mockMvc.perform(
                        post("/api/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "destinationAccountId": "%s",
                                            "amount": 100.00,
                                            "currency": "PL"
                                        }
                                        """.formatted(UUID.randomUUID()))
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(paymentService);

        clearAuthentication();
    }

    @Test
    void shouldReturnNotFoundWhenSourceAccountDoesNotExist()
            throws Exception {

        UUID userId = UUID.randomUUID();

        doThrow(new AccountNotFoundException("Source account not found"))
                .when(paymentService)
                .createPayment(
                        eq(userId),
                        any(CreatePaymentRequest.class)
                );

        setAuthentication(userId);

        mockMvc.perform(
                post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "destinationAccountId": "%s",
                                    "amount": 100.00,
                                    "currency": "PLN"
                                }
                                """.formatted(UUID.randomUUID()))
        );

        verify(paymentService).createPayment(
                eq(userId),
                any(CreatePaymentRequest.class)
        );

        clearAuthentication();
    }

    @Test
    void shouldReturnBadRequestWhenCurrenciesDoNotMatch()
            throws Exception {

        UUID userId = UUID.randomUUID();

        when(paymentService.createPayment(
                eq(userId),
                any(CreatePaymentRequest.class)
        )).thenThrow(
                new CurrencyMismatchException(
                        "Source and destination currencies must match"
                )
        );

        setAuthentication(userId);

        mockMvc.perform(
                        post("/api/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "destinationAccountId": "%s",
                                            "amount": 100.00,
                                            "currency": "PLN"
                                        }
                                        """.formatted(UUID.randomUUID()))
                )
                .andExpect(status().isBadRequest());

        clearAuthentication();
    }

    @Test
    void shouldReturnBadRequestWhenBalanceIsInsufficient()
            throws Exception {

        UUID userId = UUID.randomUUID();

        when(paymentService.createPayment(
                eq(userId),
                any(CreatePaymentRequest.class)
        )).thenThrow(
                new InsufficientBalanceException(
                        "Insufficient balance"
                )
        );

        setAuthentication(userId);

        mockMvc.perform(
                        post("/api/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "destinationAccountId": "%s",
                                            "amount": 100.00,
                                            "currency": "PLN"
                                        }
                                        """.formatted(UUID.randomUUID()))
                )
                .andExpect(status().isBadRequest());

        clearAuthentication();
    }

    private void setAuthentication(UUID userId) {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        userId.toString(),
                        null,
                        List.of()
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    private void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}