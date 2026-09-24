package com.example.fintech.payment.service;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.service.AccountService;
import com.example.fintech.payment.dto.CreatePaymentRequest;
import com.example.fintech.payment.entity.Payment;
import com.example.fintech.payment.exception.CurrencyMismatchException;
import com.example.fintech.payment.exception.InsufficientBalanceException;
import com.example.fintech.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccountService accountService;

    public PaymentService(
            PaymentRepository paymentRepository,
            AccountService accountService
    ) {
        this.paymentRepository = paymentRepository;
        this.accountService = accountService;
    }

    @Transactional
    public Payment createPayment(
            UUID userId,
            CreatePaymentRequest request
    ) {
        Account sourceAccount =
                accountService.getByUserIdAndCurrencyForUpdate(
                        userId,
                        request.currency()
                );

        Account destinationAccount =
                accountService.getByIdForUpdate(
                        request.destinationAccountId()
                );

        if (!sourceAccount.getCurrency()
                .equals(destinationAccount.getCurrency())) {
            throw new CurrencyMismatchException(
                    "Source and destination currencies must match"
            );
        }

        if (sourceAccount.getBalance()
                .compareTo(request.amount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance"
            );
        }

        sourceAccount.setBalance(
                sourceAccount.getBalance()
                        .subtract(request.amount())
        );

        destinationAccount.setBalance(
                destinationAccount.getBalance()
                        .add(request.amount())
        );

        Payment payment = new Payment(
                sourceAccount,
                destinationAccount,
                request.amount(),
                request.currency(),
                "COMPLETED"
        );

        return paymentRepository.save(payment);
    }
}