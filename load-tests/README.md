# 🚀 Naval Rivals — Testes de Carga (k6)

## Pré-requisitos

1. **k6 instalado:** `winget install grafana.k6`
2. **Aplicação rodando:** `./mvnw spring-boot:run`
3. **Docker Compose up:** `docker-compose up -d` (Prometheus, Grafana, Redis, etc.)
4. **PostgreSQL local** com o banco `naval_rivals_db` criado

## Executar testes

### REST Endpoints (Login, Ranking, Rooms, Matches)
```bash
k6 run load-tests/rest-endpoints.js
```

### WebSocket STOMP (Conexões de sala e jogo)
```bash
k6 run load-tests/websocket-stomp.js
```

### SSE Lobby (Conexões simultâneas de lobby)
```bash
k6 run load-tests/sse-lobby.js
```

### Fluxo Completo de Jogo (Login → Sala → Navios → Estado)
```bash
k6 run load-tests/game-flow.js
```

## Enviar métricas para Prometheus/Grafana

Para visualizar as métricas do k6 no Grafana, use o output Prometheus Remote Write:

```bash
# REST
k6 run --out experimental-prometheus-rw load-tests/rest-endpoints.js

# WebSocket
k6 run --out experimental-prometheus-rw load-tests/websocket-stomp.js

# SSE
k6 run --out experimental-prometheus-rw load-tests/sse-lobby.js

# Game Flow
k6 run --out experimental-prometheus-rw load-tests/game-flow.js
```

Por padrão envia para `http://localhost:9090/api/v1/write`.
Para customizar:

```bash
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write k6 run --out experimental-prometheus-rw load-tests/rest-endpoints.js
```

## Configuração customizada

Todos os scripts aceitam variáveis de ambiente:

```bash
# Apontar para outro host
k6 run -e BASE_URL=http://192.168.1.100:8080 load-tests/rest-endpoints.js

# WebSocket URL customizada
k6 run -e BASE_URL=http://192.168.1.100:8080 -e WS_URL=ws://192.168.1.100:8080/ws load-tests/websocket-stomp.js
```

## Dashboard Grafana para k6

1. Acesse o Grafana: http://localhost:3000
2. Vá em **Dashboards → Import**
3. Use o ID: **19665** (k6 Prometheus — oficial)
4. Selecione o datasource Prometheus

## Estrutura dos testes

```
load-tests/
├── rest-endpoints.js    # Endpoints REST autenticados (login, ranking, matches, rooms)
├── websocket-stomp.js   # Conexões WebSocket STOMP (subscribe, register)
├── sse-lobby.js         # Conexões SSE simultâneas no lobby
├── game-flow.js         # Fluxo completo: login → sala → navios → estado
└── README.md            # Este arquivo
```

## Métricas customizadas monitoradas

| Métrica | Script | Descrição |
|---------|--------|-----------|
| `login_duration` | rest-endpoints | Tempo de login |
| `auth_endpoint_duration` | rest-endpoints | Latência de endpoints autenticados |
| `ws_connect_duration` | websocket-stomp | Tempo de conexão WebSocket |
| `ws_messages_sent/received` | websocket-stomp | Throughput de mensagens STOMP |
| `sse_connect_duration` | sse-lobby | Tempo de conexão SSE |
| `sse_connections_opened` | sse-lobby | Total de conexões SSE abertas |
| `ship_placement_duration` | game-flow | Latência do posicionamento de navios |
| `game_flow_duration` | game-flow | Tempo total do fluxo de jogo |
