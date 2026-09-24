package com.example.fintech.auth.service;

import com.example.fintech.auth.dto.LoginRequest;
import com.example.fintech.auth.dto.LoginResponse;
import com.example.fintech.auth.exception.InvalidCredentialsException;
import com.example.fintech.security.jwt.JwtService;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.exception.UserNotFoundException;
import com.example.fintech.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse authenticate(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() ->
                        new InvalidCredentialsException("Invalid email or password")
                );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(token, "Bearer");
    }
}