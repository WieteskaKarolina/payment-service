package com.example.fintech.exception;

public record ErrorResponse(
        int status,
        String message
) {
}