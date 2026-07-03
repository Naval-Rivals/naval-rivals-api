package com.navalrivals.domain.game.controller;

import com.navalrivals.domain.game.dto.AttackRequest;
import com.navalrivals.domain.game.dto.AttackResponse;
import com.navalrivals.domain.game.entity.Game;
import com.navalrivals.domain.game.enums.GameStatus;
import com.navalrivals.domain.game.service.GameDisconnectService;
import com.navalrivals.domain.game.service.GameEventPublisher;
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
import java.util.UUID;

/**
 * Controller WebSocket para ações de jogo em tempo real.
 *
 * Endpoints STOMP:
 * - /app/game/{gameId}/attack  → processa ataque e publica resultado
 * - /app/game/{gameId}/register → registra sessão para tracking de desconexão
 */
@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final GameService gameService;
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

        // Cancela o timer ANTES de executar o ataque para evitar race condition
        // com handleTimeout() que poderia trocar o turno simultaneamente
        turnTimerService.cancelTimer(gameId);

        // Executa o ataque
        Shot shot = gameService.shoot(gameId, user, position);

        // Busca estado atualizado do game
        Game game = gameService.findById(gameId);

        // Determina o oponente
        UUID opponentId = game.getPlayer1().getPlayerId().equals(user.getId())
                ? game.getPlayer2().getPlayerId()
                : game.getPlayer1().getPlayerId();

        // Publica ATTACK_RESULT no tópico de eventos
        eventPublisher.publishAttackResult(gameId, user.getId(), request.cell(), shot.isHit());

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

        if (gameOver) {
            // Publica GAME_OVER e cancela timer
            eventPublisher.publishGameOver(gameId, game.getWinnerId(), opponentId, "ALL_SHIPS_SUNK");
            turnTimerService.cancelTimer(gameId);
        } else {
            // Publica TURN_CHANGE e reinicia timer para o próximo turno
            eventPublisher.publishTurnChange(gameId, game.getCurrentTurn(), turnTimerService.getTurnTimeout());
            turnTimerService.startTimer(gameId);
        }

        // Publica AttackResponse no tópico de attack (para compatibilidade com frontend)
        AttackResponse response = new AttackResponse(
                gameId,
                user.getId(),
                request.cell(),
                shot.isHit(),
                sunk,
                shipType,
                gameOver,
                gameOver ? game.getWinnerId() : null,
                gameOver ? null : game.getCurrentTurn()
        );
        messagingTemplate.convertAndSend("/topic/game/" + gameId + "/attack", response);

        // Limpa jogo da memória após publicar todos os eventos
        if (gameOver) {
            gameService.removeGame(gameId);
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
