package com.navalrivals.infra.security.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.navalrivals.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private TokenService tokenService;
    private String secret;

    @BeforeEach
    void setup(){
        tokenService = new TokenService();
        secret = "test-secret-token";
        ReflectionTestUtils.setField(tokenService, "secret", secret);
    }

    @Test
    @DisplayName("Deve validar corretamente o token JWT")
    void testValidateTokenSuccess(){
        Algorithm algorithm = Algorithm.HMAC256(secret);

        String token = JWT.create()
                .withIssuer("naval-rivals-api")
                .withSubject("Teste")
                .sign(algorithm);

        String subject = tokenService.validateToken(token);

        assertEquals("Teste", subject);
    }

    @Test
    @DisplayName("Deve gerar corretamente um token JWT")
    void testGenerateTokenSuccess(){

        User user = new User();
        String token = tokenService.generateToken(user);

        assertNotNull(token, "Token não deve ser nulo");
    }

}