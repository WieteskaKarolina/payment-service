package com.example.fintech.payment.service;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.exception.AccountNotFoundException;
import com.example.fintech.account.service.AccountService;
import com.example.fintech.payment.dto.CreatePaymentRequest;
import com.example.fintech.payment.entity.Payment;
import com.example.fintech.payment.exception.CurrencyMismatchException;
import com.example.fintech.payment.exception.InsufficientBalanceException;
import com.example.fintech.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void shouldCreatePaymentAndTransferMoney() {
        UUID userId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        Account sourceAccount = mock(Account.class);
        Account destinationAccount = mock(Account.class);

        when(sourceAccount.getCurrency()).thenReturn("PLN");
        when(sourceAccount.getBalance())
                .thenReturn(new BigDecimal("1000.00"));

        when(destinationAccount.getCurrency()).thenReturn("PLN");
        when(destinationAccount.getBalance())
                .thenReturn(new BigDecimal("500.00"));

        when(accountService.getByUserIdAndCurrencyForUpdate(
                userId,
                "PLN"
        )).thenReturn(sourceAccount);

        when(accountService.getByIdForUpdate(destinationAccountId))
                .thenReturn(destinationAccount);

        CreatePaymentRequest request = new CreatePaymentRequest(
                destinationAccountId,
                new BigDecimal("100.00"),
                "PLN"
        );

        Payment payment = mock(Payment.class);

        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(payment);

        Payment result = paymentService.createPayment(userId, request);

        assertSame(payment, result);

        verify(sourceAccount).setBalance(
                new BigDecimal("900.00")
        );

        verify(destinationAccount).setBalance(
                new BigDecimal("600.00")
        );

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void shouldRejectPaymentWhenBalanceIsInsufficient() {
        UUID userId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        Account sourceAccount = mock(Account.class);
        Account destinationAccount = mock(Account.class);

        when(sourceAccount.getCurrency()).thenReturn("PLN");
        when(sourceAccount.getBalance())
                .thenReturn(new BigDecimal("50.00"));

        when(destinationAccount.getCurrency()).thenReturn("PLN");

        when(accountService.getByUserIdAndCurrencyForUpdate(
                userId,
                "PLN"
        )).thenReturn(sourceAccount);

        when(accountService.getByIdForUpdate(destinationAccountId))
                .thenReturn(destinationAccount);

        CreatePaymentRequest request = new CreatePaymentRequest(
                destinationAccountId,
                new BigDecimal("100.00"),
                "PLN"
        );

        assertThrows(
                InsufficientBalanceException.class,
                () -> paymentService.createPayment(userId, request)
        );

        verify(sourceAccount, never())
                .setBalance(any(BigDecimal.class));

        verify(destinationAccount, never())
                .setBalance(any(BigDecimal.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void shouldRejectPaymentWhenCurrenciesDoNotMatch() {
        UUID userId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        Account sourceAccount = mock(Account.class);
        Account destinationAccount = mock(Account.class);

        when(sourceAccount.getCurrency()).thenReturn("PLN");
        when(destinationAccount.getCurrency()).thenReturn("EUR");

        when(accountService.getByUserIdAndCurrencyForUpdate(
                userId,
                "PLN"
        )).thenReturn(sourceAccount);

        when(accountService.getByIdForUpdate(destinationAccountId))
                .thenReturn(destinationAccount);

        CreatePaymentRequest request = new CreatePaymentRequest(
                destinationAccountId,
                new BigDecimal("100.00"),
                "PLN"
        );

        assertThrows(
                CurrencyMismatchException.class,
                () -> paymentService.createPayment(userId, request)
        );

        verify(sourceAccount, never())
                .setBalance(any(BigDecimal.class));

        verify(destinationAccount, never())
                .setBalance(any(BigDecimal.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));
    }

    @Test
    void shouldRejectPaymentWhenSourceAccountDoesNotExist() {
        UUID userId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        when(accountService.getByUserIdAndCurrencyForUpdate(
                userId,
                "PLN"
        )).thenThrow(
                new AccountNotFoundException("Source account not found")
        );

        CreatePaymentRequest request = new CreatePaymentRequest(
                destinationAccountId,
                new BigDecimal("100.00"),
                "PLN"
        );

        assertThrows(
                AccountNotFoundException.class,
                () -> paymentService.createPayment(userId, request)
        );

        verify(accountService, never())
                .getByIdForUpdate(destinationAccountId);

        verifyNoInteractions(paymentRepository);
    }
}