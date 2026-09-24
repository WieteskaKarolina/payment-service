package com.example.fintech.account.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String currency,
        BigDecimal balance
) {}
