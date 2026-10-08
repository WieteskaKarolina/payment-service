package com.example.fintech.payment.controller;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.repository.AccountRepository;
import com.example.fintech.payment.repository.PaymentRepository;
import com.example.fintech.payment.service.PaymentService;
import com.example.fintech.payment.dto.CreatePaymentRequest;
import com.example.fintech.payment.outbox.repository.OutboxEventRepository;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.repository.UserRepository;
import com.example.fintech.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
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

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

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
                                .header("Idempotency-Key", "test-key-create")
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
                                .header("Idempotency-Key", "test-key-insufficient")
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
                                .header("Idempotency-Key", "test-key-missing-destination")
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

    @Test
    void shouldReturnConflictForChangedRetryEvenWhenNewDestinationIsMissing() throws Exception {
        User sourceUser = userRepository.save(new User(
                "changed-retry-source@test.com", "passwordHash", "Source", "User", "USER"
        ));
        User destinationUser = userRepository.save(new User(
                "changed-retry-destination@test.com", "passwordHash", "Destination", "User", "USER"
        ));
        Account source = accountRepository.save(new Account(
                sourceUser, "PLN", new BigDecimal("1000.00")
        ));
        Account destination = accountRepository.save(new Account(
                destinationUser, "PLN", new BigDecimal("500.00")
        ));
        String token = jwtService.generateToken(sourceUser);
        String key = "changed-retry-" + UUID.randomUUID();
        String original = paymentJson(source.getId(), destination.getId());

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(original))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType("application/json")
                        .content(paymentJson(source.getId(), UUID.randomUUID())))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldAllowDepositOnlyToAuthenticatedUsersOwnAccount() throws Exception {
        User owner = userRepository.save(new User(
                "deposit-owner-" + UUID.randomUUID() + "@test.com", "hash", "Owner", "User", "USER"
        ));
        User other = userRepository.save(new User(
                "deposit-other-" + UUID.randomUUID() + "@test.com", "hash", "Other", "User", "USER"
        ));
        Account owned = accountRepository.save(new Account(
                owner, "PLN", new BigDecimal("100.00")
        ));
        Account otherAccount = accountRepository.save(new Account(
                other, "PLN", new BigDecimal("100.00")
        ));

        mockMvc.perform(post("/api/accounts/{accountId}/deposit", owned.getId())
                        .header("Authorization", "Bearer " + jwtService.generateToken(owner))
                        .contentType("application/json")
                        .content("{\"amount\":25.00}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/accounts/{accountId}/deposit", otherAccount.getId())
                        .header("Authorization", "Bearer " + jwtService.generateToken(owner))
                        .contentType("application/json")
                        .content("{\"amount\":25.00}"))
                .andExpect(status().isForbidden());

        assertEquals(0, new BigDecimal("125.00").compareTo(
                accountRepository.findById(owned.getId()).orElseThrow().getBalance()));
        assertEquals(0, new BigDecimal("100.00").compareTo(
                accountRepository.findById(otherAccount.getId()).orElseThrow().getBalance()));
    }

    @Test
    void shouldProcessConcurrentIdenticalRetriesOnlyOnce() throws Exception {
        User sourceUser = userRepository.save(new User(
                "concurrent-source-" + UUID.randomUUID() + "@test.com", "hash", "Source", "User", "USER"
        ));
        User destinationUser = userRepository.save(new User(
                "concurrent-destination-" + UUID.randomUUID() + "@test.com", "hash", "Destination", "User", "USER"
        ));
        Account source = accountRepository.save(new Account(
                sourceUser, "PLN", new BigDecimal("1000.00")
        ));
        Account destination = accountRepository.save(new Account(
                destinationUser, "PLN", new BigDecimal("500.00")
        ));
        CreatePaymentRequest request = new CreatePaymentRequest(
                source.getId(), destination.getId(), new BigDecimal("100.00"), "PLN"
        );
        String key = "concurrent-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<com.example.fintech.payment.entity.Payment> first = executor.submit(() -> {
                start.await();
                return paymentService.createPayment(sourceUser.getId(), key, request);
            });
            Future<com.example.fintech.payment.entity.Payment> second = executor.submit(() -> {
                start.await();
                return paymentService.createPayment(sourceUser.getId(), key, request);
            });
            start.countDown();
            var firstResult = first.get(15, TimeUnit.SECONDS);
            var secondResult = second.get(15, TimeUnit.SECONDS);

            assertEquals(firstResult.getId(), secondResult.getId());
            assertEquals(1, paymentRepository.findAll().stream()
                    .filter(payment -> payment.getSourceAccount().getId().equals(source.getId()))
                    .filter(payment -> key.equals(payment.getIdempotencyKey()))
                    .count());
            assertEquals(1, outboxEventRepository.findAll().stream()
                    .filter(event -> firstResult.getId().equals(event.getAggregateId()))
                    .count());
            assertEquals(0, new BigDecimal("900.00").compareTo(
                    accountRepository.findById(source.getId()).orElseThrow().getBalance()));
        } finally {
            executor.shutdownNow();
        }
    }

    private static String paymentJson(UUID sourceId, UUID destinationId) {
        return """
                {"sourceAccountId":"%s","destinationAccountId":"%s","amount":100.00,"currency":"PLN"}
                """.formatted(sourceId, destinationId);
    }
}
