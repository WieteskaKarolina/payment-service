package com.example.fintech.payment.exception;

public class AccountAccessDeniedException extends RuntimeException {
    public AccountAccessDeniedException(String message) {
        super(message);
    }
}
