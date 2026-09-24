package com.example.fintech.account.repository;

import com.example.fintech.account.entity.Account;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AccountRepositoryIntegrationTest {

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
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindAccount() {
        User user = new User(
                "account@test.com",
                "hashed-password",
                "Test",
                "User",
                "USER"
        );

        user = userRepository.save(user);

        Account account = new Account(
                user,
                "PLN",
                new BigDecimal("1500.2500")
        );

        Account saved = accountRepository.save(account);

        assertNotNull(saved.getId());

        Account found = accountRepository.findById(saved.getId())
                .orElseThrow();

        assertEquals("PLN", found.getCurrency());
        assertEquals(
                new BigDecimal("1500.2500"),
                found.getBalance()
        );
        assertEquals(user.getId(), found.getUser().getId());
    }

    @Test
    void shouldFindAllAccountsByUserId() {
        User user = new User(
                "accounts@test.com",
                "hashed-password",
                "Test",
                "User",
                "USER"
        );

        user = userRepository.save(user);

        Account pln = accountRepository.save(
                new Account(
                        user,
                        "PLN",
                        new BigDecimal("1000.00")
                )
        );

        Account eur = accountRepository.save(
                new Account(
                        user,
                        "EUR",
                        new BigDecimal("500.00")
                )
        );

        List<Account> accounts =
                accountRepository.findAllByUserId(user.getId());

        assertEquals(2, accounts.size());
        assertTrue(accounts.stream()
                .anyMatch(account -> account.getId().equals(pln.getId())));
        assertTrue(accounts.stream()
                .anyMatch(account -> account.getId().equals(eur.getId())));
    }
}