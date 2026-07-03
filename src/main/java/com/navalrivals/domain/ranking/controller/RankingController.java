package com.navalrivals.domain.ranking.controller;

import com.navalrivals.domain.ranking.dto.RankingResponse;
import com.navalrivals.domain.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping
    public ResponseEntity<Page<RankingResponse>> getAll(@PageableDefault(size = 20) Pageable pageable){
        var response = rankingService.get(pageable);
        return ResponseEntity.ok(response);
    }
}
