package com.navalrivals.domain.game.service;

import com.navalrivals.domain.game.dto.GameResultResponse;
import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.entity.GameResult;
import com.navalrivals.domain.game.enums.AbilityType;
import com.navalrivals.domain.game.enums.GameMode;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.repository.GameResultRepository;
import com.navalrivals.domain.game.storage.GameStorage;
import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.ship.enums.ShipType;
import com.navalrivals.domain.shot.entity.Shot;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.infra.exception.exceptions.NotFoundException;
import com.navalrivals.infra.exception.exceptions.PlayerWithoutPermissionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameStorage storage;

    @Mock
    private GameResultRepository gameResultRepository;

    @Mock
    private GameWebSocketService gameWebSocketService;

    @Mock
    private TurnTimerService turnTimerService;

    @Mock
    private GameResultService gameResultService;

    @Mock
    private GameEventPublisher gameEventPublisher;

    @InjectMocks
    private GameService gameService;

    // ======================== Helpers ========================

    private User createUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setNickname("Player");
        user.setEmail("player@test.com");
        user.setPassword("password");
        return user;
    }

    private List<Ship> createValidFleet() {
        return List.of(
                new Ship(ShipType.CARRIER, List.of(
                        new Position(0, 0), new Position(0, 1), new Position(0, 2),
                        new Position(0, 3), new Position(0, 4)), false),
                new Ship(ShipType.BATTLESHIP, List.of(
                        new Position(1, 0), new Position(1, 1), new Position(1, 2),
                        new Position(1, 3)), false),
                new Ship(ShipType.CRUISER, List.of(
                        new Position(2, 0), new Position(2, 1), new Position(2, 2)), false),
                new Ship(ShipType.SUBMARINE, List.of(
                        new Position(3, 0), new Position(3, 1), new Position(3, 2)), false),
                new Ship(ShipType.DESTROYER, List.of(
                        new Position(4, 0), new Position(4, 1)), false)
        );
    }

    private List<Ship> createValidFleetAlternate() {
        return List.of(
                new Ship(ShipType.CARRIER, List.of(
                        new Position(5, 0), new Position(5, 1), new Position(5, 2),
                        new Position(5, 3), new Position(5, 4)), false),
                new Ship(ShipType.BATTLESHIP, List.of(
                        new Position(6, 0), new Position(6, 1), new Position(6, 2),
                        new Position(6, 3)), false),
                new Ship(ShipType.CRUISER, List.of(
                        new Position(7, 0), new Position(7, 1), new Position(7, 2)), false),
                new Ship(ShipType.SUBMARINE, List.of(
                        new Position(8, 0), new Position(8, 1), new Position(8, 2)), false),
                new Ship(ShipType.DESTROYER, List.of(
                        new Position(9, 0), new Position(9, 1)), false)
        );
    }

    // ======================== createGame ========================

    @Test
    @DisplayName("createGame - deve criar jogo e salvar no storage")
    void createGame_shouldCreateAndSave() {
        User player = createUser();

        Game result = gameService.createGame(player, GameMode.CLASSIC);

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals(GameStatus.WAITING_OPPONENT, result.getStatus());
        assertEquals(GameMode.CLASSIC, result.getGameMode());
        assertEquals(player.getId(), result.getPlayer1().getPlayerId());
        verify(storage).save(any(Game.class));
    }

    // ======================== joinGame ========================

    @Test
    @DisplayName("joinGame - deve permitir jogador entrar na partida")
    void joinGame_shouldJoinSuccessfully() {
        User player1 = createUser();
        User player2 = createUser();
        Game game = new Game(player1, GameMode.CLASSIC);
        UUID gameId = game.getId();

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        Game result = gameService.joinGame(gameId, player2);

        assertEquals(GameStatus.PLACING_SHIPS, result.getStatus());
        assertNotNull(result.getPlayer2());
        assertEquals(player2.getId(), result.getPlayer2().getPlayerId());
    }

    @Test
    @DisplayName("joinGame - deve lançar NotFoundException quando jogo não encontrado")
    void joinGame_shouldThrowNotFound() {
        UUID gameId = UUID.randomUUID();
        User player = createUser();

        when(storage.findById(gameId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> gameService.joinGame(gameId, player));
    }

    // ======================== placeShips ========================

    @Test
    @DisplayName("placeShips - ambos prontos deve mudar para IN_PROGRESS, notificar e iniciar timer")
    void placeShips_bothReady_shouldStartGame() {
        User player1 = createUser();
        User player2 = createUser();
        Game game = new Game(player1, GameMode.CLASSIC);
        game.join(player2);
        UUID gameId = game.getId();

        // Player1 posiciona
        when(storage.findById(gameId)).thenReturn(Optional.of(game));
        gameService.placeShips(gameId, player1, createValidFleet());

        // Player2 posiciona — ambos prontos
        Game result = gameService.placeShips(gameId, player2, createValidFleetAlternate());

        assertEquals(GameStatus.IN_PROGRESS, result.getStatus());
        verify(gameWebSocketService).notifyGameStarted(eq(gameId), eq(player2.getId()), any(UUID.class));
        verify(turnTimerService).startTimer(gameId);
    }

    @Test
    @DisplayName("placeShips - apenas um pronto deve notificar opponent ready")
    void placeShips_onlyOneReady_shouldNotifyOpponentReady() {
        User player1 = createUser();
        User player2 = createUser();
        Game game = new Game(player1, GameMode.CLASSIC);
        game.join(player2);
        UUID gameId = game.getId();

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        Game result = gameService.placeShips(gameId, player1, createValidFleet());

        assertEquals(GameStatus.PLACING_SHIPS, result.getStatus());
        verify(gameWebSocketService).notifyOpponentReady(gameId, player1.getId());
        verify(turnTimerService, never()).startTimer(any());
    }

    @Test
    @DisplayName("placeShips - jogador não pertence à partida deve lançar exceção")
    void placeShips_playerNotInGame_shouldThrowException() {
        User player1 = createUser();
        User player2 = createUser();
        User outsider = createUser();
        Game game = new Game(player1, GameMode.CLASSIC);
        game.join(player2);
        UUID gameId = game.getId();

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        assertThrows(PlayerWithoutPermissionException.class,
                () -> gameService.placeShips(gameId, outsider, createValidFleet()));
    }

    // ======================== shoot ========================

    @Test
    @DisplayName("shoot - tiro normal com hit deve retornar shot com hit=true")
    void shoot_normalHit_shouldReturnHitShot() {
        User player1 = createUser();
        User player2 = createUser();
        Game game = new Game(player1, GameMode.CLASSIC);
        game.join(player2);
        UUID gameId = game.getId();

        game.placeShips(player1.getId(), createValidFleet());
        game.placeShips(player2.getId(), createValidFleetAlternate());

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        // Quem tem o turno atira em posição de navio do oponente
        UUID currentTurn = game.getCurrentTurn();
        User attacker = currentTurn.equals(player1.getId()) ? player1 : player2;
        // O oponente tem navios na frota alternativa (5,0) ou frota normal (0,0)
        Position target = currentTurn.equals(player1.getId())
                ? new Position(5, 0)  // player2 ships start at row 5
                : new Position(0, 0); // player1 ships start at row 0

        Shot result = gameService.shoot(gameId, attacker, target, "NORMAL");

        assertTrue(result.isHit());
    }

    @Test
    @DisplayName("shoot - tiro normal com miss deve trocar turno")
    void shoot_normalMiss_shouldSwitchTurn() {
        User player1 = createUser();
        User player2 = createUser();
        Game game = new Game(player1, GameMode.CLASSIC);
        game.join(player2);
        UUID gameId = game.getId();

        game.placeShips(player1.getId(), createValidFleet());
        game.placeShips(player2.getId(), createValidFleetAlternate());

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        UUID currentTurn = game.getCurrentTurn();
        User attacker = currentTurn.equals(player1.getId()) ? player1 : player2;
        // Posição sem navios (row 9, col 9 — não há navio em nenhuma frota ali)
        Position emptyPosition = new Position(9, 9);

        Shot result = gameService.shoot(gameId, attacker, emptyPosition, "NORMAL");

        assertFalse(result.isHit());
        // Turno deve ter mudado
        assertNotEquals(currentTurn, game.getCurrentTurn());
    }

    @Test
    @DisplayName("shoot - torpedo deve afundar navio inteiro se acertar")
    void shoot_torpedo_shouldSinkEntireShip() {
        User player1 = createUser();
        User player2 = createUser();
        Game game = new Game(player1, GameMode.TACTICAL);
        game.join(player2);
        UUID gameId = game.getId();

        game.placeShips(player1.getId(), createValidFleet());
        game.placeShips(player2.getId(), createValidFleetAlternate());

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        UUID currentTurn = game.getCurrentTurn();
        User attacker = currentTurn.equals(player1.getId()) ? player1 : player2;
        // Mira no destroyer do oponente (menor navio)
        Position target = currentTurn.equals(player1.getId())
                ? new Position(9, 0)  // player2 destroyer at (9,0),(9,1)
                : new Position(4, 0); // player1 destroyer at (4,0),(4,1)

        Shot result = gameService.shoot(gameId, attacker, target, "TORPEDO");

        assertTrue(result.isHit());
    }

    @Test
    @DisplayName("shoot - jogador não pertence à partida deve lançar exceção")
    void shoot_playerNotInGame_shouldThrowException() {
        User player1 = createUser();
        User player2 = createUser();
        User outsider = createUser();
        Game game = new Game(player1, GameMode.CLASSIC);
        game.join(player2);
        UUID gameId = game.getId();

        game.placeShips(player1.getId(), createValidFleet());
        game.placeShips(player2.getId(), createValidFleetAlternate());

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        assertThrows(PlayerWithoutPermissionException.class,
                () -> gameService.shoot(gameId, outsider, new Position(0, 0), "NORMAL"));
    }

    @Test
    @DisplayName("shoot - jogo não encontrado deve lançar NotFoundException")
    void shoot_gameNotFound_shouldThrowException() {
        UUID gameId = UUID.randomUUID();
        User player = createUser();

        when(storage.findById(gameId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> gameService.shoot(gameId, player, new Position(0, 0), "NORMAL"));
    }

    // ======================== useAbility ========================

    @Test
    @DisplayName("useAbility - SHIELD deve ativar escudo sem consumir turno")
    void useAbility_shield_shouldActivateShield() {
        User player1 = createUser();
        User player2 = createUser();
        Game game = new Game(player1, GameMode.TACTICAL);
        game.join(player2);
        UUID gameId = game.getId();

        game.placeShips(player1.getId(), createValidFleet());
        game.placeShips(player2.getId(), createValidFleetAlternate());

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        UUID currentTurn = game.getCurrentTurn();
        User attacker = currentTurn.equals(player1.getId()) ? player1 : player2;

        List<Position> result = gameService.useAbility(gameId, attacker, AbilityType.SHIELD, null);

        assertTrue(result.isEmpty());
        // Turno não deve mudar (SHIELD não consome turno)
        assertEquals(currentTurn, game.getCurrentTurn());
        // Escudo ativado no board do jogador
        assertTrue(game.getBoardOf(currentTurn).isShieldActive());
    }

    @Test
    @DisplayName("useAbility - RADAR deve revelar posições e consumir turno")
    void useAbility_radar_shouldRevealPositionsAndSwitchTurn() {
        User player1 = createUser();
        User player2 = createUser();
        Game game = new Game(player1, GameMode.TACTICAL);
        game.join(player2);
        UUID gameId = game.getId();

        game.placeShips(player1.getId(), createValidFleet());
        game.placeShips(player2.getId(), createValidFleetAlternate());

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        UUID currentTurn = game.getCurrentTurn();
        User attacker = currentTurn.equals(player1.getId()) ? player1 : player2;
        // Aponta radar para área com navios do oponente
        Position radarTarget = currentTurn.equals(player1.getId())
                ? new Position(5, 1)  // perto dos navios do player2
                : new Position(0, 1); // perto dos navios do player1

        List<Position> result = gameService.useAbility(gameId, attacker, AbilityType.RADAR, radarTarget);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        // Turno deve mudar (RADAR consome turno)
        assertNotEquals(currentTurn, game.getCurrentTurn());
    }

    @Test
    @DisplayName("useAbility - EMP_NAVAL deve desabilitar habilidades do oponente e consumir turno")
    void useAbility_empNaval_shouldDisableOpponentAndSwitchTurn() {
        User player1 = createUser();
        User player2 = createUser();
        Game game = new Game(player1, GameMode.TACTICAL);
        game.join(player2);
        UUID gameId = game.getId();

        game.placeShips(player1.getId(), createValidFleet());
        game.placeShips(player2.getId(), createValidFleetAlternate());

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        UUID currentTurn = game.getCurrentTurn();
        User attacker = currentTurn.equals(player1.getId()) ? player1 : player2;
        UUID opponentId = currentTurn.equals(player1.getId()) ? player2.getId() : player1.getId();

        List<Position> result = gameService.useAbility(gameId, attacker, AbilityType.EMP_NAVAL, null);

        assertTrue(result.isEmpty());
        // Turno deve mudar (EMP consome turno)
        assertNotEquals(currentTurn, game.getCurrentTurn());
        // Oponente deve estar com EMP ativado
        assertTrue(game.getBoardOf(opponentId).isEmpDisabled());
    }

    @Test
    @DisplayName("useAbility - jogador não pertence à partida deve lançar exceção")
    void useAbility_playerNotInGame_shouldThrowException() {
        User player1 = createUser();
        User player2 = createUser();
        User outsider = createUser();
        Game game = new Game(player1, GameMode.TACTICAL);
        game.join(player2);
        UUID gameId = game.getId();

        game.placeShips(player1.getId(), createValidFleet());
        game.placeShips(player2.getId(), createValidFleetAlternate());

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        assertThrows(PlayerWithoutPermissionException.class,
                () -> gameService.useAbility(gameId, outsider, AbilityType.SHIELD, null));
    }

    // ======================== getGameResult ========================

    @Test
    @DisplayName("getGameResult - deve retornar resultado quando encontrado")
    void getGameResult_found_shouldReturnResult() {
        UUID gameId = UUID.randomUUID();
        GameResult gameResult = new GameResult();
        gameResult.setId(gameId);
        gameResult.setRoomCode("ABC123");
        gameResult.setStatus(GameStatus.FINISHED);
        gameResult.setGameMode(GameMode.CLASSIC);

        User winner = createUser();
        User loser = createUser();
        gameResult.setWinner(winner);
        gameResult.setLoser(loser);
        gameResult.setDurationSeconds(120L);
        gameResult.setWinnerShots(20);
        gameResult.setWinnerHits(15);
        gameResult.setWinnerMisses(5);
        gameResult.setWinnerShipsDestroyed(5);
        gameResult.setLoserShots(18);
        gameResult.setLoserHits(10);
        gameResult.setLoserMisses(8);
        gameResult.setLoserShipsDestroyed(3);
        gameResult.setFinishedAt(java.time.Instant.now());

        when(gameResultRepository.findById(gameId)).thenReturn(Optional.of(gameResult));

        GameResultResponse result = gameService.getGameResult(gameId);

        assertNotNull(result);
        assertEquals(gameId, result.gameId());
        assertEquals(GameStatus.FINISHED, result.status());
    }

    @Test
    @DisplayName("getGameResult - deve lançar NotFoundException quando não encontrado")
    void getGameResult_notFound_shouldThrowException() {
        UUID gameId = UUID.randomUUID();

        when(gameResultRepository.findById(gameId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> gameService.getGameResult(gameId));
    }

    // ======================== forfeitGame ========================

    @Test
    @DisplayName("forfeitGame - deve finalizar jogo IN_PROGRESS, persistir resultado, publicar GAME_OVER e remover")
    void forfeitGame_inProgress_shouldFinalizeAndPersist() {
        User player1 = createUser();
        User player2 = createUser();
        Game game = new Game(player1, GameMode.CLASSIC);
        game.join(player2);
        UUID gameId = game.getId();

        game.placeShips(player1.getId(), createValidFleet());
        game.placeShips(player2.getId(), createValidFleetAlternate());
        // Game agora está IN_PROGRESS

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        boolean result = gameService.forfeitGame(gameId, player2.getId());

        assertTrue(result);
        assertEquals(GameStatus.FINISHED, game.getStatus());
        assertEquals(player1.getId(), game.getWinnerId());
        verify(gameResultService).persistGameResult(game);
        verify(gameEventPublisher).publishGameOver(gameId, player1.getId(), player2.getId(), "OPPONENT_SURRENDERED");
        verify(gameResultService).updatePlayerStatsAsync(player1.getId(), player2.getId());
        verify(turnTimerService).cancelTimer(gameId);
        verify(storage).remove(gameId);
    }

    @Test
    @DisplayName("forfeitGame - deve retornar false quando jogo não está IN_PROGRESS")
    void forfeitGame_notInProgress_shouldReturnFalse() {
        User player1 = createUser();
        User player2 = createUser();
        Game game = new Game(player1, GameMode.CLASSIC);
        game.join(player2);
        UUID gameId = game.getId();
        // Game está PLACING_SHIPS (não IN_PROGRESS)

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        boolean result = gameService.forfeitGame(gameId, player2.getId());

        assertFalse(result);
        verify(gameResultService, never()).persistGameResult(any());
        verify(gameEventPublisher, never()).publishGameOver(any(), any(), any(), any());
        verify(storage, never()).remove(any());
    }

    @Test
    @DisplayName("forfeitGame - deve retornar false quando jogo não encontrado")
    void forfeitGame_gameNotFound_shouldReturnFalse() {
        UUID gameId = UUID.randomUUID();

        when(storage.findById(gameId)).thenReturn(Optional.empty());

        boolean result = gameService.forfeitGame(gameId, UUID.randomUUID());

        assertFalse(result);
        verify(gameResultService, never()).persistGameResult(any());
    }

    @Test
    @DisplayName("forfeitGame - deve retornar false quando jogo já foi finalizado")
    void forfeitGame_alreadyFinished_shouldReturnFalse() {
        User player1 = createUser();
        User player2 = createUser();
        Game game = new Game(player1, GameMode.CLASSIC);
        game.join(player2);
        UUID gameId = game.getId();

        game.placeShips(player1.getId(), createValidFleet());
        game.placeShips(player2.getId(), createValidFleetAlternate());
        game.finish(player1.getId()); // Já finalizado

        when(storage.findById(gameId)).thenReturn(Optional.of(game));

        boolean result = gameService.forfeitGame(gameId, player2.getId());

        assertFalse(result);
        verify(gameResultService, never()).persistGameResult(any());
    }

    // ======================== removeGame ========================

    @Test
    @DisplayName("removeGame - deve chamar storage.remove")
    void removeGame_shouldCallStorageRemove() {
        UUID gameId = UUID.randomUUID();

        gameService.removeGame(gameId);

        verify(storage).remove(gameId);
    }
}
