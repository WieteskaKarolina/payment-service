package com.example.fintech.payment.controller;

import com.example.fintech.payment.dto.CreatePaymentRequest;
import com.example.fintech.payment.dto.PaymentResponse;
import com.example.fintech.payment.entity.Payment;
import com.example.fintech.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreatePaymentRequest request
    ) {
        Payment payment = paymentService.createPayment(
                UUID.fromString(userId),
                request
        );

        return new PaymentResponse(
                payment.getId(),
                payment.getSourceAccount().getId(),
                payment.getDestinationAccount().getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus()
        );
    }
}