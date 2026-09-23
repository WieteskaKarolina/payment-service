package com.example.fintech.user.dto;

public record CreateUserRequest(
        String email,
        String password,
        String firstName,
        String lastName
) {
}
