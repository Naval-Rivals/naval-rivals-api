package com.navalrivals.domain.user.controller;

import com.navalrivals.domain.user.dto.UpdateNicknameRequest;
import com.navalrivals.domain.user.dto.UserResponse;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal User user){
        return ResponseEntity.ok(new UserResponse(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id){
        var response = userService.getById(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me/nickname")
    public ResponseEntity<UserResponse> changeNickname(
            @RequestBody @Valid UpdateNicknameRequest requestBody,
            @AuthenticationPrincipal User user
    ){
        var response = userService.changeNickname(requestBody, user);
        return ResponseEntity.ok(response);
    }
}
