package com.example.fintech.payment.exception;

public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(String message) {
        super(message);
    }
}
