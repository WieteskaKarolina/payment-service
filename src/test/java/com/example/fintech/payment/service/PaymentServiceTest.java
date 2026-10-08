package com.example.fintech.payment.service;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.exception.AccountNotFoundException;
import com.example.fintech.account.service.AccountService;
import com.example.fintech.kafka.event.PaymentCreatedEvent;
import com.example.fintech.payment.dto.CreatePaymentRequest;
import com.example.fintech.payment.entity.Payment;
import com.example.fintech.payment.exception.AccountAccessDeniedException;
import com.example.fintech.payment.exception.CurrencyMismatchException;
import com.example.fintech.payment.exception.InsufficientBalanceException;
import com.example.fintech.payment.outbox.entity.OutboxEvent;
import com.example.fintech.payment.outbox.repository.OutboxEventRepository;
import com.example.fintech.payment.repository.PaymentRepository;
import com.example.fintech.user.entity.User;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
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

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void shouldCreatePaymentAndTransferMoney() {
        UUID userId = UUID.fromString(
                "10000000-0000-0000-0000-000000000001"
        );

        UUID sourceAccountId = UUID.fromString(
                "00000000-0000-0000-0000-000000000001"
        );

        UUID destinationAccountId = UUID.fromString(
                "00000000-0000-0000-0000-000000000002"
        );

        User user = mock(User.class);

        Account sourceAccount = mock(Account.class);
        Account destinationAccount = mock(Account.class);

        when(sourceAccount.getId()).thenReturn(sourceAccountId);
        when(destinationAccount.getId()).thenReturn(destinationAccountId);

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

        when(objectMapper.writeValueAsString(any(PaymentCreatedEvent.class)))
                .thenReturn("""
                {
                    "paymentId": "test",
                    "sourceAccountId": "source",
                    "destinationAccountId": "destination",
                    "amount": 100.00,
                    "currency": "PLN"
                }
                """);

        Payment result = paymentService.createPayment(userId, "test-key", request);

        assertNotNull(result);

        verify(sourceAccount).setBalance(
                new BigDecimal("900.00")
        );

        verify(destinationAccount).setBalance(
                new BigDecimal("600.00")
        );

        verify(paymentRepository).save(any(Payment.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void shouldRejectPaymentWhenSourceAccountBelongsToAnotherUser() {
        UUID userId = UUID.fromString(
                "10000000-0000-0000-0000-000000000001"
        );

        UUID accountOwnerId = UUID.fromString(
                "10000000-0000-0000-0000-000000000002"
        );

        UUID sourceAccountId = UUID.fromString(
                "00000000-0000-0000-0000-000000000001"
        );

        UUID destinationAccountId = UUID.fromString(
                "00000000-0000-0000-0000-000000000002"
        );

        User accountOwner = mock(User.class);

        Account sourceAccount = mock(Account.class);
        Account destinationAccount = mock(Account.class);

        when(sourceAccount.getId()).thenReturn(sourceAccountId);

        when(sourceAccount.getUser()).thenReturn(accountOwner);
        when(accountOwner.getId()).thenReturn(accountOwnerId);

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
                AccountAccessDeniedException.class,
                () -> paymentService.createPayment(userId, "test-key", request)
        );

        verify(accountService)
                .getByIdForUpdate(sourceAccountId);

        verify(accountService)
                .getByIdForUpdate(destinationAccountId);

        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void shouldRejectPaymentWhenBalanceIsInsufficient() {
        UUID userId = UUID.fromString(
                "10000000-0000-0000-0000-000000000001"
        );

        UUID sourceAccountId = UUID.fromString(
                "00000000-0000-0000-0000-000000000001"
        );

        UUID destinationAccountId = UUID.fromString(
                "00000000-0000-0000-0000-000000000002"
        );

        User user = mock(User.class);

        Account sourceAccount = mock(Account.class);
        Account destinationAccount = mock(Account.class);

        when(sourceAccount.getId()).thenReturn(sourceAccountId);

        when(sourceAccount.getUser())
                .thenReturn(user);

        when(user.getId())
                .thenReturn(userId);

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
                () -> paymentService.createPayment(userId, "test-key", request)
        );

        verify(sourceAccount, never())
                .setBalance(any(BigDecimal.class));

        verify(destinationAccount, never())
                .setBalance(any(BigDecimal.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));
        verify(outboxEventRepository, never())
                .save(any(OutboxEvent.class));
    }

    @Test
    void shouldRejectPaymentWhenCurrenciesDoNotMatch() {
        UUID userId =
                UUID.fromString("10000000-0000-0000-0000-000000000001");

        UUID sourceAccountId =
                UUID.fromString("00000000-0000-0000-0000-000000000001");

        UUID destinationAccountId =
                UUID.fromString("00000000-0000-0000-0000-000000000002");

        User user = new User(
                "test@example.com",
                "passwordHash",
                "Test",
                "User",
                "USER"
        );
        user.setId(userId);

        Account sourceAccount = mock(Account.class);
        Account destinationAccount = mock(Account.class);

        when(sourceAccount.getId()).thenReturn(sourceAccountId);

        when(sourceAccount.getUser()).thenReturn(user);

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
                () -> paymentService.createPayment(userId, "test-key", request)
        );

        verify(sourceAccount, never())
                .setBalance(any(BigDecimal.class));

        verify(destinationAccount, never())
                .setBalance(any(BigDecimal.class));

        verify(paymentRepository, never())
                .save(any(Payment.class));
        verify(outboxEventRepository, never())
                .save(any(OutboxEvent.class));
    }

    @Test
    void shouldRejectPaymentWhenSourceAccountDoesNotExist() {
        UUID userId = UUID.fromString(
                "10000000-0000-0000-0000-000000000001"
        );

        UUID sourceAccountId = UUID.fromString(
                "00000000-0000-0000-0000-000000000001"
        );

        UUID destinationAccountId = UUID.fromString(
                "00000000-0000-0000-0000-000000000002"
        );

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
                () -> paymentService.createPayment(userId, "test-key", request)
        );

        verify(accountService)
                .getByIdForUpdate(sourceAccountId);

        verify(accountService, never())
                .getByIdForUpdate(destinationAccountId);

        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void shouldReturnExistingPaymentForSameIdempotencyKey() {
        UUID userId = UUID.fromString(
                "10000000-0000-0000-0000-000000000001"
        );

        UUID sourceAccountId = UUID.fromString(
                "00000000-0000-0000-0000-000000000001"
        );

        UUID destinationAccountId = UUID.fromString(
                "00000000-0000-0000-0000-000000000002"
        );

        User sourceUser = new User(
                "source@test.com",
                "passwordHash",
                "Source",
                "User",
                "USER"
        );

        sourceUser.setId(userId);

        Account sourceAccount = new Account(
                sourceUser,
                "PLN",
                new BigDecimal("1000.00")
        );

        sourceAccount.setId(sourceAccountId);

        Account destinationAccount = new Account(
                new User(
                        "destination@test.com",
                        "passwordHash",
                        "Destination",
                        "User",
                        "USER"
                ),
                "PLN",
                new BigDecimal("500.00")
        );

        destinationAccount.setId(destinationAccountId);

        Payment existingPayment = getExistingPayment(
                sourceAccount,
                destinationAccount
        );

        CreatePaymentRequest request = new CreatePaymentRequest(
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("100.00"),
                "PLN"
        );

        when(accountService.getByIdForUpdate(sourceAccountId))
                .thenReturn(sourceAccount);

        when(accountService.getByIdForUpdate(destinationAccountId))
                .thenReturn(destinationAccount);

        when(paymentRepository.findBySourceAccountIdAndIdempotencyKey(
                sourceAccountId,
                "test-key"
        )).thenReturn(Optional.of(existingPayment));

        Payment result = paymentService.createPayment(
                userId,
                "test-key",
                request
        );

        assertSame(existingPayment, result);

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void shouldRejectPaymentWhenSourceAndDestinationAccountsAreTheSame() {
        UUID accountId = UUID.fromString(
                "00000000-0000-0000-0000-000000000001"
        );
        CreatePaymentRequest request = new CreatePaymentRequest(
                accountId,
                accountId,
                new BigDecimal("10.00"),
                "PLN"
        );

        assertThrows(
                com.example.fintech.payment.exception.SameAccountTransferException.class,
                () -> paymentService.createPayment(
                        UUID.randomUUID(), "test-key", request
                )
        );

        verifyNoInteractions(accountService);
        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(outboxEventRepository);
    }

    private static @NonNull Payment getExistingPayment(
            Account sourceAccount,
            Account destinationAccount
    ) {
        return new Payment(
                sourceAccount,
                destinationAccount,
                new BigDecimal("100.00"),
                "PLN",
                "COMPLETED",
                "test-key"
        );
    }
}