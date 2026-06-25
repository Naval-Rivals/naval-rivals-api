package com.navalrivals.infra.security.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.infra.exception.exceptions.TokenJwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    @Value("${api.security.token.secret}")
    private String secret;

    public String generateToken(User user){
        try{
            Algorithm algorithm = Algorithm.HMAC256(secret);

            return JWT.create()
                    .withIssuer("naval-rivals-api")
                    .withSubject(user.getEmail())
                    .sign(algorithm);
        }catch (JWTCreationException e){
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
            throw new TokenJwtException("Erro ao validar o Token JWT");
        }
    }
}
