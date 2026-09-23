package com.example.fintech.user.controller;

import com.example.fintech.user.dto.CreateUserRequest;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.exception.UserAlreadyExistsException;
import com.example.fintech.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;


    @Test
    void shouldCreateUser() throws Exception {

        User user = new User(
                "john@example.com",
                "hashed-password",
                "John",
                "Doe",
                "USER"
        );

        when(userService.createUser(any(CreateUserRequest.class)))
                .thenReturn(user);

        mockMvc.perform(
                        post("/api/users")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "john@example.com",
                                            "password": "Password123!",
                                            "firstName": "John",
                                            "lastName": "Doe"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(userService).createUser(any(CreateUserRequest.class));
    }


    @Test
    void shouldRejectInvalidRequest() throws Exception {

        mockMvc.perform(
                        post("/api/users")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "not-an-email",
                                            "password": "weak",
                                            "firstName": "",
                                            "lastName": "Doe"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }


    @Test
    void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {

        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(
                        new UserAlreadyExistsException(
                                "User with this email already exists"
                        )
                );

        mockMvc.perform(
                        post("/api/users")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "john@example.com",
                                            "password": "Password123!",
                                            "firstName": "John",
                                            "lastName": "Doe"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message")
                        .value("User with this email already exists"));

        verify(userService).createUser(any(CreateUserRequest.class));
    }
}