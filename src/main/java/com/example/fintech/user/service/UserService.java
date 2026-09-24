package com.example.fintech.user.service;

import com.example.fintech.user.dto.CreateUserRequest;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.exception.UserAlreadyExistsException;
import com.example.fintech.user.exception.UserNotFoundException;
import com.example.fintech.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User getById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    public User createUser(CreateUserRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new UserAlreadyExistsException(
                    "User with this email already exists"
            );
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
                request.email(),
                passwordHash,
                request.firstName(),
                request.lastName(),
                "USER"
        );

        return userRepository.save(user);
    }
}