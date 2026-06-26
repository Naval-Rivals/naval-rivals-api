package com.navalrivals.domain.user.controller;

import com.navalrivals.domain.user.dto.RegisterUserRequest;
import com.navalrivals.domain.user.service.UserService;
import com.navalrivals.infra.security.dto.AuthResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

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
        var response = userService.register(requestBody);
        var uri = uriBuilder.path("users/{id}").buildAndExpand(response.user().id()).toUri();
        return ResponseEntity.created(uri).body(response);
    }


}
