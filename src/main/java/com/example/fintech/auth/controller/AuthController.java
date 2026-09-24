package com.example.fintech.auth.controller;

import com.example.fintech.auth.dto.LoginRequest;
import com.example.fintech.auth.dto.LoginResponse;
import com.example.fintech.auth.service.AuthenticationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authenticationService.authenticate(request);
    }
}