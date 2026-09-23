package com.example.fintech.auth.service;

import com.example.fintech.auth.dto.LoginRequest;
import com.example.fintech.auth.dto.LoginResponse;
import com.example.fintech.security.jwt.JwtService;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.exception.UserNotFoundException;
import com.example.fintech.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthenticationService authenticationService;


    @Test
    void shouldAuthenticateUserWithValidCredentials() {

        LoginRequest request = new LoginRequest(
                "john@example.com",
                "Password123!"
        );

        User user = new User(
                "john@example.com",
                "hashed-password",
                "John",
                "Doe",
                "USER"
        );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )).thenReturn(true);

        when(jwtService.generateToken(user))
                .thenReturn("test-jwt-token");

        LoginResponse result = authenticationService.authenticate(request);

        assertEquals("test-jwt-token", result.accessToken());
        assertEquals("Bearer", result.tokenType());

        verify(userRepository).findByEmail(request.email());

        verify(passwordEncoder).matches(
                request.password(),
                user.getPasswordHash()
        );

        verify(jwtService).generateToken(user);
    }


    @Test
    void shouldRejectUnknownEmail() {

        LoginRequest request = new LoginRequest(
                "unknown@example.com",
                "Password123!"
        );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> authenticationService.authenticate(request)
        );

        assertEquals(
                "Invalid email or password",
                exception.getMessage()
        );

        verify(userRepository).findByEmail(request.email());

        verifyNoInteractions(passwordEncoder);
    }


    @Test
    void shouldRejectIncorrectPassword() {

        LoginRequest request = new LoginRequest(
                "john@example.com",
                "WrongPassword123!"
        );

        User user = new User(
                "john@example.com",
                "hashed-password",
                "John",
                "Doe",
                "USER"
        );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )).thenReturn(false);

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> authenticationService.authenticate(request)
        );

        assertEquals(
                "Invalid email or password",
                exception.getMessage()
        );

        verify(userRepository).findByEmail(request.email());

        verify(passwordEncoder).matches(
                request.password(),
                user.getPasswordHash()
        );
    }
}