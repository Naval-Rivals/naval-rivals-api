package com.navalrivals.domain.game.service;

import com.navalrivals.domain.board.entity.Board;
import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.entity.GameResult;
import com.navalrivals.domain.game.enums.GameMode;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.repository.GameResultRepository;
import com.navalrivals.domain.room.entity.Room;
import com.navalrivals.domain.room.repository.RoomRepository;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.shot.entity.Shot;
import com.navalrivals.domain.stats.entity.Stats;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.domain.user.repository.UserRepository;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameResultServiceTest {

    @Mock
    private GameResultRepository gameResultRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GameResultService gameResultService;

    private UUID gameId;
    private UUID winnerId;
    private UUID loserId;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        winnerId = UUID.randomUUID();
        loserId = UUID.randomUUID();
    }

    // ======================== Helpers ========================

    private User createUser(UUID id) {
        User user = new User();
        user.setId(id);
        user.setNickname("Player");
        user.setEmail("player@test.com");
        user.setPassword("password");
        user.setStats(new Stats(user));
        return user;
    }

    private Shot createShot(boolean hit) {
        Shot shot = mock(Shot.class);
        when(shot.isHit()).thenReturn(hit);
        return shot;
    }

    private Ship createShip(boolean sunken) {
        Ship ship = mock(Ship.class);
        when(ship.isSunken()).thenReturn(sunken);
        return ship;
    }

    // ======================== persistGameResult ========================

    @Nested
    @DisplayName("persistGameResult")
    class PersistGameResult {

        @Mock
        private Game game;

        @Mock
        private Board winnerBoard;

        @Mock
        private Board loserBoard;

        @Test
        @DisplayName("Deve persistir resultado com sucesso calculando stats corretamente")
        void shouldPersistGameResultSuccessfully() {
            // Arrange
            User winner = createUser(winnerId);
            User loser = createUser(loserId);

            when(game.getId()).thenReturn(gameId);
            when(game.getWinnerId()).thenReturn(winnerId);
            when(game.getCreatedAt()).thenReturn(Instant.now().minusSeconds(300));
            when(game.getGameMode()).thenReturn(GameMode.CLASSIC);
            when(game.getStatus()).thenReturn(GameStatus.FINISHED);

            // Player1 is the winner
            Board player1Board = mock(Board.class);
            Board player2Board = mock(Board.class);
            when(player1Board.getPlayerId()).thenReturn(winnerId);
            when(game.getPlayer1()).thenReturn(player1Board);
            when(game.getPlayer2()).thenReturn(player2Board);
            when(player2Board.getPlayerId()).thenReturn(loserId);

            // winnerBoard = board where winner shot (opponent's board = loser's board)
            // loserBoard = board where loser shot (opponent's board = winner's board)
            when(game.getOpponentBoardOf(loserId)).thenReturn(winnerBoard);
            when(game.getOpponentBoardOf(winnerId)).thenReturn(loserBoard);

            // Winner stats: 10 shots on loserBoard, 7 hits, 3 misses, 4 ships destroyed
            List<Shot> loserBoardShots = List.of(
                    createShot(true), createShot(true), createShot(true),
                    createShot(true), createShot(true), createShot(true),
                    createShot(true), createShot(false), createShot(false), createShot(false)
            );
            List<Ship> loserBoardShips = List.of(
                    createShip(true), createShip(true), createShip(true), createShip(true), createShip(false)
            );
            when(loserBoard.getShots()).thenReturn(loserBoardShots);
            when(loserBoard.getShips()).thenReturn(loserBoardShips);

            // Loser stats: 8 shots on winnerBoard, 3 hits, 5 misses, 2 ships destroyed
            List<Shot> winnerBoardShots = List.of(
                    createShot(true), createShot(true), createShot(true),
                    createShot(false), createShot(false), createShot(false),
                    createShot(false), createShot(false)
            );
            List<Ship> winnerBoardShips = List.of(
                    createShip(true), createShip(true), createShip(false), createShip(false), createShip(false)
            );
            when(winnerBoard.getShots()).thenReturn(winnerBoardShots);
            when(winnerBoard.getShips()).thenReturn(winnerBoardShips);

            when(gameResultRepository.existsById(gameId)).thenReturn(false);
            when(userRepository.findById(winnerId)).thenReturn(Optional.of(winner));
            when(userRepository.findById(loserId)).thenReturn(Optional.of(loser));

            Room room = mock(Room.class);
            when(room.getCode()).thenReturn("ABC123");
            when(roomRepository.findByGameId(gameId)).thenReturn(Optional.of(room));

            // Act
            gameResultService.persistGameResult(game);

            // Assert
            ArgumentCaptor<GameResult> captor = ArgumentCaptor.forClass(GameResult.class);
            verify(gameResultRepository).save(captor.capture());

            GameResult result = captor.getValue();
            assertEquals(gameId, result.getId());
            assertEquals("ABC123", result.getRoomCode());
            assertEquals(GameStatus.FINISHED, result.getStatus());
            assertEquals(GameMode.CLASSIC, result.getGameMode());
            assertEquals(winner, result.getWinner());
            assertEquals(loser, result.getLoser());

            // Winner stats from loserBoard
            assertEquals(10, result.getWinnerShots());
            assertEquals(7, result.getWinnerHits());
            assertEquals(3, result.getWinnerMisses());
            assertEquals(4, result.getWinnerShipsDestroyed());

            // Loser stats from winnerBoard
            assertEquals(8, result.getLoserShots());
            assertEquals(3, result.getLoserHits());
            assertEquals(5, result.getLoserMisses());
            assertEquals(2, result.getLoserShipsDestroyed());

            assertNotNull(result.getDurationSeconds());
            assertNotNull(result.getFinishedAt());
        }

        @Test
        @DisplayName("Deve usar roomCode 'N/A' quando room não é encontrada")
        void shouldUseNAWhenRoomNotFound() {
            // Arrange
            User winner = createUser(winnerId);
            User loser = createUser(loserId);

            when(game.getId()).thenReturn(gameId);
            when(game.getWinnerId()).thenReturn(winnerId);
            when(game.getCreatedAt()).thenReturn(Instant.now().minusSeconds(60));
            when(game.getGameMode()).thenReturn(GameMode.TACTICAL);
            when(game.getStatus()).thenReturn(GameStatus.FINISHED);

            Board player1Board = mock(Board.class);
            Board player2Board = mock(Board.class);
            when(player1Board.getPlayerId()).thenReturn(winnerId);
            when(game.getPlayer1()).thenReturn(player1Board);
            when(game.getPlayer2()).thenReturn(player2Board);
            when(player2Board.getPlayerId()).thenReturn(loserId);

            when(game.getOpponentBoardOf(loserId)).thenReturn(winnerBoard);
            when(game.getOpponentBoardOf(winnerId)).thenReturn(loserBoard);

            when(loserBoard.getShots()).thenReturn(List.of());
            when(loserBoard.getShips()).thenReturn(List.of());
            when(winnerBoard.getShots()).thenReturn(List.of());
            when(winnerBoard.getShips()).thenReturn(List.of());

            when(gameResultRepository.existsById(gameId)).thenReturn(false);
            when(userRepository.findById(winnerId)).thenReturn(Optional.of(winner));
            when(userRepository.findById(loserId)).thenReturn(Optional.of(loser));
            when(roomRepository.findByGameId(gameId)).thenReturn(Optional.empty());

            // Act
            gameResultService.persistGameResult(game);

            // Assert
            ArgumentCaptor<GameResult> captor = ArgumentCaptor.forClass(GameResult.class);
            verify(gameResultRepository).save(captor.capture());
            assertEquals("N/A", captor.getValue().getRoomCode());
        }

        @Test
        @DisplayName("Não deve persistir se já existe resultado (existsById=true)")
        void shouldNotPersistWhenResultAlreadyExists() {
            // Arrange
            when(game.getId()).thenReturn(gameId);
            when(gameResultRepository.existsById(gameId)).thenReturn(true);

            // Act
            gameResultService.persistGameResult(game);

            // Assert
            verify(gameResultRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve ignorar DataIntegrityViolationException (race condition)")
        void shouldIgnoreDataIntegrityViolationException() {
            // Arrange
            User winner = createUser(winnerId);
            User loser = createUser(loserId);

            when(game.getId()).thenReturn(gameId);
            when(game.getWinnerId()).thenReturn(winnerId);
            when(game.getCreatedAt()).thenReturn(Instant.now().minusSeconds(120));
            when(game.getGameMode()).thenReturn(GameMode.CLASSIC);
            when(game.getStatus()).thenReturn(GameStatus.FINISHED);

            Board player1Board = mock(Board.class);
            Board player2Board = mock(Board.class);
            when(player1Board.getPlayerId()).thenReturn(winnerId);
            when(game.getPlayer1()).thenReturn(player1Board);
            when(game.getPlayer2()).thenReturn(player2Board);
            when(player2Board.getPlayerId()).thenReturn(loserId);

            when(game.getOpponentBoardOf(loserId)).thenReturn(winnerBoard);
            when(game.getOpponentBoardOf(winnerId)).thenReturn(loserBoard);

            when(loserBoard.getShots()).thenReturn(List.of());
            when(loserBoard.getShips()).thenReturn(List.of());
            when(winnerBoard.getShots()).thenReturn(List.of());
            when(winnerBoard.getShips()).thenReturn(List.of());

            when(gameResultRepository.existsById(gameId)).thenReturn(false);
            when(userRepository.findById(winnerId)).thenReturn(Optional.of(winner));
            when(userRepository.findById(loserId)).thenReturn(Optional.of(loser));
            when(roomRepository.findByGameId(gameId)).thenReturn(Optional.empty());

            when(gameResultRepository.save(any(GameResult.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate key"));

            // Act & Assert - should not throw
            assertDoesNotThrow(() -> gameResultService.persistGameResult(game));
        }

        @Test
        @DisplayName("Deve calcular loserId corretamente quando player2 é o winner")
        void shouldCalculateLoserIdWhenPlayer2IsWinner() {
            // Arrange
            UUID player1Id = loserId;
            UUID player2Id = winnerId;

            User winner = createUser(player2Id);
            User loser = createUser(player1Id);

            when(game.getId()).thenReturn(gameId);
            when(game.getWinnerId()).thenReturn(player2Id);
            when(game.getCreatedAt()).thenReturn(Instant.now().minusSeconds(200));
            when(game.getGameMode()).thenReturn(GameMode.CLASSIC);
            when(game.getStatus()).thenReturn(GameStatus.FINISHED);

            // Player1 is NOT the winner, so loser = player1
            Board player1Board = mock(Board.class);
            when(player1Board.getPlayerId()).thenReturn(player1Id);
            when(game.getPlayer1()).thenReturn(player1Board);

            when(game.getOpponentBoardOf(player1Id)).thenReturn(winnerBoard);
            when(game.getOpponentBoardOf(player2Id)).thenReturn(loserBoard);

            when(loserBoard.getShots()).thenReturn(List.of());
            when(loserBoard.getShips()).thenReturn(List.of());
            when(winnerBoard.getShots()).thenReturn(List.of());
            when(winnerBoard.getShips()).thenReturn(List.of());

            when(gameResultRepository.existsById(gameId)).thenReturn(false);
            when(userRepository.findById(player2Id)).thenReturn(Optional.of(winner));
            when(userRepository.findById(player1Id)).thenReturn(Optional.of(loser));
            when(roomRepository.findByGameId(gameId)).thenReturn(Optional.empty());

            // Act
            gameResultService.persistGameResult(game);

            // Assert
            ArgumentCaptor<GameResult> captor = ArgumentCaptor.forClass(GameResult.class);
            verify(gameResultRepository).save(captor.capture());

            GameResult result = captor.getValue();
            assertEquals(winner, result.getWinner());
            assertEquals(loser, result.getLoser());
        }
    }

    // ======================== updatePlayerStatsAsync ========================

    @Nested
    @DisplayName("updatePlayerStatsAsync")
    class UpdatePlayerStatsAsync {

        @Test
        @DisplayName("Deve atualizar stats do winner e loser com sucesso")
        void shouldUpdateStatsSuccessfully() {
            // Arrange
            User winner = createUser(winnerId);
            User loser = createUser(loserId);

            Stats winnerStats = winner.getStats();
            Stats loserStats = loser.getStats();

            when(userRepository.findById(winnerId)).thenReturn(Optional.of(winner));
            when(userRepository.findById(loserId)).thenReturn(Optional.of(loser));

            // Act
            gameResultService.updatePlayerStatsAsync(winnerId, loserId);

            // Assert
            assertEquals(1, winnerStats.getVictories());
            assertEquals(1, winnerStats.getTotalGames());
            assertEquals(0, winnerStats.getDefeats());

            assertEquals(1, loserStats.getDefeats());
            assertEquals(1, loserStats.getTotalGames());
            assertEquals(0, loserStats.getVictories());

            verify(userRepository).save(winner);
            verify(userRepository).save(loser);
        }

        @Test
        @DisplayName("Deve tratar winner não encontrado sem lançar exceção")
        void shouldHandleWinnerNotFound() {
            // Arrange
            when(userRepository.findById(winnerId)).thenReturn(Optional.empty());

            // Act & Assert - o método captura exceções internamente
            assertDoesNotThrow(() -> gameResultService.updatePlayerStatsAsync(winnerId, loserId));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Deve tratar loser não encontrado sem lançar exceção")
        void shouldHandleLoserNotFound() {
            // Arrange
            User winner = createUser(winnerId);
            when(userRepository.findById(winnerId)).thenReturn(Optional.of(winner));
            when(userRepository.findById(loserId)).thenReturn(Optional.empty());

            // Act & Assert - o método captura exceções internamente
            assertDoesNotThrow(() -> gameResultService.updatePlayerStatsAsync(winnerId, loserId));
        }

        @Test
        @DisplayName("Não deve atualizar stats se winner.stats for null")
        void shouldNotUpdateWhenWinnerStatsIsNull() {
            // Arrange
            User winner = createUser(winnerId);
            winner.setStats(null);
            User loser = createUser(loserId);

            when(userRepository.findById(winnerId)).thenReturn(Optional.of(winner));
            when(userRepository.findById(loserId)).thenReturn(Optional.of(loser));

            // Act
            gameResultService.updatePlayerStatsAsync(winnerId, loserId);

            // Assert - winner not saved (no stats), loser saved
            verify(userRepository, never()).save(winner);
            verify(userRepository).save(loser);

            assertEquals(1, loser.getStats().getDefeats());
        }

        @Test
        @DisplayName("Não deve atualizar stats se loser.stats for null")
        void shouldNotUpdateWhenLoserStatsIsNull() {
            // Arrange
            User winner = createUser(winnerId);
            User loser = createUser(loserId);
            loser.setStats(null);

            when(userRepository.findById(winnerId)).thenReturn(Optional.of(winner));
            when(userRepository.findById(loserId)).thenReturn(Optional.of(loser));

            // Act
            gameResultService.updatePlayerStatsAsync(winnerId, loserId);

            // Assert - winner saved, loser not saved (no stats)
            verify(userRepository).save(winner);
            verify(userRepository, never()).save(loser);

            assertEquals(1, winner.getStats().getVictories());
        }
    }
}
