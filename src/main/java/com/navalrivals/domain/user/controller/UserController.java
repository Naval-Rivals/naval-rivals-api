package com.navalrivals.domain.user.controller;

import com.navalrivals.domain.game.dto.MatchHistoryResponse;
import com.navalrivals.domain.game.repository.GameResultRepository;
import com.navalrivals.domain.user.dto.UpdateNicknameRequest;
import com.navalrivals.domain.user.dto.UpdatePasswordRequest;
import com.navalrivals.domain.user.dto.UserResponse;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final GameResultRepository gameResultRepository;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal User user){
        var response = userService.getById(user.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/matches")
    public ResponseEntity<Page<MatchHistoryResponse>> myMatches(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        var pageable = PageRequest.of(page, size);
        var results = gameResultRepository.findByUserId(user.getId(), pageable)
                .map(result -> MatchHistoryResponse.from(result, user.getId()));
        return ResponseEntity.ok(results);
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

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @RequestBody @Valid UpdatePasswordRequest requestBody,
            @AuthenticationPrincipal User user
    ){
        userService.changePassword(requestBody, user);
        return ResponseEntity.noContent().build();
    }
}
