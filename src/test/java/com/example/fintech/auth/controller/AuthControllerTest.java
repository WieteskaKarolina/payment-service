package com.example.fintech.auth.controller;

import com.example.fintech.auth.dto.LoginRequest;
import com.example.fintech.auth.dto.LoginResponse;
import com.example.fintech.auth.exception.InvalidCredentialsException;
import com.example.fintech.auth.service.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationService authenticationService;

    @Test
    void shouldLoginSuccessfully() throws Exception {

        when(authenticationService.authenticate(any(LoginRequest.class)))
                .thenReturn(
                        new LoginResponse(
                                "test-jwt-token",
                                "Bearer"
                        )
                );

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "john@example.com",
                                            "password": "Password123!"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken")
                        .value("test-jwt-token"))
                .andExpect(jsonPath("$.tokenType")
                        .value("Bearer"));

        verify(authenticationService)
                .authenticate(any(LoginRequest.class));
    }

    @Test
    void shouldRejectInvalidRequest() throws Exception {

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "not-an-email",
                                            "password": ""
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authenticationService);
    }

    @Test
    void shouldReturnUnauthorizedForInvalidCredentials() throws Exception {

        when(authenticationService.authenticate(any(LoginRequest.class)))
                .thenThrow(
                        new InvalidCredentialsException(
                                "Invalid email or password"
                        )
                );

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content("""
                                        {
                                            "email": "john@example.com",
                                            "password": "WrongPassword123!"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Invalid email or password"));

        verify(authenticationService)
                .authenticate(any(LoginRequest.class));
    }
}