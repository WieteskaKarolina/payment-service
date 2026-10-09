package com.example.fintech.account.service;

import com.example.fintech.account.dto.AccountResponse;
import com.example.fintech.account.entity.Account;
import com.example.fintech.account.repository.AccountRepository;
import com.example.fintech.user.entity.User;
import com.example.fintech.user.repository.UserRepository;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
class AccountCacheIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private AccountRepository accountRepository;

    @Autowired
    private CacheManager cacheManager;

    @Test
    @Order(1)
    void cacheMissLoadsPostgresAndSecondReadUsesRedis() {
        User user = createUser();
        accountRepository.save(new Account(user, "PLN", new BigDecimal("120.00")));
        clearInvocations(accountRepository);

        List<AccountResponse> firstRead = accountService.getAccountsForUser(user.getId());
        assertEquals("RedisCacheManager", cacheManager.getClass().getSimpleName());
        assertEquals(firstRead, cacheManager.getCache("accounts").get(user.getId()).get());
        List<AccountResponse> secondRead = accountService.getAccountsForUser(user.getId());

        assertEquals(firstRead, secondRead);
        assertEquals(1, firstRead.size());
        verify(accountRepository, times(1)).findAllByUserId(user.getId());
    }

    @Test
    @Order(2)
    void creatingAccountEvictsThatUsersCachedAccountList() {
        User user = createUser();

        assertEquals(List.of(), accountService.getAccountsForUser(user.getId()));
        clearInvocations(accountRepository);

        accountService.createAccount(user.getId(), "EUR");
        List<AccountResponse> refreshed = accountService.getAccountsForUser(user.getId());

        assertEquals(1, refreshed.size());
        assertEquals("EUR", refreshed.getFirst().currency());
        verify(accountRepository, times(1)).findAllByUserId(user.getId());
    }

    @Test
    @Order(3)
    void redisUnavailableFallsBackToPostgres() {
        User user = createUser();
        accountRepository.save(new Account(user, "PLN", new BigDecimal("40.00")));
        accountService.getAccountsForUser(user.getId());
        clearInvocations(accountRepository);

        redis.stop();

        List<AccountResponse> result = accountService.getAccountsForUser(user.getId());

        assertEquals(1, result.size());
        assertEquals("PLN", result.getFirst().currency());
        verify(accountRepository, times(1)).findAllByUserId(user.getId());
    }

    private User createUser() {
        String unique = UUID.randomUUID() + "@cache-test.com";
        return userRepository.save(new User(unique, "hash", "Cache", "Test", "USER"));
    }
}
