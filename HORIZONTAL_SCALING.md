<div align="center">
  <img src="https://avatars.githubusercontent.com/u/296882315?s=400&u=c45dc755f6cdd049b8e57e3adf220b7e456944e2&v=4" width="120"/>
</div>

# Escalabilidade Horizontal — Naval Rivals API

**Análise de problemas e soluções para deploy em cluster Kubernetes**

**Naval Rivals Multiplayer Online**

Autor: Caio de Souza<br>
Data: 03 de agosto de 2026

---

## Sumário

1. [Contexto e Objetivo](#1-contexto-e-objetivo)
2. [O Limite do Sticky Session](#2-o-limite-do-sticky-session)
3. [Mapa de Estado da Aplicação](#3-mapa-de-estado-da-aplicação)
4. [Problemas Identificados](#4-problemas-identificados)
   - 4.1 [Broker STOMP em memória](#41-broker-stomp-em-memória)
   - 4.2 [Timers de turno em memória](#42-timers-de-turno-em-memória)
   - 4.3 [Tracking de desconexão e reconexão em memória](#43-tracking-de-desconexão-e-reconexão-em-memória)
   - 4.4 [Emitters SSE do lobby em memória](#44-emitters-sse-do-lobby-em-memória)
   - 4.5 [Sessão de host de sala em memória](#45-sessão-de-host-de-sala-em-memória)
   - 4.6 [Schedulers duplicados entre réplicas](#46-schedulers-duplicados-entre-réplicas)
   - 4.7 [Probes de liveness e readiness ausentes](#47-probes-de-liveness-e-readiness-ausentes)
5. [Soluções Propostas](#5-soluções-propostas)
6. [Plano de Implementação](#6-plano-de-implementação)
7. [Configuração Kubernetes](#7-configuração-kubernetes)
8. [Conclusão](#8-conclusão)

---

## 1. Contexto e Objetivo

A aplicação será implantada em um cluster Kubernetes com a seguinte configuração:

| Item | Configuração |
|------|-------------|
| Réplicas | 2 pods |
| Exposição | Ingress ou LoadBalancer |
| Sticky Session | Habilitado (afinidade por cookie/IP) |
| Estado de partida | Redis (já implementado) |
| Banco de dados | PostgreSQL (compartilhado) |

O objetivo deste documento é identificar o que quebra ao passar de 1 para 2 réplicas, avaliar o que o Sticky Session realmente resolve, e propor as correções necessárias.

---

## 2. O Limite do Sticky Session

Antes de listar os problemas, é essencial entender o que o Sticky Session **resolve** e o que ele **não resolve**.

### O que o Sticky Session garante

Um cliente específico, após a primeira request, permanece roteado para o **mesmo pod** durante toda a sessão. Isso significa que:

- ✅ A conexão WebSocket de um jogador não migra de pod no meio da partida
- ✅ A conexão SSE de um jogador permanece no mesmo pod
- ✅ O evento de disconnect do WebSocket chega no pod que registrou a sessão

### O que o Sticky Session NÃO garante

**O ponto crítico:** o Sticky Session amarra *um cliente* a *um pod*. Ele **não** amarra *dois clientes da mesma partida* ao *mesmo pod*.

```
Cenário real com 2 réplicas e sticky session ativo:

    Jogador A ──sticky──► Pod 1  ─┐
                                   ├── mesma partida (gameId: abc-123)
    Jogador B ──sticky──► Pod 2  ─┘

    Jogador A ataca → Pod 1 processa → publica evento no broker LOCAL do Pod 1
    Jogador B (no Pod 2) NUNCA recebe o evento.
```

Como o Ingress distribui novas conexões por round-robin (ou hash de IP), há **~50% de chance** de os dois jogadores de uma partida caírem em pods diferentes. Nesse caso a partida simplesmente não funciona.

### Conclusão desta seção

| Problema | Sticky Session resolve? |
|----------|------------------------|
| Conexão WebSocket migrar de pod | ✅ Sim |
| Disconnect chegar no pod correto | ✅ Sim (parcialmente) |
| Dois jogadores da mesma partida no mesmo pod | ❌ **Não** |
| Eventos STOMP cruzarem entre pods | ❌ **Não** |
| Timer de turno acessível de outro pod | ❌ **Não** |
| Broadcast SSE alcançar todos os clientes | ❌ **Não** |

**O Sticky Session é necessário, mas insuficiente.** Ele reduz a superfície do problema, mas não elimina os bloqueadores fundamentais.

---

## 3. Mapa de Estado da Aplicação

Levantamento completo de onde cada tipo de estado é armazenado hoje.

### Estado já compartilhado (seguro para múltiplas réplicas)

| Dado | Armazenamento | Detalhe |
|------|--------------|---------|
| Estado da partida (tabuleiros, navios, tiros, turno) | **Redis** | `GameStorage`, chave `game:{uuid}`, TTL 25min |
| Usuários, stats, histórico de partidas | **PostgreSQL** | Repositórios JPA |
| Salas | **PostgreSQL** | `RoomRepository` |
| Autenticação | **Stateless (JWT)** | `SessionCreationPolicy.STATELESS` |

### Estado local à JVM (quebra com múltiplas réplicas)

| Componente | Campo | Conteúdo |
|-----------|-------|----------|
| `TurnTimerService` | `activeTimers` | `Map<UUID, ScheduledFuture<?>>` — timer de 60s por partida |
| `TurnTimerService` | `pausedRemainingMs` | `Map<UUID, Long>` — ms restantes ao pausar |
| `TurnTimerService` | `timerStartedAt` | `Map<UUID, Long>` — timestamp de início do turno |
| `TurnTimerService` | `scheduler` | `ScheduledExecutorService` (4 threads) |
| `GameDisconnectService` | `sessionMap` | `Map<String, PlayerSession>` — sessionId → (gameId, playerId) |
| `GameDisconnectService` | `activeSessionByPlayer` | `Map<UUID, String>` — playerId → sessionId ativo |
| `GameDisconnectService` | `reconnectTimers` | `Map<UUID, ScheduledFuture<?>>` — timer de 30s de reconexão |
| `GameDisconnectService` | `scheduler` | `ScheduledExecutorService` (2 threads) |
| `RoomSessionService` | `sessionToRoom` | `Map<String, UUID>` — sessionId do host → roomId |
| `LobbySSEService` | `emitters` | `CopyOnWriteArrayList<SseEmitter>` — conexões SSE ativas |
| Spring WebSocket | Simple Broker | Broker STOMP em memória (`enableSimpleBroker`) |

---

## 4. Problemas Identificados

### 4.1 Broker STOMP em memória

**Severidade: 🔴 CRÍTICA — bloqueia o funcionamento da partida**

A configuração atual usa o Simple Broker do Spring, que é puramente em memória:

```java
// WebSocketConfig.java
@Override
public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setApplicationDestinationPrefixes("/app");
}
```

**Consequência:** uma mensagem publicada via `SimpMessagingTemplate` só é entregue aos clientes WebSocket conectados **àquele pod**. Todos os tópicos do jogo são afetados:

| Destino | Publicado por | Eventos |
|---------|--------------|---------|
| `/topic/game/{gameId}/events` | `GameEventPublisher` | ATTACK_RESULT, TURN_CHANGE, TURN_TIMEOUT, SHIP_SUNK, GAME_OVER, OPPONENT_DISCONNECTED, OPPONENT_RECONNECTED, SHIELD_*, EMP_*, RADAR_USED |
| `/topic/game/{gameId}/attack` | `GameWebSocketController` | Resposta do ataque |
| `/topic/game/{gameId}/placement` | `GameWebSocketService` | OPPONENT_READY, GAME_STARTED |
| `/topic/room/{roomId}` | `RoomWebSocketService` | PLAYER_JOINED, ROOM_READY, PLAYER_LEFT |
| `/user/{playerId}/topic/...` | `GameEventPublisher` | RADAR_RESULT (privado) |

**Impacto prático:** se os dois jogadores estiverem em pods diferentes, nenhum dos dois vê as ações do adversário. A partida trava.

---

### 4.2 Timers de turno em memória

**Severidade: 🔴 CRÍTICA — corrompe o estado da partida**

O `TurnTimerService` mantém os timers em `ConcurrentHashMap` local e os executa em um `ScheduledExecutorService` local:

```java
private final Map<UUID, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();
private final Map<UUID, Long> pausedRemainingMs = new ConcurrentHashMap<>();
private final Map<UUID, Long> timerStartedAt = new ConcurrentHashMap<>();
private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
```

**Cenário de falha:**

1. Jogador A (Pod 1) ataca → Pod 1 chama `startTimer(gameId)` → timer agendado no Pod 1
2. Jogador B (Pod 2) ataca → Pod 2 chama `cancelTimer(gameId)` → **não encontra nada no mapa local**
3. O timer no Pod 1 dispara de qualquer forma após 60s
4. `handleTimeout()` executa `game.forceSwapTurn()` → **troca de turno indevida**
5. O estado no Redis fica inconsistente com o que os jogadores veem

O mesmo vale para `pauseTimer`/`resumeTimer` no fluxo de reconexão: pausar em um pod e retomar em outro é impossível.

---

### 4.3 Tracking de desconexão e reconexão em memória

**Severidade: 🔴 CRÍTICA — causa derrota indevida (W.O. falso)**

O `GameDisconnectService` mantém três mapas locais:

```java
private final Map<String, PlayerSession> sessionMap = new ConcurrentHashMap<>();
private final Map<UUID, String> activeSessionByPlayer = new ConcurrentHashMap<>();
private final Map<UUID, ScheduledFuture<?>> reconnectTimers = new ConcurrentHashMap<>();
private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
```

**Cenário de falha:**

1. Jogador A conecta no Pod 1 e registra sessão via `/app/game/{gameId}/register`
2. Jogador A perde conexão → Pod 1 agenda timer de reconexão de 30s
3. Jogador A reconecta, mas o Ingress o roteia para o **Pod 2** (novo cookie de sessão, ou pod anterior indisponível)
4. Pod 2 chama `handleReconnect()` → tenta cancelar `reconnectTimers` → **não encontra o timer (está no Pod 1)**
5. O timer no Pod 1 expira → jogador A perde a partida por W.O., mesmo tendo reconectado

Com Sticky Session esse cenário é **menos frequente**, mas ainda ocorre em: rolling update, pod restart, escalonamento, ou expiração do cookie de afinidade.

---

### 4.4 Emitters SSE do lobby em memória

**Severidade: 🟡 ALTA — funcionalidade degradada**

O `LobbySSEService` guarda as conexões SSE em uma lista local:

```java
private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

public void notifyLobbyUpdated(){
    for (SseEmitter emitter : emitters){
        emitter.send(SseEmitter.event().name("LOBBY_UPDATED").data("..."));
    }
}
```

**Consequência:** quando uma sala é criada no Pod 2, apenas os clientes conectados ao Pod 2 recebem `LOBBY_UPDATED`. Os clientes do Pod 1 continuam vendo a lista de salas desatualizada até reconectarem (o `EventSource` reconecta a cada 5min por causa do timeout do emitter).

**Impacto:** metade dos jogadores no lobby não vê salas novas em tempo real. É degradação de experiência, não quebra funcional — mas visível.

---

### 4.5 Sessão de host de sala em memória

**Severidade: 🟡 ALTA — salas órfãs**

```java
// RoomSessionService.java
private final Map<String, UUID> sessionToRoom = new ConcurrentHashMap<>();
```

**Consequência:** se o host cria a sala no Pod 1 e o evento de disconnect é processado em outro pod (ou o pod cai), a sala não é deletada imediatamente. Ela fica órfã até o `RoomCleanupScheduler` limpar (5 minutos depois).

Com Sticky Session o disconnect normalmente chega no pod correto, então o risco é menor — mas rolling updates e restarts de pod deixam salas órfãs.

---

### 4.6 Schedulers duplicados entre réplicas

**Severidade: 🟢 BAIXA — desperdício, com um efeito colateral**

Existem dois schedulers com `@Scheduled`:

| Scheduler | Intervalo | Alvo | Seguro? |
|-----------|-----------|------|---------|
| `GameCleanupScheduler` | 5 min | Redis (via `GameStorage.removeIf`) | ✅ Idempotente |
| `RoomCleanupScheduler` | 1 min | PostgreSQL | ⚠️ Parcialmente |

Com 2 réplicas, ambos rodam em **todos os pods simultaneamente**. Como as operações são idempotentes (deletar algo já deletado é no-op), não há corrupção de dados. Porém:

- Há trabalho duplicado (dois SCANs no Redis, duas queries no Postgres a cada ciclo)
- O `RoomCleanupScheduler` chama `notifyLobbyUpdated()` após limpar — e essa notificação só alcança os clientes SSE do pod local (mesmo problema do item 4.4)

---

### 4.7 Probes de liveness e readiness ausentes

**Severidade: 🟡 ALTA — impacta rolling updates**

A configuração atual do Actuator expõe `/actuator/health`, mas **não habilita os grupos de probe**:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    health:
      enabled: true
      show-details: always
```

Sem `management.endpoint.health.probes.enabled=true`, os endpoints `/actuator/health/liveness` e `/actuator/health/readiness` retornam 404.

**Consequência:** o Kubernetes não consegue distinguir "aplicação subindo" de "aplicação pronta para receber tráfego". Durante o rolling update, o Ingress pode enviar requests para um pod que ainda está inicializando (Flyway migrando, pool de conexões aquecendo), causando erros 5xx.

Também não há tratamento de **graceful shutdown**: ao receber SIGTERM, a aplicação encerra imediatamente, cortando conexões WebSocket e SSE ativas sem aviso.

---

## 5. Soluções Propostas

### 5.1 Substituir o Simple Broker por Redis Pub/Sub

**Resolve:** [4.1](#41-broker-stomp-em-memória), [4.4](#44-emitters-sse-do-lobby-em-memória)

O Redis já está no projeto (`spring-boot-starter-data-redis`). A abordagem é usar Redis Pub/Sub como barramento entre os pods, mantendo o Simple Broker para entrega local.

**Arquitetura:**

```
Pod 1                              Redis                        Pod 2
─────                              ─────                        ─────
Jogador A ataca
    │
    ├─► salva no Redis (game:abc)
    │
    ├─► publica no canal ────────► game-events ─────────────────► listener
    │                                                                 │
    └─► SimpleBroker local                                            ├─► SimpleBroker local
        └─► Jogador A recebe                                          └─► Jogador B recebe ✅
```

**Implementação:**

Criar um publisher que envia o evento para o Redis em vez de direto ao broker local:

```java
@Service
@RequiredArgsConstructor
public class ClusterEventPublisher {

    private static final String CHANNEL = "naval-rivals:ws-events";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Publica o evento no Redis. Todos os pods (incluindo o atual)
     * recebem via listener e repassam ao seu broker local.
     */
    public void publish(String destination, Object payload) {
        try {
            var envelope = new ClusterMessage(destination, objectMapper.writeValueAsString(payload));
            redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.error("[CLUSTER] Falha ao publicar evento no Redis — destination={}", destination, e);
        }
    }

    public record ClusterMessage(String destination, String payloadJson) {}
}
```

E um listener que recebe do Redis e entrega ao broker local:

```java
@Component
@RequiredArgsConstructor
public class ClusterEventListener implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            var envelope = objectMapper.readValue(
                message.getBody(), ClusterEventPublisher.ClusterMessage.class
            );
            // Entrega aos clientes WebSocket conectados NESTE pod
            messagingTemplate.convertAndSend(
                envelope.destination(),
                objectMapper.readTree(envelope.payloadJson())
            );
        } catch (Exception e) {
            log.error("[CLUSTER] Falha ao processar evento recebido do Redis", e);
        }
    }
}
```

Registro do listener:

```java
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {

    private final ClusterEventListener clusterEventListener;
    private final LobbySSEClusterListener lobbySSEClusterListener;

    @Bean
    public RedisMessageListenerContainer listenerContainer(RedisConnectionFactory factory) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(clusterEventListener, new ChannelTopic("naval-rivals:ws-events"));
        container.addMessageListener(lobbySSEClusterListener, new ChannelTopic("naval-rivals:lobby-events"));
        return container;
    }
}
```

Depois, `GameEventPublisher`, `GameWebSocketService` e `RoomWebSocketService` passam a usar o `ClusterEventPublisher` no lugar do `SimpMessagingTemplate` direto.

**Para o SSE do lobby**, o mesmo padrão: `notifyLobbyUpdated()` publica no canal Redis, e cada pod tem um listener que faz o broadcast nos seus emitters locais.

```java
@Service
@RequiredArgsConstructor
public class LobbySSEService {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final StringRedisTemplate redisTemplate;

    /** Chamado pela aplicação — publica no cluster */
    public void notifyLobbyUpdated() {
        redisTemplate.convertAndSend("naval-rivals:lobby-events", "LOBBY_UPDATED");
    }

    /** Chamado pelo listener do Redis — faz broadcast local */
    public void broadcastLocal() {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("LOBBY_UPDATED").data("{\"event\":\"LOBBY_UPDATED\"}"));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}
```

**Alternativa considerada:** usar RabbitMQ ou ActiveMQ com `enableStompBrokerRelay()`. É a solução canônica do Spring e mais robusta (suporta ACK, persistência, heartbeat nativo), mas exige subir um broker adicional no cluster. Como o Redis já está no projeto e o volume de mensagens do jogo é baixo, Redis Pub/Sub é a opção com melhor custo-benefício aqui.

---

### 5.2 Migrar timers para Redis com locking distribuído

**Resolve:** [4.2](#42-timers-de-turno-em-memória)

O problema tem duas partes: **onde guardar o deadline** e **quem executa o timeout**.

**Parte 1 — Deadline no Redis**

Em vez de guardar o `ScheduledFuture`, guarda-se o timestamp de expiração do turno no próprio estado do jogo (ou em uma chave Redis separada):

```java
// Adicionar na entidade Game
private Instant turnDeadline;
```

Ao processar um ataque, qualquer pod calcula e salva o novo deadline:

```java
game.setTurnDeadline(Instant.now().plusSeconds(turnTimeoutSeconds));
storage.save(game);
```

**Parte 2 — Executor com lock distribuído**

Um `@Scheduled` roda em todos os pods a cada 1 segundo, varre os jogos com deadline expirado e processa o timeout — mas usando lock no Redis para garantir que apenas **um** pod processe cada timeout:

```java
@Service
@RequiredArgsConstructor
public class TurnTimeoutScheduler {

    private final GameStorage storage;
    private final StringRedisTemplate redisTemplate;
    private final GameEventPublisher eventPublisher;

    @Scheduled(fixedRate = 1000)
    public void processExpiredTurns() {
        storage.findExpiredTurns(Instant.now()).forEach(game -> {
            // SET NX EX — só um pod consegue o lock
            String lockKey = "lock:turn-timeout:" + game.getId();
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", Duration.ofSeconds(5));

            if (Boolean.TRUE.equals(acquired)) {
                handleTimeout(game);
            }
        });
    }
}
```

O `setIfAbsent` (comando `SET NX`) é atômico no Redis — apenas um pod recebe `true`, os outros ignoram. O TTL de 5s no lock evita deadlock se o pod cair durante o processamento.

**Sobre o pause/resume da reconexão:** em vez de guardar `pausedRemainingMs`, guarda-se um flag `turnPaused` e o `pausedRemainingMs` dentro do próprio `Game` no Redis. Assim qualquer pod pode pausar e retomar.

---

### 5.3 Migrar tracking de sessão para Redis

**Resolve:** [4.3](#43-tracking-de-desconexão-e-reconexão-em-memória), [4.5](#45-sessão-de-host-de-sala-em-memória)

Os mapas de sessão viram chaves no Redis:

| Mapa atual | Chave Redis proposta | TTL |
|-----------|---------------------|-----|
| `sessionMap` | `ws:session:{sessionId}` → JSON `{gameId, playerId}` | 1h |
| `activeSessionByPlayer` | `ws:player:{playerId}` → sessionId | 1h |
| `reconnectTimers` | `reconnect:deadline:{playerId}` → timestamp | 60s |
| `sessionToRoom` | `ws:room-host:{sessionId}` → roomId | 1h |

O timer de reconexão segue o mesmo padrão do timer de turno: deadline no Redis + `@Scheduled` com lock distribuído.

```java
@Service
@RequiredArgsConstructor
public class SessionRegistry {

    private static final Duration SESSION_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void registerSession(String sessionId, UUID gameId, UUID playerId) {
        try {
            var session = new PlayerSession(gameId, playerId);
            redisTemplate.opsForValue().set(
                "ws:session:" + sessionId,
                objectMapper.writeValueAsString(session),
                SESSION_TTL
            );
            redisTemplate.opsForValue().set(
                "ws:player:" + playerId, sessionId, SESSION_TTL
            );
        } catch (Exception e) {
            log.error("[SESSION] Falha ao registrar sessão — sessionId={}", sessionId, e);
        }
    }

    public Optional<PlayerSession> findBySessionId(String sessionId) {
        String json = redisTemplate.opsForValue().get("ws:session:" + sessionId);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, PlayerSession.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void removeSession(String sessionId) {
        findBySessionId(sessionId).ifPresent(s ->
            redisTemplate.delete("ws:player:" + s.playerId())
        );
        redisTemplate.delete("ws:session:" + sessionId);
    }
}
```

---

### 5.4 Eleger um líder para os schedulers de cleanup

**Resolve:** [4.6](#46-schedulers-duplicados-entre-réplicas)

Aplicar o mesmo padrão de lock distribuído nos schedulers existentes, para que apenas um pod execute cada ciclo:

```java
@Scheduled(fixedRateString = "${game.cleanup.interval-ms}")
public void cleanupAbandonedGames() {
    Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent("lock:cleanup:games", "1", Duration.ofMinutes(4));

    if (!Boolean.TRUE.equals(acquired)) {
        log.debug("[CLEANUP] Outro pod está executando o cleanup — ignorando ciclo");
        return;
    }

    // ... lógica de cleanup existente
}
```

O TTL do lock deve ser menor que o intervalo do scheduler (4min para um scheduler de 5min), garantindo que o próximo ciclo consiga adquirir o lock.

---

### 5.5 Habilitar probes e graceful shutdown

**Resolve:** [4.7](#47-probes-de-liveness-e-readiness-ausentes)

Ajustes no `application.yaml`:

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true              # Habilita /health/liveness e /health/readiness
      group:
        readiness:
          include: db, redis       # Só está "ready" se banco e Redis respondem
        liveness:
          include: ping
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics

server:
  shutdown: graceful               # Aguarda requests em andamento antes de encerrar

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

Com `shutdown: graceful` e `timeout-per-shutdown-phase: 30s`, ao receber SIGTERM a aplicação:
1. Para de aceitar novas conexões
2. Aguarda até 30s para finalizar requests em andamento
3. Encerra

Isso combinado com o `terminationGracePeriodSeconds` do Kubernetes evita cortar partidas em andamento durante rolling updates.

---

### 5.6 Resumo de soluções por problema

| # | Problema | Severidade | Solução | Esforço |
|---|----------|-----------|---------|---------|
| 4.1 | Broker STOMP em memória | 🔴 Crítica | Redis Pub/Sub como barramento entre pods | Alto |
| 4.2 | Timers de turno em memória | 🔴 Crítica | Deadline no Redis + `@Scheduled` com lock `SET NX` | Alto |
| 4.3 | Tracking de sessão em memória | 🔴 Crítica | Mapas de sessão → chaves Redis com TTL | Médio |
| 4.4 | Emitters SSE em memória | 🟡 Alta | Redis Pub/Sub + broadcast local por pod | Baixo |
| 4.5 | Sessão de host em memória | 🟡 Alta | `sessionToRoom` → chave Redis | Baixo |
| 4.6 | Schedulers duplicados | 🟢 Baixa | Lock distribuído no Redis | Baixo |
| 4.7 | Probes ausentes | 🟡 Alta | Configuração no `application.yaml` | Trivial |

---

## 6. Plano de Implementação

Ordem sugerida, priorizando o que desbloqueia o funcionamento da partida.

### Fase 1 — Configuração (sem código)

| Item | Descrição |
|------|-----------|
| 1 | Habilitar probes de liveness/readiness no `application.yaml` |
| 2 | Habilitar `server.shutdown: graceful` |
| 3 | Configurar Sticky Session no Ingress |
| 4 | Definir `terminationGracePeriodSeconds` no Deployment |

**Resultado:** rolling updates seguros. A aplicação ainda não funciona corretamente com 2 réplicas, mas o deploy fica estável.

### Fase 2 — Barramento de eventos (desbloqueia a partida)

| Item | Descrição |
|------|-----------|
| 5 | Criar `ClusterEventPublisher` e `ClusterEventListener` |
| 6 | Registrar `RedisMessageListenerContainer` |
| 7 | Refatorar `GameEventPublisher`, `GameWebSocketService`, `RoomWebSocketService` para publicar via Redis |
| 8 | Refatorar `LobbySSEService` para broadcast via Redis |

**Resultado:** jogadores em pods diferentes passam a ver os eventos um do outro. A partida funciona.

### Fase 3 — Estado de sessão e timers

| Item | Descrição |
|------|-----------|
| 9 | Criar `SessionRegistry` (Redis) e migrar `GameDisconnectService` |
| 10 | Migrar `RoomSessionService` para Redis |
| 11 | Adicionar `turnDeadline` na entidade `Game` |
| 12 | Criar `TurnTimeoutScheduler` com lock distribuído |
| 13 | Remover os `ScheduledExecutorService` locais |

**Resultado:** reconexão e timers funcionam independente do pod.

### Fase 4 — Ajustes finais

| Item | Descrição |
|------|-----------|
| 14 | Adicionar lock distribuído nos schedulers de cleanup |
| 15 | Rodar os testes de carga (`load-tests/`) contra o cluster com 2 réplicas |
| 16 | Validar partida completa com jogadores forçados em pods diferentes |

---

## 7. Configuração Kubernetes

### 7.1 Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: navalrivals-api
spec:
  replicas: 2
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0        # Garante que sempre haja 2 pods disponíveis
  selector:
    matchLabels:
      app: navalrivals-api
  template:
    metadata:
      labels:
        app: navalrivals-api
    spec:
      terminationGracePeriodSeconds: 45   # > timeout-per-shutdown-phase (30s)
      containers:
        - name: api
          image: navalrivals-api:latest
          ports:
            - containerPort: 8080
          envFrom:
            - secretRef:
                name: navalrivals-secrets
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 5
            failureThreshold: 3
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "1000m"
```

### 7.2 Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: navalrivals-api
spec:
  type: ClusterIP
  selector:
    app: navalrivals-api
  ports:
    - port: 80
      targetPort: 8080
```

### 7.3 Ingress com Sticky Session (NGINX Ingress Controller)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: navalrivals-api
  annotations:
    # Sticky session via cookie
    nginx.ingress.kubernetes.io/affinity: "cookie"
    nginx.ingress.kubernetes.io/affinity-mode: "persistent"
    nginx.ingress.kubernetes.io/session-cookie-name: "navalrivals-pod"
    nginx.ingress.kubernetes.io/session-cookie-max-age: "3600"
    nginx.ingress.kubernetes.io/session-cookie-path: "/"

    # WebSocket e SSE precisam de timeouts longos
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"

    # SSE não deve ser bufferizado pelo proxy
    nginx.ingress.kubernetes.io/proxy-buffering: "off"
spec:
  ingressClassName: nginx
  rules:
    - host: api.navalrivals.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: navalrivals-api
                port:
                  number: 80
```

**Notas importantes sobre as annotations:**

| Annotation | Motivo |
|-----------|--------|
| `affinity: cookie` | Mantém o cliente no mesmo pod (essencial para WebSocket/SSE) |
| `proxy-read-timeout: 3600` | Sem isso o NGINX corta WebSocket/SSE após 60s (default) |
| `proxy-buffering: off` | SSE precisa de streaming sem buffer, senão os eventos chegam em lote |
| `maxUnavailable: 0` | Durante rolling update sempre há pod disponível para receber tráfego |

---

## 8. Conclusão

### O Sticky Session resolve parte do problema

O Sticky Session é **necessário** — sem ele, conexões WebSocket e SSE migrariam de pod e quebrariam constantemente. Mas ele **não é suficiente**, porque não garante que os dois jogadores de uma mesma partida caiam no mesmo pod.

### Estado atual da aplicação

| Aspecto | Situação |
|---------|----------|
| Autenticação stateless (JWT) | ✅ Pronto para escalar |
| Estado da partida no Redis | ✅ Pronto para escalar |
| Dados persistentes (PostgreSQL) | ✅ Pronto para escalar |
| Entrega de eventos WebSocket | ❌ Bloqueador crítico |
| Timers de turno | ❌ Bloqueador crítico |
| Tracking de reconexão | ❌ Bloqueador crítico |
| Broadcast SSE do lobby | ⚠️ Degradação |
| Probes / graceful shutdown | ⚠️ Ausente |

A decisão de mover o estado da partida para o Redis (documentada na seção 6.7 do documento de observabilidade) foi o passo mais importante nessa direção e já está feita. O que falta é resolver a **entrega de mensagens** e os **timers**, que continuam presos à memória de cada JVM.

### Recomendação

**Não subir para 2 réplicas antes de concluir a Fase 2** do plano de implementação. Com o barramento Redis Pub/Sub funcionando, as partidas passam a funcionar entre pods — que é o requisito mínimo. As Fases 3 e 4 corrigem os casos de borda (reconexão durante rolling update, timers órfãos) e podem ser implementadas em sequência.

Enquanto a Fase 2 não estiver pronta, rodar com **1 réplica** é mais seguro que rodar com 2, mesmo com Sticky Session ativo.
