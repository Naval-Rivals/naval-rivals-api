package com.navalrivals.infra.security.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.infra.exception.exceptions.TokenJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class TokenService {

    private static final long TOKEN_EXPIRATION_SECONDS = 518400; // 72 horas

    @Value("${api.security.token.secret}")
    private String secret;

    public String generateToken(User user){
        try{
            Algorithm algorithm = Algorithm.HMAC256(secret);

            String token = JWT.create()
                    .withIssuer("naval-rivals-api")
                    .withSubject(user.getEmail())
                    .withExpiresAt(Instant.now().plusSeconds(TOKEN_EXPIRATION_SECONDS))
                    .sign(algorithm);

            log.debug("[TOKEN] Token gerado para userId={}", user.getId());
            return token;
        }catch (JWTCreationException e){
            log.error("[TOKEN] Falha ao gerar token para userId={}: {}", user.getId(), e.getMessage());
            throw new TokenJwtException("Erro ao criar o Token JWT");
        }
    }

    public String validateToken(String token){
        try{
            Algorithm algorithm = Algorithm.HMAC256(secret);

            return JWT.require(algorithm)
                    .withIssuer("naval-rivals-api")
                    .build()
                    .verify(token)
                    .getSubject();
        }catch (JWTVerificationException e){
            log.warn("[TOKEN] Token inválido: {}", e.getMessage());
            throw new TokenJwtException("Erro ao validar o Token JWT");
        }
    }
}
