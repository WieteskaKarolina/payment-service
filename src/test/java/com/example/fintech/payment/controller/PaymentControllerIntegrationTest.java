package com.example.fintech.payment.controller;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.repository.AccountRepository;
import com.example.fintech.payment.repository.PaymentRepository;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.repository.UserRepository;
import com.example.fintech.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PaymentControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldCreatePaymentWithValidJwt() throws Exception {

        User sourceUser = new User(
                "source@test.com",
                "passwordHash",
                "Source",
                "User",
                "USER"
        );

        User destinationUser = new User(
                "destination@test.com",
                "passwordHash",
                "Destination",
                "User",
                "USER"
        );

        userRepository.save(sourceUser);
        userRepository.save(destinationUser);

        Account sourceAccount = new Account(
                sourceUser,
                "PLN",
                new BigDecimal("1000.00")
        );

        Account destinationAccount = new Account(
                destinationUser,
                "PLN",
                new BigDecimal("500.00")
        );

        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        String token = jwtService.generateToken(sourceUser);

        mockMvc.perform(
                        post("/api/payments")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType("application/json")
                                .content("""
                                        {
                                            "sourceAccountId": "%s",
                                            "destinationAccountId": "%s",
                                            "amount": 100.00,
                                            "currency": "PLN"
                                        }
                                        """.formatted(
                                        sourceAccount.getId(),
                                        destinationAccount.getId()
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceAccountId")
                        .value(sourceAccount.getId().toString()))
                .andExpect(jsonPath("$.destinationAccountId")
                        .value(destinationAccount.getId().toString()))
                .andExpect(jsonPath("$.amount")
                        .value(100))
                .andExpect(jsonPath("$.currency")
                        .value("PLN"))
                .andExpect(jsonPath("$.status")
                        .value("COMPLETED"));
    }

    @Test
    void shouldRejectPaymentWithoutJwt() throws Exception {

        mockMvc.perform(
                        post("/api/payments")
                                .contentType("application/json")
                                .content("""
                                    {
                                        "destinationAccountId": "%s",
                                        "amount": 100.00,
                                        "currency": "PLN"
                                    }
                                    """.formatted(UUID.randomUUID()))
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectPaymentWhenBalanceIsInsufficient()
            throws Exception {

        User sourceUser = new User(
                "poor-source@test.com",
                "passwordHash",
                "Poor",
                "Source",
                "USER"
        );

        User destinationUser = new User(
                "rich-destination@test.com",
                "passwordHash",
                "Rich",
                "Destination",
                "USER"
        );

        userRepository.save(sourceUser);
        userRepository.save(destinationUser);

        Account sourceAccount = new Account(
                sourceUser,
                "PLN",
                new BigDecimal("50.00")
        );

        Account destinationAccount = new Account(
                destinationUser,
                "PLN",
                new BigDecimal("500.00")
        );

        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        String token = jwtService.generateToken(sourceUser);

        mockMvc.perform(
                        post("/api/payments")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType("application/json")
                                .content("""
                                    {
                                        "sourceAccountId": "%s",
                                        "destinationAccountId": "%s",
                                        "amount": 100.00,
                                        "currency": "PLN"
                                    }
                                    """.formatted(
                                        sourceAccount.getId(),
                                        destinationAccount.getId()
                                ))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectPaymentWhenDestinationAccountDoesNotExist()
            throws Exception {

        User sourceUser = new User(
                "missing-destination-source@test.com",
                "passwordHash",
                "Source",
                "User",
                "USER"
        );

        userRepository.save(sourceUser);

        Account sourceAccount = new Account(
                sourceUser,
                "PLN",
                new BigDecimal("1000.00")
        );

        accountRepository.save(sourceAccount);

        String token = jwtService.generateToken(sourceUser);

        UUID nonExistingAccountId = UUID.randomUUID();

        mockMvc.perform(
                        post("/api/payments")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType("application/json")
                                .content("""
                                    {
                                        "sourceAccountId": "%s",
                                        "destinationAccountId": "%s",
                                        "amount": 100.00,
                                        "currency": "PLN"
                                    }
                                    """.formatted(
                                        sourceAccount.getId(),
                                        nonExistingAccountId
                                ))
                )
                .andExpect(status().isNotFound());
    }
}