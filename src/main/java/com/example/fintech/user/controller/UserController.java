package com.example.fintech.user.controller;

import com.example.fintech.user.dto.CreateUserRequest;
import com.example.fintech.user.dto.CreateUserResponse;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserResponse createUser(@RequestBody CreateUserRequest request) {
        User user = userService.createUser(request);

        return new CreateUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName()
        );
    }
}