package com.example.fintech.payment.service;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.service.AccountService;
import com.example.fintech.kafka.event.PaymentCreatedEvent;
import com.example.fintech.kafka.producer.PaymentEventProducer;
import com.example.fintech.payment.dto.CreatePaymentRequest;
import com.example.fintech.payment.entity.Payment;
import com.example.fintech.payment.exception.CurrencyMismatchException;
import com.example.fintech.payment.exception.InsufficientBalanceException;
import com.example.fintech.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccountService accountService;
    private final PaymentEventProducer paymentEventProducer;

    public PaymentService(
            PaymentRepository paymentRepository,
            AccountService accountService,
            PaymentEventProducer paymentEventProducer
    ) {
        this.paymentRepository = paymentRepository;
        this.accountService = accountService;
        this.paymentEventProducer = paymentEventProducer;
    }

    @Transactional
    public Payment createPayment(
            UUID userId,
            CreatePaymentRequest request
    ) {
        Account sourceAccount = getSourceAccount(userId, request);
        Account destinationAccount = getDestinationAccount(request);

        validateTransfer(sourceAccount, destinationAccount, request.amount());

        transferBalance(sourceAccount, destinationAccount, request.amount());

        Payment payment = createPaymentEntity(
                sourceAccount,
                destinationAccount,
                request
        );

        Payment savedPayment = paymentRepository.save(payment);

        publishPaymentCreatedEvent(savedPayment);

        return savedPayment;
    }

    private Account getSourceAccount(
            UUID userId,
            CreatePaymentRequest request
    ) {
        return accountService.getByUserIdAndCurrencyForUpdate(
                userId,
                request.currency()
        );
    }

    private Account getDestinationAccount(CreatePaymentRequest request) {
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
            CreatePaymentRequest request
    ) {
        return new Payment(
                sourceAccount,
                destinationAccount,
                request.amount(),
                request.currency(),
                "COMPLETED"
        );
    }

    private void publishPaymentCreatedEvent(Payment payment) {
        PaymentCreatedEvent event = new PaymentCreatedEvent(
                payment.getId(),
                payment.getSourceAccount().getId(),
                payment.getDestinationAccount().getId(),
                payment.getAmount(),
                payment.getCurrency()
        );

        paymentEventProducer.publishPaymentCreated(event);
    }
}