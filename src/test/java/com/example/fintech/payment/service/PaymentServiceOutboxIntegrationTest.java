package com.example.fintech.payment.service;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.repository.AccountRepository;
import com.example.fintech.account.service.AccountService;
import com.example.fintech.kafka.event.PaymentCreatedEvent;
import com.example.fintech.payment.dto.CreatePaymentRequest;
import com.example.fintech.payment.entity.Payment;
import com.example.fintech.payment.outbox.entity.OutboxEvent;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PaymentServiceOutboxIntegrationTest {

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
    private PaymentRepository paymentRepository;

    @MockitoSpyBean
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        paymentRepository.deleteAll();
        outboxEventRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldSavePaymentAndOutboxEventInSameTransaction()
            throws Exception {

        User sourceUser = new User(
                "outbox-source@test.com",
                "passwordHash",
                "Source",
                "User",
                "USER"
        );

        User destinationUser = new User(
                "outbox-destination@test.com",
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

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        sourceAccount.getId(),
                        destinationAccount.getId(),
                        new BigDecimal("100.00"),
                        "PLN"
                );

        Payment payment =
                paymentService.createPayment(
                        sourceUser.getId(),
                        "test-key",
                        request
                );

        assertNotNull(payment.getId());

        assertEquals(
                1,
                paymentRepository.count()
        );

        List<OutboxEvent> events =
                outboxEventRepository
                        .findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

        assertEquals(1, events.size());

        OutboxEvent outboxEvent = events.getFirst();

        assertEquals(
                "PAYMENT_CREATED",
                outboxEvent.getEventType()
        );

        assertEquals(
                payment.getId(),
                outboxEvent.getAggregateId()
        );

        assertNull(outboxEvent.getPublishedAt());

        PaymentCreatedEvent event =
                objectMapper.readValue(
                        outboxEvent.getPayload(),
                        PaymentCreatedEvent.class
                );

        assertEquals(
                payment.getId(),
                event.paymentId()
        );

        assertEquals(
                sourceAccount.getId(),
                event.sourceAccountId()
        );

        assertEquals(
                destinationAccount.getId(),
                event.destinationAccountId()
        );

        assertEquals(
                new BigDecimal("100.00"),
                event.amount()
        );

        assertEquals(
                "PLN",
                event.currency()
        );
    }

    @Test
    void shouldRollbackPaymentWhenOutboxSaveFails() {
        User sourceUser = new User(
                "rollback-source@test.com",
                "passwordHash",
                "Source",
                "User",
                "USER"
        );

        User destinationUser = new User(
                "rollback-destination@test.com",
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

        CreatePaymentRequest request =
                new CreatePaymentRequest(
                        sourceAccount.getId(),
                        destinationAccount.getId(),
                        new BigDecimal("100.00"),
                        "PLN"
                );

        doAnswer(invocation -> {
            invocation.callRealMethod();

            throw new RuntimeException(
                    "Simulated Outbox failure"
            );
        }).when(outboxEventRepository)
                .save(any(OutboxEvent.class));

        assertThrows(
                RuntimeException.class,
                () -> paymentService.createPayment(
                        sourceUser.getId(),
                        "test-key",
                        request
                )
        );

        assertEquals(
                0,
                paymentRepository.count()
        );

        assertEquals(
                0,
                outboxEventRepository.count()
        );
    }

    @Test
    void shouldProcessPaymentOnlyOnceForSameIdempotencyKey() {
        User sourceUser = new User(
                "idempotency-source@test.com",
                "passwordHash",
                "Source",
                "User",
                "USER"
        );

        User destinationUser = new User(
                "idempotency-destination@test.com",
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

        CreatePaymentRequest request = new CreatePaymentRequest(
                sourceAccount.getId(),
                destinationAccount.getId(),
                new BigDecimal("100.00"),
                "PLN"
        );

        Payment firstPayment = paymentService.createPayment(
                sourceUser.getId(),
                "same-key",
                request
        );

        Payment secondPayment = paymentService.createPayment(
                sourceUser.getId(),
                "same-key",
                request
        );

        assertEquals(
                firstPayment.getId(),
                secondPayment.getId()
        );

        Account updatedSourceAccount =
                accountRepository.findById(sourceAccount.getId())
                        .orElseThrow();

        Account updatedDestinationAccount =
                accountRepository.findById(destinationAccount.getId())
                        .orElseThrow();

        assertEquals(
                new BigDecimal("900.00"),
                updatedSourceAccount.getBalance()
                        .setScale(2)
        );

        assertEquals(
                new BigDecimal("600.00"),
                updatedDestinationAccount.getBalance()
                        .setScale(2)
        );

        assertEquals(1, paymentRepository.count());
        assertEquals(1, outboxEventRepository.count());
    }
}