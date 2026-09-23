package com.example.fintech.user.repository;

import com.example.fintech.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import lombok.RequiredArgsConstructor;

@SpringBootTest
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class UserRepositoryIntegrationTest {

    private final UserRepository userRepository;

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void shouldSaveAndFindUserById() {
        User user = new User(
                "karolina@example.com",
                "hashed-password",
                "Karolina",
                "Wieteska",
                "USER"
        );

        User savedUser = userRepository.save(user);

        Optional<User> foundUser =
                userRepository.findById(savedUser.getId());

        assertTrue(foundUser.isPresent());
        assertEquals("karolina@example.com", foundUser.get().getEmail());
        assertEquals("Karolina", foundUser.get().getFirstName());
    }

    @Test
    void shouldRejectDuplicateEmail() {
        User firstUser = new User(
                "duplicate@example.com",
                "hash1",
                "John",
                "Doe",
                "USER"
        );

        User secondUser = new User(
                "duplicate@example.com",
                "hash2",
                "Jane",
                "Doe",
                "USER"
        );

        userRepository.saveAndFlush(firstUser);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(secondUser)
        );
    }

    @Test
    void shouldReturnEmptyWhenUserDoesNotExist() {
        UUID id = UUID.randomUUID();

        Optional<User> result = userRepository.findById(id);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindUserByEmail() {
        User user = new User(
                "find@example.com",
                "hashed-password",
                "Karolina",
                "Wieteska",
                "USER"
        );

        userRepository.save(user);

        Optional<User> result =
                userRepository.findByEmail("find@example.com");

        assertTrue(result.isPresent());
        assertEquals("find@example.com", result.get().getEmail());
    }

    @Test
    void shouldReturnEmptyWhenEmailDoesNotExist() {
        Optional<User> result =
                userRepository.findByEmail("missing@example.com");

        assertTrue(result.isEmpty());
    }
}