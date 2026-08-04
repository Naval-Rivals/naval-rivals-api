package com.navalrivals.domain.game.controller;

import com.navalrivals.domain.game.dto.AbilityRequest;
import com.navalrivals.domain.game.dto.AttackRequest;
import com.navalrivals.domain.game.dto.AttackResponse;
import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.AbilityType;
import com.navalrivals.domain.game.enums.GameMode;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.service.GameDisconnectService;
import com.navalrivals.domain.game.service.GameEventPublisher;
import com.navalrivals.domain.game.service.GameResultService;
import com.navalrivals.domain.game.service.GameService;
import com.navalrivals.domain.game.service.TurnTimerService;
import com.navalrivals.domain.game.util.CellConverter;
import com.navalrivals.domain.position.entity.Position;
import com.navalrivals.domain.ship.entity.Ship;
import com.navalrivals.domain.shot.entity.Shot;
import com.navalrivals.domain.user.entity.User;
import com.navalrivals.infra.cluster.ClusterEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final GameService gameService;
    private final GameResultService gameResultService;
    private final ClusterEventPublisher clusterEventPublisher;
    private final GameEventPublisher eventPublisher;
    private final TurnTimerService turnTimerService;
    private final GameDisconnectService disconnectService;

    @MessageMapping("/game/{gameId}/attack")
    public void attack(
            @DestinationVariable UUID gameId,
            AttackRequest request,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        User user = extractUser(principal);
        MDC.put("gameId", gameId.toString());
        MDC.put("userId", user.getId().toString());
        try {
            Position position = CellConverter.toPosition(request.cell());
            String attackType = request.type() != null ? request.type() : "NORMAL";

            log.info("[WS IN] /app/game/{}/attack — playerId={}, cell={}, type={}", gameId, user.getId(), request.cell(), attackType);

        // Cancela o timer ANTES de executar o ataque para evitar race condition
        turnTimerService.cancelTimer(gameId);

        // Executa o ataque
        var result = gameService.shoot(gameId, user, position, attackType);
        Game game = result.game();
        Shot shot = result.shot();


        // Determina o oponente
        UUID opponentId = game.getPlayer1().getPlayerId().equals(user.getId())
                ? game.getPlayer2().getPlayerId()
                : game.getPlayer1().getPlayerId();

        // No modo tático, se o tiro foi bloqueado, publica evento de bloqueio ANTES do ATTACK_RESULT
        if (game.getGameMode() == GameMode.TACTICAL && shot.isBlocked()) {
            if (shot.getBlockedBy() == Shot.BlockedBy.SHIELD) {
                eventPublisher.publishShieldBlocked(gameId, opponentId, request.cell());
            }
        }

        // Publica ATTACK_RESULT no tópico de eventos
        eventPublisher.publishAttackResult(gameId, user.getId(), request.cell(), shot.isHit(), attackType);

        // Verifica se afundou navio
        var opponentBoard = game.getOpponentBoardOf(user.getId());
        Ship sunkShip = opponentBoard.getShips().stream()
                .filter(Ship::isSunken)
                .filter(ship -> ship.getPositions().stream()
                        .anyMatch(pos -> pos.getRow() == position.getRow()
                                && pos.getCol() == position.getCol()))
                .findFirst()
                .orElse(null);

        boolean sunk = sunkShip != null;
        String shipType = sunk ? sunkShip.getType().name() : null;

        if (sunk) {
            log.info("[GAME] Navio afundado — gameId={}, opponentId={}, shipType={}", gameId, opponentId, shipType);
            eventPublisher.publishShipSunk(gameId, opponentId, sunkShip);
        }

        boolean gameOver = game.getStatus() == GameStatus.FINISHED;

        // Publica AttackResponse
        AttackResponse response = new AttackResponse(
                gameId,
                user.getId(),
                request.cell(),
                shot.isHit(),
                sunk,
                shipType,
                gameOver,
                gameOver ? game.getWinnerId() : null,
                gameOver ? null : game.getCurrentTurn(),
                attackType
        );
        clusterEventPublisher.publish("/topic/game/" + gameId + "/attack", response);

        if (gameOver) {
            log.info("[GAME] Partida finalizada por ALL_SHIPS_SUNK — gameId={}, winnerId={}", gameId, game.getWinnerId());
            gameResultService.persistGameResult(game);
            eventPublisher.publishGameOver(gameId, game.getWinnerId(), opponentId, "ALL_SHIPS_SUNK");
            turnTimerService.cancelTimer(gameId);
            gameResultService.updatePlayerStatsAsync(game.getWinnerId(), opponentId);
            disconnectService.cleanupGame(gameId);
            gameService.removeGame(gameId);
        } else {
            eventPublisher.publishTurnChange(gameId, game.getCurrentTurn(), turnTimerService.getTurnTimeout());
            turnTimerService.startTimer(gameId);
        }
        } finally {
            MDC.remove("gameId");
            MDC.remove("userId");
        }
    }

    @MessageMapping("/game/{gameId}/ability")
    public void ability(
            @DestinationVariable UUID gameId,
            AbilityRequest request,
            Principal principal
    ) {
        User user = extractUser(principal);
        MDC.put("gameId", gameId.toString());
        MDC.put("userId", user.getId().toString());
        try {
            AbilityType abilityType = request.ability();
            Position target = request.cell() != null ? CellConverter.toPosition(request.cell()) : null;

            log.info("[WS IN] /app/game/{}/ability — playerId={}, ability={}, cell={}", gameId, user.getId(), abilityType, request.cell());

        // Para habilidades que consomem turno (RADAR, EMP), cancela timer antes
        boolean consumesTurn = (abilityType == AbilityType.RADAR || abilityType == AbilityType.EMP_NAVAL);
        if (consumesTurn) {
            turnTimerService.cancelTimer(gameId);
        }

        // Executa a habilidade
        var result = gameService.useAbility(gameId, user, abilityType, target);
        Game game = result.game();
        List<Position> positions = result.positions();

        // Busca estado atualizado
//        Game game = gameService.findById(gameId);
        UUID opponentId = game.getPlayer1().getPlayerId().equals(user.getId())
                ? game.getPlayer2().getPlayerId()
                : game.getPlayer1().getPlayerId();

        // Publica eventos de acordo com a habilidade
        switch (abilityType) {
            case SHIELD -> {
                var myBoard = game.getBoardOf(user.getId());
                eventPublisher.publishShieldActivated(gameId, user.getId(), myBoard.getShieldCharges());
            }
            case RADAR -> {
                List<String> revealedCells = positions.stream()
                        .map(CellConverter::toCell)
                        .toList();
                eventPublisher.publishRadarResult(gameId, user.getId(), request.cell(), revealedCells);
                eventPublisher.publishTurnChange(gameId, game.getCurrentTurn(), turnTimerService.getTurnTimeout());
                turnTimerService.startTimer(gameId);
            }
            case EMP_NAVAL -> {
                eventPublisher.publishEmpActivated(gameId, user.getId(), opponentId, 2);
                eventPublisher.publishTurnChange(gameId, game.getCurrentTurn(), turnTimerService.getTurnTimeout());
                turnTimerService.startTimer(gameId);
            }
        }
        } finally {
            MDC.remove("gameId");
            MDC.remove("userId");
        }
    }

    @MessageMapping("/game/{gameId}/register")
    public void register(
            @DestinationVariable UUID gameId,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        User user = extractUser(principal);
        String sessionId = headerAccessor.getSessionId();
        MDC.put("gameId", gameId.toString());
        MDC.put("userId", user.getId().toString());
        try {
            log.info("[WS IN] /app/game/{}/register — playerId={}, sessionId={}", gameId, user.getId(), sessionId);

            if (!gameService.exists(gameId)) {
                log.warn("[GAME] Register em game inexistente — gameId={}, playerId={}", gameId, user.getId());
                clusterEventPublisher.publish("/topic/game/" + gameId + "/events",
                        (Object) Map.of("event", "GAME_NOT_FOUND", "gameId", gameId.toString(), "reason", "OPPONENT_DISCONNECTED"));
                return;
            }

            disconnectService.handleReconnect(sessionId, gameId, user.getId());
        } finally {
            MDC.remove("gameId");
            MDC.remove("userId");
        }
    }

    private User extractUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            return (User) auth.getPrincipal();
        }
        throw new IllegalStateException("Usuário não autenticado no WebSocket");
    }
}
