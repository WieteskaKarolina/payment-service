package com.example.fintech.user.service;

import com.example.fintech.user.dto.CreateUserRequest;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldReturnUserWhenUserExists() {
        UUID userId = UUID.randomUUID();

        User user = new User(
                "test@example.com",
                "hashed-password",
                "Test",
                "User",
                "USER"
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        User result = userService.getById(userId);

        assertEquals(user, result);
    }

    @Test
    void shouldThrowExceptionWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.getById(userId)
        );
    }

    @Test
    void shouldCreateUserWithHashedPassword() {
        CreateUserRequest request = new CreateUserRequest(
                "new@example.com",
                "plain-password",
                "John",
                "Doe"
        );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode(request.password()))
                .thenReturn("hashed-password");

        User savedUser = new User(
                request.email(),
                "hashed-password",
                request.firstName(),
                request.lastName(),
                "USER"
        );

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        User result = userService.createUser(request);

        assertEquals("new@example.com", result.getEmail());
        assertEquals("hashed-password", result.getPasswordHash());
    }

    @Test
    void shouldRejectUserWhenEmailAlreadyExists() {
        CreateUserRequest request = new CreateUserRequest(
                "existing@example.com",
                "plain-password",
                "John",
                "Doe"
        );

        User existingUser = new User(
                request.email(),
                "existing-hash",
                "Jane",
                "Doe",
                "USER"
        );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(existingUser));

        assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUser(request)
        );

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }
}