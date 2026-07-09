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
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Controller WebSocket para ações de jogo em tempo real.
 *
 * Endpoints STOMP:
 * - /app/game/{gameId}/attack   → processa ataque e publica resultado
 * - /app/game/{gameId}/ability  → usa habilidade (modo tático)
 * - /app/game/{gameId}/register → registra sessão para tracking de desconexão
 */
@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final GameService gameService;
    private final GameResultService gameResultService;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameEventPublisher eventPublisher;
    private final TurnTimerService turnTimerService;
    private final GameDisconnectService disconnectService;

    /**
     * Processa um ataque enviado pelo frontend.
     *
     * Fluxo:
     * 1. Extrai User do Principal (JWT validado no CONNECT)
     * 2. Converte célula "C4" → Position(2, 3)
     * 3. Executa shoot (valida turno, status, etc.)
     * 4. Publica ATTACK_RESULT no /topic/game/{gameId}/events
     * 5. Se afundou navio → publica SHIP_SUNK
     * 6. Se game over → publica GAME_OVER e cancela timer
     * 7. Se continua → publica TURN_CHANGE e reinicia timer
     * 8. Publica AttackResponse no /topic/game/{gameId}/attack (compatibilidade)
     */
    @MessageMapping("/game/{gameId}/attack")
    public void attack(
            @DestinationVariable UUID gameId,
            AttackRequest request,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        User user = extractUser(principal);
        Position position = CellConverter.toPosition(request.cell());
        String attackType = request.type() != null ? request.type() : "NORMAL";

        // Cancela o timer ANTES de executar o ataque para evitar race condition
        // com handleTimeout() que poderia trocar o turno simultaneamente
        turnTimerService.cancelTimer(gameId);

        // Executa o ataque
        Shot shot = gameService.shoot(gameId, user, position, attackType);

        // Busca estado atualizado do game
        Game game = gameService.findById(gameId);

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
            eventPublisher.publishShipSunk(gameId, opponentId, sunkShip);
        }

        boolean gameOver = game.getStatus() == GameStatus.FINISHED;

        // Publica AttackResponse PRIMEIRO (frontend vê resultado do tiro imediatamente)
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
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/attack", response);

        if (gameOver) {
            // Persiste GameResult ANTES de publicar GAME_OVER
            // (garante que o GET /games/{id}/result funcione quando o frontend buscar)
            gameResultService.persistGameResult(game);

            // Publica GAME_OVER (frontend navega para tela de resultado e faz GET)
            eventPublisher.publishGameOver(gameId, game.getWinnerId(), opponentId, "ALL_SHIPS_SUNK");
            turnTimerService.cancelTimer(gameId);

            // Atualiza stats dos jogadores de forma assíncrona (não bloqueia)
            gameResultService.updatePlayerStatsAsync(game.getWinnerId(), opponentId);
            disconnectService.cleanupGame(gameId);
            gameService.removeGame(gameId);
        } else {
            // Publica TURN_CHANGE e reinicia timer para o próximo turno
            eventPublisher.publishTurnChange(gameId, game.getCurrentTurn(), turnTimerService.getTurnTimeout());
            turnTimerService.startTimer(gameId);
        }
    }

    /**
     * Usa uma habilidade no modo tático.
     *
     * Habilidades disponíveis:
     * - SHIELD: ativa escudo (não consome turno)
     * - RADAR: revela bloco 3x3 (consome turno)
     * - EMP_NAVAL: desativa habilidades do oponente por 2 turnos (consome turno)
     */
    @MessageMapping("/game/{gameId}/ability")
    public void ability(
            @DestinationVariable UUID gameId,
            AbilityRequest request,
            Principal principal
    ) {
        User user = extractUser(principal);

        AbilityType abilityType = request.ability();
        Position target = request.cell() != null ? CellConverter.toPosition(request.cell()) : null;

        // Para habilidades que consomem turno (RADAR, EMP), cancela timer antes
        boolean consumesTurn = (abilityType == AbilityType.RADAR || abilityType == AbilityType.EMP_NAVAL);
        if (consumesTurn) {
            turnTimerService.cancelTimer(gameId);
        }

        // Executa a habilidade
        List<Position> result = gameService.useAbility(gameId, user, abilityType, target);

        // Busca estado atualizado
        Game game = gameService.findById(gameId);
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
                List<String> revealedCells = result.stream()
                        .map(CellConverter::toCell)
                        .toList();
                eventPublisher.publishRadarResult(gameId, user.getId(), request.cell(), revealedCells);
                // Radar consome turno → publica TURN_CHANGE e reinicia timer
                eventPublisher.publishTurnChange(gameId, game.getCurrentTurn(), turnTimerService.getTurnTimeout());
                turnTimerService.startTimer(gameId);
            }
            case EMP_NAVAL -> {
                eventPublisher.publishEmpActivated(gameId, user.getId(), opponentId, 2);
                // EMP consome turno → publica TURN_CHANGE e reinicia timer
                eventPublisher.publishTurnChange(gameId, game.getCurrentTurn(), turnTimerService.getTurnTimeout());
                turnTimerService.startTimer(gameId);
            }
        }
    }

    /**
     * Registra a sessão do jogador para tracking de desconexão.
     *
     * O frontend DEVE chamar isso ao entrar no jogo (após se inscrever nos tópicos).
     * Se for uma reconexão (o jogador havia desconectado), o service trata automaticamente.
     */
    @MessageMapping("/game/{gameId}/register")
    public void register(
            @DestinationVariable UUID gameId,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        User user = extractUser(principal);
        String sessionId = headerAccessor.getSessionId();

        // handleReconnect verifica se há desconexão pendente para esse player
        // Se sim, cancela timeout e retoma timer. Se não, apenas registra.
        disconnectService.handleReconnect(sessionId, gameId, user.getId());
    }

    private User extractUser(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            return (User) auth.getPrincipal();
        }
        throw new IllegalStateException("Usuário não autenticado no WebSocket");
    }
}
