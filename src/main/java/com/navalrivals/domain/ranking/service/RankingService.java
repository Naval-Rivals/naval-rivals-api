package com.navalrivals.domain.ranking.service;

import com.navalrivals.domain.ranking.dto.RankingProjection;
import com.navalrivals.domain.ranking.dto.RankingResponse;
import com.navalrivals.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final UserRepository userRepository;

    public Page<RankingResponse> get(Pageable pageable) {
        return userRepository.findRanking(pageable)
                .map(p -> new RankingResponse(
                        p.getPosition(),
                        p.getUserId(),
                        p.getNickname(),
                        p.getVictories(),
                        p.getTotalGames(),
                        p.getWinRate()
                ));
    }
}
