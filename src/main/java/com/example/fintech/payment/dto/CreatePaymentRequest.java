package com.example.fintech.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequest(

        @NotNull
        UUID sourceAccountId,

        @NotNull
        UUID destinationAccountId,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @NotNull
        @Size(min = 3, max = 3)
        String currency
) {
}
