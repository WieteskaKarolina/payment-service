package com.example.fintech.payment.service;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.repository.AccountRepository;
import com.example.fintech.payment.dto.CreatePaymentRequest;
import com.example.fintech.payment.outbox.repository.OutboxEventRepository;
import com.example.fintech.payment.repository.PaymentRepository;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PaymentServiceSameKeyConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanDatabase() {
        paymentRepository.deleteAll();
        outboxEventRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldReturnSamePaymentForConcurrentRequestsWithSameKey()
            throws Exception {

        User sourceUser = userRepository.save(new User(
                "race-source@test.com",
                "passwordHash",
                "Source",
                "User",
                "USER"
        ));

        User destinationUser = userRepository.save(new User(
                "race-destination@test.com",
                "passwordHash",
                "Destination",
                "User",
                "USER"
        ));

        Account sourceAccount = accountRepository.save(new Account(
                sourceUser,
                "PLN",
                new BigDecimal("1000.00")
        ));

        Account destinationAccount = accountRepository.save(new Account(
                destinationUser,
                "PLN",
                new BigDecimal("0.00")
        ));

        CreatePaymentRequest request = new CreatePaymentRequest(
                sourceAccount.getId(),
                destinationAccount.getId(),
                new BigDecimal("100.00"),
                "PLN"
        );

        int threads = 8;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<Throwable>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            results.add(executor.submit(() -> {
                startLatch.await();
                try {
                    transactionTemplate.executeWithoutResult(status ->
                            paymentService.createPayment(
                                    sourceUser.getId(),
                                    "same-race-key",
                                    request
                            )
                    );
                    return null;
                } catch (Throwable throwable) {
                    return throwable;
                }
            }));
        }

        startLatch.countDown();

        List<Throwable> failures = new ArrayList<>();
        for (Future<Throwable> result : results) {
            Throwable failure = result.get(30, TimeUnit.SECONDS);
            if (failure != null) {
                failures.add(failure);
            }
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        System.out.println("=== THREADS: " + threads + " ===");
        System.out.println("=== FAILURES: " + failures.size() + " ===");
        failures.forEach(failure ->
                System.out.println("=== FAILURE: " + failure
                        + " | cause: " + failure.getCause()));

        System.out.println("=== PAYMENTS: "
                + paymentRepository.count() + " ===");
        System.out.println("=== OUTBOX: "
                + outboxEventRepository.count() + " ===");

        assertEquals(
                0,
                failures.size(),
                "expected no failures, got: " + failures
        );
        assertEquals(1, paymentRepository.count());
        assertEquals(1, outboxEventRepository.count());
    }
}