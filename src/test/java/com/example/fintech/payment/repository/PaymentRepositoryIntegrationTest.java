package com.example.fintech.payment.repository;

import com.example.fintech.account.entity.Account;
import com.example.fintech.account.repository.AccountRepository;
import com.example.fintech.payment.entity.Payment;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PaymentRepositoryIntegrationTest {

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
    private PaymentRepository paymentRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindPayment() {
        User user = new User(
                "payment@test.com",
                "hashed-password",
                "Test",
                "User",
                "USER"
        );

        user = userRepository.save(user);

        Account sourceAccount = accountRepository.save(
                new Account(
                        user,
                        "PLN",
                        new BigDecimal("1000.00")
                )
        );

        Account destinationAccount = accountRepository.save(
                new Account(
                        user,
                        "PLN",
                        new BigDecimal("500.00")
                )
        );

        Payment payment = new Payment(
                sourceAccount,
                destinationAccount,
                new BigDecimal("100.00"),
                "PLN",
                "COMPLETED"
        );

        Payment saved = paymentRepository.save(payment);

        assertNotNull(saved.getId());

        Payment found = paymentRepository.findById(saved.getId())
                .orElseThrow();

        assertEquals(
                0,
                new BigDecimal("100.00").compareTo(found.getAmount())
        );
        assertEquals("PLN", found.getCurrency());
        assertEquals("COMPLETED", found.getStatus());
        assertEquals(
                sourceAccount.getId(),
                found.getSourceAccount().getId()
        );
        assertEquals(
                destinationAccount.getId(),
                found.getDestinationAccount().getId()
        );
    }
}