package com.example.fintech.security.jwt;

import com.example.fintech.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        String secret = Encoders.BASE64.encode(
                Jwts.SIG.HS256.key().build().getEncoded()
        );

        jwtService = new JwtService(secret, 900_000);
    }

    @Test
    void shouldGenerateValidToken() {
        UUID userId = UUID.randomUUID();

        User user = new User(
                "john@example.com",
                "hashed-password",
                "John",
                "Doe",
                "USER"
        );

        ReflectionTestUtils.setField(user, "id", userId);

        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void shouldGenerateTokenWithCorrectClaims() {
        UUID userId = UUID.randomUUID();

        User user = new User(
                "john@example.com",
                "hashed-password",
                "John",
                "Doe",
                "USER"
        );

        ReflectionTestUtils.setField(user, "id", userId);

        String token = jwtService.generateToken(user);

        Claims claims = jwtService.extractAllClaims(token);

        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("USER", claims.get("role"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void shouldRejectInvalidToken() {
        UUID userId = UUID.randomUUID();

        User user = new User(
                "john@example.com",
                "hashed-password",
                "John",
                "Doe",
                "USER"
        );

        ReflectionTestUtils.setField(user, "id", userId);

        String token = jwtService.generateToken(user);

        String tamperedToken =
                token.substring(0, token.length() - 1) + "x";

        assertThrows(
                Exception.class,
                () -> jwtService.extractAllClaims(tamperedToken)
        );
    }
}