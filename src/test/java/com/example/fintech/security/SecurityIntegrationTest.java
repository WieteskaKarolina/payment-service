package com.example.fintech.security;

import com.example.fintech.security.jwt.JwtService;
import com.example.fintech.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

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
    void shouldRejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/admin"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectUserFromAdminEndpoint() throws Exception {

        User user = createUser("USER");

        String token = jwtService.generateToken(user);

        mockMvc.perform(
                        get("/api/users/admin")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminToAccessAdminEndpoint() throws Exception {

        User admin = createUser("ADMIN");

        String token = jwtService.generateToken(admin);

        mockMvc.perform(
                        get("/api/users/admin")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(content().string("Admin access granted"));
    }

    private User createUser(String role) {
        User user = new User(
                "admin@test.com",
                "password",
                "Admin",
                "User",
                role
        );

        user.setId(UUID.randomUUID());

        return user;
    }
}