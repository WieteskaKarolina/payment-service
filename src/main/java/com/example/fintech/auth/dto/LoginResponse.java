package com.example.fintech.auth.dto;

public record LoginResponse(
        String accessToken,
        String tokenType
) {
}