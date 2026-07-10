package com.navalrivals.domain.ranking.service;

import com.navalrivals.domain.ranking.dto.RankingProjection;
import com.navalrivals.domain.ranking.dto.RankingResponse;
import com.navalrivals.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RankingService rankingService;

    @Test
    @DisplayName("get - should return page of RankingResponses mapped from projections")
    void get_shouldReturnPageOfRankingResponses() {
        Pageable pageable = PageRequest.of(0, 10);

        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        RankingProjection projection1 = mock(RankingProjection.class);
        when(projection1.getPosition()).thenReturn(1L);
        when(projection1.getUserId()).thenReturn(userId1);
        when(projection1.getNickname()).thenReturn("Player1");
        when(projection1.getVictories()).thenReturn(10);
        when(projection1.getTotalGames()).thenReturn(15);
        when(projection1.getWinRate()).thenReturn("66.67%");

        RankingProjection projection2 = mock(RankingProjection.class);
        when(projection2.getPosition()).thenReturn(2L);
        when(projection2.getUserId()).thenReturn(userId2);
        when(projection2.getNickname()).thenReturn("Player2");
        when(projection2.getVictories()).thenReturn(8);
        when(projection2.getTotalGames()).thenReturn(20);
        when(projection2.getWinRate()).thenReturn("40.00%");

        List<RankingProjection> projections = List.of(projection1, projection2);
        Page<RankingProjection> projectionPage = new PageImpl<>(projections, pageable, 2);

        when(userRepository.findRanking(pageable)).thenReturn(projectionPage);

        Page<RankingResponse> result = rankingService.get(pageable);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());

        RankingResponse response1 = result.getContent().get(0);
        assertEquals(1L, response1.position());
        assertEquals(userId1, response1.userId());
        assertEquals("Player1", response1.nickname());
        assertEquals(10, response1.victories());
        assertEquals(15, response1.totalGames());
        assertEquals("66.67%", response1.winRate());

        RankingResponse response2 = result.getContent().get(1);
        assertEquals(2L, response2.position());
        assertEquals(userId2, response2.userId());
        assertEquals("Player2", response2.nickname());
        assertEquals(8, response2.victories());
        assertEquals(20, response2.totalGames());
        assertEquals("40.00%", response2.winRate());

        verify(userRepository).findRanking(pageable);
    }

    @Test
    @DisplayName("get - empty page should return empty page")
    void get_emptyPage_shouldReturnEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);

        Page<RankingProjection> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(userRepository.findRanking(pageable)).thenReturn(emptyPage);

        Page<RankingResponse> result = rankingService.get(pageable);

        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());

        verify(userRepository).findRanking(pageable);
    }

    @Test
    @DisplayName("get - should pass pageable correctly to repository")
    void get_shouldPassPageableToRepository() {
        Pageable pageable = PageRequest.of(2, 5, Sort.by("victories").descending());

        Page<RankingProjection> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(userRepository.findRanking(pageable)).thenReturn(emptyPage);

        rankingService.get(pageable);

        verify(userRepository, times(1)).findRanking(pageable);
        verifyNoMoreInteractions(userRepository);
    }
}
