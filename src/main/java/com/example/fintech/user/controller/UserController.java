package com.example.fintech.user.controller;

import com.example.fintech.user.dto.CreateUserRequest;
import com.example.fintech.user.dto.CreateUserResponse;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        User user = userService.createUser(request);

        return new CreateUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName()
        );
    }

    @GetMapping("/me")
    public CreateUserResponse getCurrentUser(
            Authentication authentication
    ) {
        UUID userId = UUID.fromString(authentication.getName());

        User user = userService.getById(userId);

        return new CreateUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName()
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public String adminEndpoint() {
        return "Admin access granted";
    }
}