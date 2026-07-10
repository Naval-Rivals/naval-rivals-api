package com.navalrivals.infra.security.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.infra.exception.exceptions.TokenJwtException;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {

    private TokenService tokenService;
    private User user;

    private static final String SECRET = "test-secret-key";
    private static final String USER_EMAIL = "player@navalrivals.com";

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setNickname("Captain");
        user.setEmail(USER_EMAIL);
        user.setPassword("encoded-password");
    }

    @Test
    @DisplayName("generateToken - should return a valid non-null JWT")
    void generateToken_shouldReturnValidJwt() {
        String token = tokenService.generateToken(user);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    @DisplayName("generateToken - should contain correct subject (email)")
    void generateToken_shouldContainCorrectSubject() {
        String token = tokenService.generateToken(user);

        String subject = JWT.require(Algorithm.HMAC256(SECRET))
                .withIssuer("naval-rivals-api")
                .build()
                .verify(token)
                .getSubject();

        assertEquals(USER_EMAIL, subject);
    }

    @Test
    @DisplayName("validateToken - valid token should return email")
    void validateToken_validToken_shouldReturnEmail() {
        String token = tokenService.generateToken(user);

        String result = tokenService.validateToken(token);

        assertEquals(USER_EMAIL, result);
    }

    @Test
    @DisplayName("validateToken - invalid token should throw TokenJwtException")
    void validateToken_invalidToken_shouldThrowException() {
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.payload";

        assertThrows(TokenJwtException.class, () -> tokenService.validateToken(invalidToken));
    }

    @Test
    @DisplayName("validateToken - expired token should throw TokenJwtException")
    void validateToken_expiredToken_shouldThrowException() {
        String expiredToken = JWT.create()
                .withIssuer("naval-rivals-api")
                .withSubject(USER_EMAIL)
                .withExpiresAt(Instant.now().minusSeconds(3600))
                .sign(Algorithm.HMAC256(SECRET));

        assertThrows(TokenJwtException.class, () -> tokenService.validateToken(expiredToken));
    }

    @Test
    @DisplayName("validateToken - token with wrong issuer should throw TokenJwtException")
    void validateToken_wrongIssuer_shouldThrowException() {
        String wrongIssuerToken = JWT.create()
                .withIssuer("wrong-issuer")
                .withSubject(USER_EMAIL)
                .withExpiresAt(Instant.now().plusSeconds(7200))
                .sign(Algorithm.HMAC256(SECRET));

        assertThrows(TokenJwtException.class, () -> tokenService.validateToken(wrongIssuerToken));
    }
}
