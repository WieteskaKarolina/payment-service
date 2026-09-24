package com.example.fintech.payment.service;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.repository.AccountRepository;
import com.example.fintech.payment.dto.CreatePaymentRequest;
import com.example.fintech.payment.exception.InsufficientBalanceException;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.repository.UserRepository;
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
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PaymentServiceConcurrencyIntegrationTest {

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
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void shouldPreventConcurrentTransfersFromSpendingSameBalance()
            throws Exception {

        // Arrange

        User sourceUser = new User(
                "source@test.com",
                "password",
                "Source",
                "User",
                "USER"
        );

        User destinationUser = new User(
                "destination@test.com",
                "password",
                "Destination",
                "User",
                "USER"
        );

        sourceUser = userRepository.save(sourceUser);
        destinationUser = userRepository.save(destinationUser);

        Account sourceAccount = new Account(
                sourceUser,
                "PLN",
                new BigDecimal("100.00")
        );

        Account destinationAccount = new Account(
                destinationUser,
                "PLN",
                BigDecimal.ZERO
        );

        sourceAccount = accountRepository.save(sourceAccount);
        destinationAccount = accountRepository.save(destinationAccount);

        UUID sourceUserId = sourceUser.getId();
        UUID destinationAccountId = destinationAccount.getId();

        CreatePaymentRequest request = new CreatePaymentRequest(
                destinationAccountId,
                new BigDecimal("100.00"),
                "PLN"
        );

        // Two transactions start at approximately the same time.
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch startLatch = new CountDownLatch(1);

        Future<?> firstTransfer = executor.submit(() -> {
            await(startLatch);

            transactionTemplate.executeWithoutResult(
                    status -> paymentService.createPayment(
                            sourceUserId,
                            request
                    )
            );
        });

        Future<?> secondTransfer = executor.submit(() -> {
            await(startLatch);

            transactionTemplate.executeWithoutResult(
                    status -> paymentService.createPayment(
                            sourceUserId,
                            request
                    )
            );
        });

        // Act
        startLatch.countDown();

        int successfulTransfers = 0;
        int rejectedTransfers = 0;

        for (Future<?> transfer : new Future<?>[]{
                firstTransfer,
                secondTransfer
        }) {
            try {
                transfer.get();
                successfulTransfers++;
            } catch (ExecutionException exception) {
                if (exception.getCause()
                        instanceof InsufficientBalanceException) {
                    rejectedTransfers++;
                } else {
                    throw exception;
                }
            }
        }

        executor.shutdown();

        assertTrue(
                executor.awaitTermination(10, TimeUnit.SECONDS)
        );

        // Assert

        assertEquals(1, successfulTransfers);
        assertEquals(1, rejectedTransfers);

        Account finalSourceAccount =
                accountRepository.findById(sourceAccount.getId())
                        .orElseThrow();

        Account finalDestinationAccount =
                accountRepository.findById(destinationAccount.getId())
                        .orElseThrow();

        assertEquals(
                0,
                finalSourceAccount.getBalance()
                        .compareTo(new BigDecimal("0.00"))
        );

        assertEquals(
                0,
                finalDestinationAccount.getBalance()
                        .compareTo(new BigDecimal("100.00"))
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }
}