package com.example.fintech.payment.service;

import com.example.fintech.account.entity.Account;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccountService accountService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentService(
            PaymentRepository paymentRepository,
            AccountService accountService,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.accountService = accountService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Payment createPayment(
            UUID userId,
            String idempotencyKey,
            CreatePaymentRequest request
    ) {
        // 1. Lock source account
        Account sourceAccount = getSourceAccount(userId, request);

        // 2. Check idempotency
        Optional<Payment> existingPayment =
                paymentRepository.findBySourceAccountIdAndIdempotencyKey(
                        sourceAccount.getId(),
                        idempotencyKey
                );

        // 3. Same request was already processed
        if (existingPayment.isPresent()) {
            return existingPayment.get();
        }

        // 4. Lock destination account
        Account destinationAccount = getDestinationAccount(request);

        // 5. Validate transfer
        validateTransfer(
                sourceAccount,
                destinationAccount,
                request.amount()
        );

        // 6. Transfer money
        transferBalance(
                sourceAccount,
                destinationAccount,
                request.amount()
        );

        // 7. Create payment
        Payment payment = createPaymentEntity(
                sourceAccount,
                destinationAccount,
                request,
                idempotencyKey
        );

        Payment savedPayment = paymentRepository.save(payment);

        // 8. Create outbox event
        createOutboxEvent(savedPayment);

        return savedPayment;
    }

    private Account getSourceAccount(
            UUID userId,
            CreatePaymentRequest request
    ) {
        Account sourceAccount = accountService.getByIdForUpdate(
                request.sourceAccountId()
        );

        if (!sourceAccount.getUser().getId().equals(userId)) {
            throw new AccountAccessDeniedException(
                    "You do not have access to this account"
            );
        }

        return sourceAccount;
    }

    private Account getDestinationAccount(
            CreatePaymentRequest request
    ) {
        return accountService.getByIdForUpdate(
                request.destinationAccountId()
        );
    }

    private void validateTransfer(
            Account sourceAccount,
            Account destinationAccount,
            BigDecimal amount
    ) {
        if (!sourceAccount.getCurrency()
                .equals(destinationAccount.getCurrency())) {

            throw new CurrencyMismatchException(
                    "Source and destination currencies must match"
            );
        }

        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance"
            );
        }
    }

    private void transferBalance(
            Account sourceAccount,
            Account destinationAccount,
            BigDecimal amount
    ) {
        sourceAccount.setBalance(
                sourceAccount.getBalance().subtract(amount)
        );

        destinationAccount.setBalance(
                destinationAccount.getBalance().add(amount)
        );
    }

    private Payment createPaymentEntity(
            Account sourceAccount,
            Account destinationAccount,
            CreatePaymentRequest request,
            String idempotencyKey
    ) {
        return new Payment(
                sourceAccount,
                destinationAccount,
                request.amount(),
                request.currency(),
                "COMPLETED",
                idempotencyKey
        );
    }

    private void createOutboxEvent(Payment payment) {
        PaymentCreatedEvent event = new PaymentCreatedEvent(
                payment.getId(),
                payment.getSourceAccount().getId(),
                payment.getDestinationAccount().getId(),
                payment.getAmount(),
                payment.getCurrency()
        );

        String payload = objectMapper.writeValueAsString(event);

        OutboxEvent outboxEvent = new OutboxEvent(
                UUID.randomUUID(),
                "PAYMENT_CREATED",
                payment.getId(),
                payload
        );

        outboxEventRepository.save(outboxEvent);
    }
}