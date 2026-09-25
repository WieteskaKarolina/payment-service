package com.example.fintech.payment.service;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.exception.AccountNotFoundException;
import com.example.fintech.account.service.AccountService;
import com.example.fintech.kafka.event.PaymentCreatedEvent;
import com.example.fintech.kafka.producer.PaymentEventProducer;
import com.example.fintech.payment.dto.CreatePaymentRequest;
import com.example.fintech.payment.entity.Payment;
import com.example.fintech.payment.exception.AccountAccessDeniedException;
import com.example.fintech.payment.exception.CurrencyMismatchException;
import com.example.fintech.payment.exception.InsufficientBalanceException;
import com.example.fintech.payment.repository.PaymentRepository;
import com.example.fintech.user.entity.User;
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

    @Mock
    private PaymentEventProducer paymentEventProducer;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void shouldCreatePaymentAndTransferMoney() {
        UUID userId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        User user = mock(User.class);

        Account sourceAccount = mock(Account.class);
        Account destinationAccount = mock(Account.class);

        when(sourceAccount.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);
        when(sourceAccount.getCurrency()).thenReturn("PLN");
        when(sourceAccount.getBalance())
                .thenReturn(new BigDecimal("1000.00"));

        when(destinationAccount.getCurrency()).thenReturn("PLN");
        when(destinationAccount.getBalance())
                .thenReturn(new BigDecimal("500.00"));

        when(accountService.getByIdForUpdate(sourceAccountId))
                .thenReturn(sourceAccount);

        when(accountService.getByIdForUpdate(destinationAccountId))
                .thenReturn(destinationAccount);

        CreatePaymentRequest request = new CreatePaymentRequest(
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("100.00"),
                "PLN"
        );

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Payment result = paymentService.createPayment(userId, request);

        assertNotNull(result);

        verify(sourceAccount).setBalance(
                new BigDecimal("900.00")
        );

        verify(destinationAccount).setBalance(
                new BigDecimal("600.00")
        );

        verify(paymentRepository).save(any(Payment.class));

        verify(paymentEventProducer)
                .publishPaymentCreated(any(PaymentCreatedEvent.class));
    }

    @Test
    void shouldRejectPaymentWhenSourceAccountBelongsToAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID accountOwnerId = UUID.randomUUID();

        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        User accountOwner = mock(User.class);
        Account sourceAccount = mock(Account.class);

        when(sourceAccount.getUser()).thenReturn(accountOwner);
        when(accountOwner.getId()).thenReturn(accountOwnerId);

        when(accountService.getByIdForUpdate(sourceAccountId))
                .thenReturn(sourceAccount);

        CreatePaymentRequest request = new CreatePaymentRequest(
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("100.00"),
                "PLN"
        );

        assertThrows(
                AccountAccessDeniedException.class,
                () -> paymentService.createPayment(userId, request)
        );

        verify(accountService, never())
                .getByIdForUpdate(destinationAccountId);

        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(paymentEventProducer);
    }

    @Test
    void shouldRejectPaymentWhenBalanceIsInsufficient() {
        UUID userId = UUID.randomUUID();
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        User user = mock(User.class);
        Account sourceAccount = mock(Account.class);
        Account destinationAccount = mock(Account.class);

        when(sourceAccount.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);

        when(sourceAccount.getCurrency()).thenReturn("PLN");
        when(sourceAccount.getBalance())
                .thenReturn(new BigDecimal("50.00"));

        when(destinationAccount.getCurrency()).thenReturn("PLN");

        when(accountService.getByIdForUpdate(sourceAccountId))
                .thenReturn(sourceAccount);

        when(accountService.getByIdForUpdate(destinationAccountId))
                .thenReturn(destinationAccount);

        CreatePaymentRequest request = new CreatePaymentRequest(
                sourceAccountId,
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
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        User user = mock(User.class);
        Account sourceAccount = mock(Account.class);
        Account destinationAccount = mock(Account.class);

        when(sourceAccount.getUser()).thenReturn(user);
        when(user.getId()).thenReturn(userId);

        when(sourceAccount.getCurrency()).thenReturn("PLN");
        when(destinationAccount.getCurrency()).thenReturn("EUR");

        when(accountService.getByIdForUpdate(sourceAccountId))
                .thenReturn(sourceAccount);

        when(accountService.getByIdForUpdate(destinationAccountId))
                .thenReturn(destinationAccount);

        CreatePaymentRequest request = new CreatePaymentRequest(
                sourceAccountId,
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
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        when(accountService.getByIdForUpdate(sourceAccountId))
                .thenThrow(
                        new AccountNotFoundException(
                                "Source account not found"
                        )
                );

        CreatePaymentRequest request = new CreatePaymentRequest(
                sourceAccountId,
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
        verifyNoInteractions(paymentEventProducer);
    }
}