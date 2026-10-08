package com.example.fintech.payment.service;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.service.AccountService;
import com.example.fintech.kafka.event.PaymentCreatedEvent;
import com.example.fintech.payment.dto.CreatePaymentRequest;
import com.example.fintech.payment.entity.Payment;
import com.example.fintech.payment.exception.AccountAccessDeniedException;
import com.example.fintech.payment.exception.CurrencyMismatchException;
import com.example.fintech.payment.exception.InsufficientBalanceException;
import com.example.fintech.payment.exception.IdempotencyKeyConflictException;
import com.example.fintech.payment.exception.InvalidPaymentRequestException;
import com.example.fintech.payment.exception.SameAccountTransferException;
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
    private record LockedAccounts(
            Account source,
            Account destination
    ) {}

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
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new InvalidPaymentRequestException(
                    "Idempotency-Key must contain 1 to 100 non-blank characters"
            );
        }

        if (request.amount().signum() <= 0 || request.amount().scale() > 4) {
            throw new InvalidPaymentRequestException(
                    "Amount must be positive and have no more than 4 decimal places"
            );
        }

        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new SameAccountTransferException(
                    "Source and destination accounts must be different"
            );
        }

        // Check for an existing key before resolving the destination. This makes
        // changed retries conflict even when their new destination does not exist.
        Account requestedSource = accountService.getById(request.sourceAccountId());
        verifySourceOwnership(requestedSource, userId);
        Optional<Payment> priorAttempt = findPriorPayment(
                requestedSource.getId(), idempotencyKey
        );
        if (priorAttempt.isPresent()) {
            return returnIfSameRequest(
                    priorAttempt.get(), request.destinationAccountId(), request
            );
        }

        // 1. Lock both accounts in deterministic order
        LockedAccounts accounts = lockAccounts(request);

        Account sourceAccount = accounts.source();
        Account destinationAccount = accounts.destination();

        // 2. Verify that the authenticated user owns the source account
        verifySourceOwnership(sourceAccount, userId);

        // 3. Check idempotency
        Optional<Payment> existingPayment = findPriorPayment(
                sourceAccount.getId(), idempotencyKey
        );

        // 4. Return an exact retry; reject key reuse for different request data
        if (existingPayment.isPresent()) {
            return returnIfSameRequest(
                    existingPayment.get(), destinationAccount.getId(), request
            );
        }

        // 5. Validate transfer
        validateTransfer(
                sourceAccount,
                destinationAccount,
                request.amount(),
                request.currency()
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

    private void verifySourceOwnership(Account sourceAccount, UUID userId) {
        if (!sourceAccount.getUser().getId().equals(userId)) {
            throw new AccountAccessDeniedException(
                    "You do not have access to this account"
            );
        }
    }

    private Optional<Payment> findPriorPayment(UUID sourceAccountId, String idempotencyKey) {
        return paymentRepository.findBySourceAccountIdAndIdempotencyKey(
                sourceAccountId, idempotencyKey
        );
    }

    private Payment returnIfSameRequest(
            Payment priorPayment,
            UUID destinationAccountId,
            CreatePaymentRequest request
    ) {
        boolean sameRequest = priorPayment.getDestinationAccount().getId()
                .equals(destinationAccountId)
                && priorPayment.getAmount().compareTo(request.amount()) == 0
                && priorPayment.getCurrency().equals(request.currency());
        if (!sameRequest) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key was already used for a different payment"
            );
        }
        return priorPayment;
    }

    private void validateTransfer(
            Account sourceAccount,
            Account destinationAccount,
            BigDecimal amount,
            String currency
    ) {

        if (!sourceAccount.getCurrency()
                .equals(destinationAccount.getCurrency())) {

            throw new CurrencyMismatchException(
                    "Source and destination currencies must match"
            );
        }

        if (!sourceAccount.getCurrency().equals(currency)) {
            throw new CurrencyMismatchException("Payment currency must match the account currency");
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

    private LockedAccounts lockAccounts(CreatePaymentRequest request) {

        UUID sourceAccountId = request.sourceAccountId();
        UUID destinationAccountId = request.destinationAccountId();

        UUID firstAccountId;
        UUID secondAccountId;

        if (sourceAccountId.compareTo(destinationAccountId) < 0) {
            firstAccountId = sourceAccountId;
            secondAccountId = destinationAccountId;
        } else {
            firstAccountId = destinationAccountId;
            secondAccountId = sourceAccountId;
        }

        Account firstAccount =
                accountService.getByIdForUpdate(firstAccountId);

        Account secondAccount =
                accountService.getByIdForUpdate(secondAccountId);

        if (firstAccount.getId().equals(sourceAccountId)) {
            return new LockedAccounts(
                    firstAccount,
                    secondAccount
            );
        }

        return new LockedAccounts(
                secondAccount,
                firstAccount
        );
    }
}
