package com.navalrivals.domain.user.controller;

import com.navalrivals.domain.user.dto.LoginUserRequest;
import com.navalrivals.domain.user.dto.RegisterUserRequest;
import com.navalrivals.domain.user.service.UserService;
import com.navalrivals.infra.security.dto.AuthResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @RequestBody @Valid RegisterUserRequest requestBody,
            UriComponentsBuilder uriBuilder
    ){
        log.info("[AUTH] Tentativa de registro — email={}, nickname={}", requestBody.email(), requestBody.nickname());
        var response = userService.register(requestBody);
        log.info("[AUTH] Registro bem-sucedido — userId={}, nickname={}", response.id(), response.nickname());
        var uri = uriBuilder.path("users/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginUserRequest requestBody){
        log.info("[AUTH] Tentativa de login — login={}", requestBody.login());
        var response = userService.login(requestBody);
        log.info("[AUTH] Login bem-sucedido — userId={}, nickname={}", response.id(), response.nickname());
        return ResponseEntity.ok(response);
    }


}
