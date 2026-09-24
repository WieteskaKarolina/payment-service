package com.example.fintech.user.dto;

import java.util.UUID;

public record CreateUserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName
) {
}
