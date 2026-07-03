# Naval Rivals API - Complete Documentation

## Table of Contents

1. [Base URL & Server Info](#base-url--server-info)
2. [Authentication Flow](#authentication-flow)
3. [Error Response Format](#error-response-format)
4. [Enums](#enums)
5. [HTTP Endpoints](#http-endpoints)
   - [Auth](#auth)
   - [Users](#users)
   - [Rooms](#rooms)
   - [Games](#games)
   - [Ranking](#ranking)
6. [WebSocket Connection](#websocket-connection)
7. [WebSocket Events](#websocket-events)
   - [Room Events](#room-events)
   - [Placement Events](#placement-events)
   - [Game Events](#game-events)
8. [Complete Game Flow](#complete-game-flow)

---

## Base URL & Server Info

- **Base URL:** `http://localhost:8080` (default Spring Boot port)
- **CORS Allowed Origin:** `http://localhost:5173`
- **Encoding:** UTF-8
- **Auth:** JWT Bearer Token (HMAC256, 24h expiration)
- **Issuer:** `naval-rivals-api`

---

## Authentication Flow

1. **Register** or **Login** via `/auth/register` or `/auth/login`
2. Receive a JWT token in the response
3. Include the token in all authenticated requests:
   ```
   Authorization: Bearer <token>
   ```
4. Token expires after **24 hours**
5. For WebSocket connections, send the token in the STOMP CONNECT frame header:
   ```
   Authorization: Bearer <token>
   ```

---

## Error Response Format

### Standard Error

```json
{
  "message": "Descriptive error message",
  "status": 404,
  "details": null
}
```

### Validation Error

```json
{
  "message": "Erro de validação dos campos",
  "status": 400,
  "details": [
    {
      "field": "email",
      "message": "Formato de e-mail inválido"
    },
    {
      "field": "password",
      "message": "Precisa ter no mínimo 6 caracteres"
    }
  ]
}
```

### HTTP Status Codes Used

| Status | When |
|--------|------|
| 400 | Validation errors, invalid UUID, invalid cell, player without permission |
| 401 | Missing/invalid token, wrong credentials |
| 404 | Resource not found |
| 409 | User already exists, room full, invalid game status |
| 500 | Internal security/token errors |

---

## Enums

### GameStatus
```
WAITING_OPPONENT | PLACING_SHIPS | IN_PROGRESS | FINISHED
```

### RoomStatus
```
WAITING | FULL | PLACING_SHIPS | IN_PROGRESS | FINISHED
```

### ShipType
| Value | Size (cells) |
|-------|-------------|
| CARRIER | 5 |
| BATTLESHIP | 4 |
| CRUISER | 3 |
| SUBMARINE | 3 |
| DESTROYER | 2 |

---

## HTTP Endpoints

### Auth

#### POST /auth/register
**Authentication:** None (public)

**Request Body:**
```json
{
  "nickname": "PlayerOne",
  "email": "player@example.com",
  "password": "secret123",
  "passwordConfirmation": "secret123"
}
```

| Field | Type | Validations |
|-------|------|------------|
| nickname | String | Required, max 150 chars |
| email | String | Required, valid email format |
| password | String | Required, min 6, max 254 chars |
| passwordConfirmation | String | Required, must match password |

**Response: 201 Created**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nickname": "PlayerOne",
  "email": "player@example.com",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Business Rules:**
- Email must be unique
- Nickname must be unique
- Password and passwordConfirmation must match
- A Stats record is automatically created for the user

**Errors:**
- 409: "Usuário já cadastrado" (email taken)
- 409: "Já existe um usuário com esse apelido" (nickname taken)
- 400: "As senhas não coincidem"

---

#### POST /auth/login
**Authentication:** None (public)

**Request Body:**
```json
{
  "email": "player@example.com",
  "password": "secret123"
}
```

| Field | Type | Validations |
|-------|------|------------|
| email | String | Required, valid email format |
| password | String | Required |

**Response: 200 OK**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nickname": "PlayerOne",
  "email": "player@example.com",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Errors:**
- 401: "E-mail ou senha incorretos"

---

### Users

#### GET /users/me
**Authentication:** Required

**Response: 200 OK**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nickname": "PlayerOne",
  "email": "player@example.com",
  "stats": {
    "totalGames": 15,
    "victories": 10,
    "defeats": 5,
    "winRate": "67%"
  }
}
```

---

#### GET /users/{id}
**Authentication:** None (public)

**Path Parameters:**
- `id` (UUID) — User ID

**Response: 200 OK**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nickname": "PlayerOne",
  "email": "player@example.com",
  "stats": {
    "totalGames": 15,
    "victories": 10,
    "defeats": 5,
    "winRate": "67%"
  }
}
```

**Errors:**
- 404: "Usuário não encontrado"
- 400: "UUID inválido" (malformed UUID)

---

#### GET /users/me/matches
**Authentication:** Required

**Query Parameters:**
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| page | int | 0 | Page number (0-indexed) |
| size | int | 10 | Page size |

**Response: 200 OK** (Paginated)
```json
{
  "content": [
    {
      "gameId": "660e8400-e29b-41d4-a716-446655440000",
      "roomCode": "NR-A1B2",
      "status": "FINISHED",
      "opponentNickname": "Rival",
      "opponentId": "770e8400-e29b-41d4-a716-446655440000",
      "victory": true,
      "durationSeconds": 420,
      "myStats": {
        "shots": 32,
        "hits": 17,
        "misses": 15,
        "shipsDestroyed": 5
      },
      "opponentStats": {
        "shots": 28,
        "hits": 12,
        "misses": 16,
        "shipsDestroyed": 3
      },
      "finishedAt": "2026-07-03T17:00:00Z"
    }
  ],
  "pageable": { ... },
  "totalElements": 25,
  "totalPages": 3,
  "number": 0,
  "size": 10
}
```

---

#### PATCH /users/me/nickname
**Authentication:** Required

**Request Body:**
```json
{
  "nickname": "NewNickname"
}
```

| Field | Type | Validations |
|-------|------|------------|
| nickname | String | Required, max 150 chars |

**Response: 200 OK**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "nickname": "NewNickname",
  "email": "player@example.com",
  "stats": { ... }
}
```

**Errors:**
- 409: "Já existe um usuário com esse apelido"

---

#### PATCH /users/me/password
**Authentication:** Required

**Request Body:**
```json
{
  "currentPassword": "oldPassword123",
  "newPassword": "newPassword456",
  "passwordConfirmation": "newPassword456"
}
```

| Field | Type | Validations |
|-------|------|------------|
| currentPassword | String | Required |
| newPassword | String | Required, min 6, max 254 chars |
| passwordConfirmation | String | Required, must match newPassword |

**Response: 204 No Content**

**Errors:**
- 401: "Senha atual incorreta"
- 401: "Nova senha não pode ser igual a atual"
- 400: "As senhas não coincidem"



---

### Rooms

#### POST /rooms
**Authentication:** Required

Creates a new room. The authenticated user becomes the host.

**Request Body:** None

**Response: 201 Created**
```json
{
  "id": "880e8400-e29b-41d4-a716-446655440000",
  "code": "NR-A1B2",
  "status": "WAITING",
  "host": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "nickname": "PlayerOne"
  },
  "opponent": null,
  "createdAt": "2026-07-03T14:00:00Z"
}
```

**Business Rules:**
- Room code is auto-generated with format `NR-XXXX` (4 alphanumeric chars)
- Room starts with status `WAITING`
- The creator is the `host`

---

#### POST /rooms/join
**Authentication:** Required

Join an existing room by code.

**Request Body:**
```json
{
  "code": "NR-A1B2"
}
```

| Field | Type | Validations |
|-------|------|------------|
| code | String | Required (not blank) |

**Response: 200 OK**
```json
{
  "id": "880e8400-e29b-41d4-a716-446655440000",
  "code": "NR-A1B2",
  "status": "FULL",
  "host": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "nickname": "PlayerOne"
  },
  "opponent": {
    "id": "770e8400-e29b-41d4-a716-446655440000",
    "nickname": "PlayerTwo"
  },
  "createdAt": "2026-07-03T14:00:00Z"
}
```

**Business Rules:**
- Code lookup is case-insensitive (converted to uppercase)
- Host cannot join their own room
- Room must not be full
- When an opponent joins:
  1. Room status changes to `FULL`
  2. A Game is automatically created (in-memory)
  3. Both players join the game (status becomes `PLACING_SHIPS`)
  4. WebSocket events `PLAYER_JOINED` and `ROOM_READY` are published
  5. The response includes the `gameId` implicitly (via the room's `gameId`)

**Errors:**
- 404: "Sala não encontrada"
- 400: "Não pode entrar na própria sala"
- 409: "Sala já está cheia"

---

#### GET /rooms/{roomId}
**Authentication:** Required

**Path Parameters:**
- `roomId` (UUID) — Room ID

**Response: 200 OK**
```json
{
  "id": "880e8400-e29b-41d4-a716-446655440000",
  "code": "NR-A1B2",
  "status": "FULL",
  "host": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "nickname": "PlayerOne"
  },
  "opponent": {
    "id": "770e8400-e29b-41d4-a716-446655440000",
    "nickname": "PlayerTwo"
  },
  "createdAt": "2026-07-03T14:00:00Z"
}
```

**Errors:**
- 404: "Sala não encontrada"

---

#### DELETE /rooms/{roomId}
**Authentication:** Required

Leave a room.

**Path Parameters:**
- `roomId` (UUID) — Room ID

**Response: 204 No Content**

**Business Rules:**
- If the host leaves: the entire room is deleted (and any associated game removed from memory)
- If the opponent leaves: opponent is removed, room goes back to `WAITING`, game is removed from memory
- A `PLAYER_LEFT` WebSocket event is published

**Errors:**
- 404: "Sala não encontrada"
- 400: "Jogador não pertence a essa sala"

---

### Games

#### POST /games/{gameId}/ships
**Authentication:** Required

Place ships on the board. Each player must call this once during the `PLACING_SHIPS` phase.

**Path Parameters:**
- `gameId` (UUID) — Game ID

**Request Body:**
```json
{
  "ships": [
    {
      "type": "CARRIER",
      "positions": [
        {"row": 0, "col": 0},
        {"row": 0, "col": 1},
        {"row": 0, "col": 2},
        {"row": 0, "col": 3},
        {"row": 0, "col": 4}
      ]
    },
    {
      "type": "BATTLESHIP",
      "positions": [
        {"row": 2, "col": 0},
        {"row": 2, "col": 1},
        {"row": 2, "col": 2},
        {"row": 2, "col": 3}
      ]
    },
    {
      "type": "CRUISER",
      "positions": [
        {"row": 4, "col": 0},
        {"row": 4, "col": 1},
        {"row": 4, "col": 2}
      ]
    },
    {
      "type": "SUBMARINE",
      "positions": [
        {"row": 6, "col": 0},
        {"row": 6, "col": 1},
        {"row": 6, "col": 2}
      ]
    },
    {
      "type": "DESTROYER",
      "positions": [
        {"row": 8, "col": 0},
        {"row": 8, "col": 1}
      ]
    }
  ]
}
```

**Ship Fleet Composition (exactly 5 ships):**

| Ship | Type | Size |
|------|------|------|
| Aircraft Carrier | CARRIER | 5 |
| Battleship | BATTLESHIP | 4 |
| Cruiser | CRUISER | 3 |
| Submarine | SUBMARINE | 3 |
| Destroyer | DESTROYER | 2 |

**Position Format:**
- `row`: 0-9 (A-J on the grid)
- `col`: 0-9 (1-10 on the grid)

**Response: 200 OK** (empty body)

**Validation Rules:**
- Exactly 5 ships required
- Fleet must contain exactly 1 of each type
- Each ship must have the correct number of positions for its type
- All positions must be within the 10×10 grid (0-9)
- Ships must be in a straight line (horizontal or vertical)
- Ship positions must be contiguous (adjacent cells)
- No overlapping positions between ships

**Business Rules:**
- Game must be in `PLACING_SHIPS` status
- Player must belong to the game
- Player can only place ships once
- When the first player places → `OPPONENT_READY` WebSocket event is published
- When the second player places → game transitions to `IN_PROGRESS`, `GAME_STARTED` event is published, turn timer starts (60s)

**Errors:**
- 404: "Partida não encontrada"
- 400: "Jogador não pertence a essa partida"
- 409: "Partida não está na fase de posicionamento"
- 400: "Jogador já posicionou seus navios"
- 400: Various ship placement validation errors

---

#### GET /games/{gameId}/state
**Authentication:** Required

Get the current game state from the player's perspective.

**Path Parameters:**
- `gameId` (UUID) — Game ID

**Response: 200 OK**
```json
{
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS",
  "currentTurn": "550e8400-e29b-41d4-a716-446655440000",
  "myPlayerId": "550e8400-e29b-41d4-a716-446655440000",
  "myShips": [
    {
      "type": "CARRIER",
      "positions": [
        {"row": 0, "col": 0},
        {"row": 0, "col": 1},
        {"row": 0, "col": 2},
        {"row": 0, "col": 3},
        {"row": 0, "col": 4}
      ],
      "sunk": false
    },
    {
      "type": "DESTROYER",
      "positions": [
        {"row": 8, "col": 0},
        {"row": 8, "col": 1}
      ],
      "sunk": true
    }
  ],
  "myShotsReceived": [
    {"position": {"row": 0, "col": 0}, "hit": true},
    {"position": {"row": 5, "col": 5}, "hit": false}
  ],
  "myShotsMade": [
    {"position": {"row": 3, "col": 4}, "hit": true},
    {"position": {"row": 7, "col": 2}, "hit": false}
  ]
}
```

**Business Rules:**
- Player must belong to the game
- `myShips` = the player's own ships with positions and sunk status
- `myShotsReceived` = shots the opponent made on the player's board
- `myShotsMade` = shots the player made on the opponent's board
- This endpoint is useful for reconnection (rebuild the entire board state)

**Errors:**
- 404: "Partida não encontrada"
- 400: "Jogador não pertence a essa partida"

---

#### GET /games/{gameId}/result
**Authentication:** Required

Get the persisted result of a finished game.

**Path Parameters:**
- `gameId` (UUID) — Game ID

**Response: 200 OK**
```json
{
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "roomCode": "NR-A1B2",
  "status": "FINISHED",
  "winner": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "nickname": "PlayerOne",
    "email": "player@example.com",
    "stats": {
      "totalGames": 16,
      "victories": 11,
      "defeats": 5,
      "winRate": "69%"
    }
  },
  "loser": {
    "id": "770e8400-e29b-41d4-a716-446655440000",
    "nickname": "PlayerTwo",
    "email": "player2@example.com",
    "stats": {
      "totalGames": 10,
      "victories": 4,
      "defeats": 6,
      "winRate": "40%"
    }
  },
  "durationSeconds": 420,
  "winnerStats": {
    "shots": 32,
    "hits": 17,
    "misses": 15,
    "shipsDestroyed": 5
  },
  "loserStats": {
    "shots": 28,
    "hits": 12,
    "misses": 16,
    "shipsDestroyed": 3
  },
  "finishedAt": "2026-07-03T17:00:00Z"
}
```

**Errors:**
- 404: "Resultado da partida não encontrado"

---

### Ranking

#### GET /ranking
**Authentication:** Required

Get the global ranking, sorted by victories.

**Query Parameters (Spring Pageable):**
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| page | int | 0 | Page number |
| size | int | 20 | Page size |
| sort | string | — | Sort field |

**Response: 200 OK** (Paginated)
```json
{
  "content": [
    {
      "position": 1,
      "userId": "550e8400-e29b-41d4-a716-446655440000",
      "nickname": "ProPlayer",
      "victories": 50,
      "totalGames": 60,
      "winRate": "83%"
    },
    {
      "position": 2,
      "userId": "770e8400-e29b-41d4-a716-446655440000",
      "nickname": "GoodPlayer",
      "victories": 40,
      "totalGames": 55,
      "winRate": "73%"
    }
  ],
  "pageable": { ... },
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20
}
```



---

## WebSocket Connection

### Connection Info

| Property | Value |
|----------|-------|
| Endpoint | `ws://localhost:8080/ws` |
| Protocol | STOMP over WebSocket |
| App Prefix | `/app` (for sending messages to server) |
| Broker Prefixes | `/topic`, `/queue` (for subscribing) |

### How to Connect

```javascript
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: {
    Authorization: `Bearer ${token}`
  },
  onConnect: () => {
    console.log('Connected!');
    // Subscribe to topics here
  },
  onStompError: (frame) => {
    // Connection refused (invalid token, etc.)
    console.error('STOMP error:', frame.headers['message']);
  }
});

client.activate();
```

### Authentication

- The JWT token MUST be sent in the STOMP CONNECT frame as a native header: `Authorization: Bearer <token>`
- The server intercepts the CONNECT command, validates the token, and sets the user principal for the session
- If the token is invalid or missing, the server sends a STOMP ERROR frame and the connection is refused
- Once authenticated, the user identity persists for the entire WebSocket session (no need to re-authenticate on each message)

### Important: After connecting to a game, ALWAYS send a register message

```javascript
client.publish({
  destination: '/app/game/{gameId}/register',
  body: '{}'
});
```

This registers the WebSocket session for disconnect tracking. Without it, the server won't detect if you disconnect.

---

## WebSocket Events

### Room Events

**Topic:** `/topic/room/{roomId}`

Subscribe to this topic after creating or joining a room to receive real-time updates.

---

#### PLAYER_JOINED

Published when a player joins the room.

```json
{
  "event": "PLAYER_JOINED",
  "roomId": "880e8400-e29b-41d4-a716-446655440000",
  "userId": "770e8400-e29b-41d4-a716-446655440000",
  "nickname": "PlayerTwo",
  "gameId": null
}
```

---

#### ROOM_READY

Published when the room is full (2 players) and the game has been created. The frontend should use the `gameId` to navigate to the game/placement phase.

```json
{
  "event": "ROOM_READY",
  "roomId": "880e8400-e29b-41d4-a716-446655440000",
  "userId": "770e8400-e29b-41d4-a716-446655440000",
  "nickname": "PlayerTwo",
  "gameId": "660e8400-e29b-41d4-a716-446655440000"
}
```

---

#### PLAYER_LEFT

Published when a player leaves the room.

```json
{
  "event": "PLAYER_LEFT",
  "roomId": "880e8400-e29b-41d4-a716-446655440000",
  "userId": "770e8400-e29b-41d4-a716-446655440000",
  "nickname": "PlayerTwo",
  "gameId": null
}
```

---

### Placement Events

**Topic:** `/topic/game/{gameId}/placement`

Subscribe to this topic during the ship placement phase.

---

#### OPPONENT_READY

Published when the OTHER player finishes placing their ships (but you haven't placed yours yet).

```json
{
  "event": "OPPONENT_READY",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "playerId": "770e8400-e29b-41d4-a716-446655440000",
  "firstTurn": null,
  "turnTimeout": null
}
```

**Frontend action:** Show indicator that opponent is ready. Player still needs to place their ships.

---

#### GAME_STARTED

Published when BOTH players have placed their ships. The game transitions to `IN_PROGRESS`.

```json
{
  "event": "GAME_STARTED",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "playerId": "770e8400-e29b-41d4-a716-446655440000",
  "firstTurn": "550e8400-e29b-41d4-a716-446655440000",
  "turnTimeout": 60
}
```

| Field | Description |
|-------|-------------|
| playerId | The player who was the last to place (triggered the start) |
| firstTurn | UUID of the player who goes first (randomly chosen) |
| turnTimeout | Seconds per turn (always 60) |

**Frontend action:** Navigate to the battle screen. Start the turn countdown timer if it's your turn.

---

### Game Events

**Topic:** `/topic/game/{gameId}/events`

Subscribe to this topic during the battle phase. All game events are published here.

Each event has this structure:
```json
{
  "event": "EVENT_TYPE",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "payload": { ... }
}
```

---

#### ATTACK_RESULT

Published after each attack. Both players receive this.

```json
{
  "event": "ATTACK_RESULT",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "attackerId": "550e8400-e29b-41d4-a716-446655440000",
    "cell": "C4",
    "hit": true
  }
}
```

| Payload Field | Type | Description |
|---------------|------|-------------|
| attackerId | UUID | Who made the attack |
| cell | String | Cell notation (e.g., "A1", "J10") |
| hit | boolean | Whether the shot hit a ship |

---

#### TURN_CHANGE

Published after each attack or timeout. Informs who plays next.

```json
{
  "event": "TURN_CHANGE",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "nextTurn": "770e8400-e29b-41d4-a716-446655440000",
    "turnTimeout": 60
  }
}
```

| Payload Field | Type | Description |
|---------------|------|-------------|
| nextTurn | UUID | Player who should attack next |
| turnTimeout | int | Seconds for this turn (always 60) |

**Frontend action:** Reset and start the countdown timer. Enable/disable attack controls based on whose turn it is.

---

#### TURN_TIMEOUT

Published when a player's turn timer expires (they didn't attack in 60s). The turn passes automatically.

```json
{
  "event": "TURN_TIMEOUT",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "timedOutPlayer": "550e8400-e29b-41d4-a716-446655440000",
    "nextTurn": "770e8400-e29b-41d4-a716-446655440000"
  }
}
```

**Frontend action:** Show a visual indicator that the turn was lost due to timeout.

---

#### SHIP_SUNK

Published when a ship is completely destroyed.

```json
{
  "event": "SHIP_SUNK",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "ownerId": "770e8400-e29b-41d4-a716-446655440000",
    "shipType": "DESTROYER",
    "positions": ["H3", "H4"]
  }
}
```

| Payload Field | Type | Description |
|---------------|------|-------------|
| ownerId | UUID | Owner of the sunken ship |
| shipType | String | ShipType enum value |
| positions | String[] | Cell notations of the ship's positions |

**Frontend action:** Reveal the full ship on the opponent's grid. Show sunk animation/indicator.

---

#### GAME_OVER

Published when the game ends.

```json
{
  "event": "GAME_OVER",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "winnerId": "550e8400-e29b-41d4-a716-446655440000",
    "loserId": "770e8400-e29b-41d4-a716-446655440000",
    "reason": "ALL_SHIPS_SUNK"
  }
}
```

| Payload Field | Type | Description |
|---------------|------|-------------|
| winnerId | UUID | Winner's user ID |
| loserId | UUID | Loser's user ID |
| reason | String | `"ALL_SHIPS_SUNK"` or `"OPPONENT_DISCONNECTED"` |

**Frontend action:** Show victory/defeat screen. Call `GET /games/{gameId}/result` for detailed stats.

---

#### OPPONENT_DISCONNECTED

Published when the opponent's WebSocket connection drops during an active game.

```json
{
  "event": "OPPONENT_DISCONNECTED",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "disconnectedPlayerId": "770e8400-e29b-41d4-a716-446655440000",
    "reconnectTimeout": 30
  }
}
```

| Payload Field | Type | Description |
|---------------|------|-------------|
| disconnectedPlayerId | UUID | Player who disconnected |
| reconnectTimeout | int | Seconds to reconnect (30) |

**Frontend action:** Show a "waiting for opponent" overlay with a 30-second countdown. The turn timer is paused on the server.

---

#### OPPONENT_RECONNECTED

Published when the disconnected player comes back online within the 30-second window.

```json
{
  "event": "OPPONENT_RECONNECTED",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "reconnectedPlayerId": "770e8400-e29b-41d4-a716-446655440000"
  }
}
```

**Frontend action:** Remove the "waiting" overlay. The turn timer resumes with the remaining time.

---

### Attack Topic (Legacy/Compatibility)

**Topic:** `/topic/game/{gameId}/attack`

This is a secondary topic that also receives attack results in a flat format (for backward compatibility). The `/events` topic is preferred.

```json
{
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "attackerId": "550e8400-e29b-41d4-a716-446655440000",
  "cell": "C4",
  "hit": true,
  "sunk": true,
  "shipType": "DESTROYER",
  "gameOver": false,
  "winnerId": null,
  "nextTurn": "770e8400-e29b-41d4-a716-446655440000"
}
```

---

### Sending Messages (Client → Server)

#### Send Attack

**Destination:** `/app/game/{gameId}/attack`

```json
{
  "cell": "C4"
}
```

| Field | Type | Description |
|-------|------|-------------|
| cell | String | Target cell in notation format (letter A-J + number 1-10) |

**Cell notation:**
- Row: A=row 0, B=row 1, ..., J=row 9
- Column: 1=col 0, 2=col 1, ..., 10=col 9
- Examples: `"A1"` = (0,0), `"C4"` = (2,3), `"J10"` = (9,9)

**Business rules:**
- Must be your turn
- Game must be in `IN_PROGRESS` status
- Cannot attack the same cell twice
- After a successful attack, the server publishes multiple events: ATTACK_RESULT, then either SHIP_SUNK + GAME_OVER (if game ends) or TURN_CHANGE (if game continues)

---

#### Register Session

**Destination:** `/app/game/{gameId}/register`

**Body:** empty (`{}` or no body)

**Purpose:** Registers the WebSocket session for disconnect tracking. MUST be sent after connecting and subscribing to game topics.

**When to send:**
- After initial connection to a game
- After reconnecting to a game

If a reconnection is detected (player had previously disconnected), the server automatically:
1. Cancels the 30s reconnect timeout
2. Publishes `OPPONENT_RECONNECTED`
3. Resumes the turn timer



---

## Complete Game Flow

### Step-by-Step from Start to Finish

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. AUTHENTICATION                                                │
├─────────────────────────────────────────────────────────────────┤
│ POST /auth/register  OR  POST /auth/login                        │
│ → Receive JWT token                                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. CREATE/JOIN ROOM                                              │
├─────────────────────────────────────────────────────────────────┤
│ Host: POST /rooms → gets room code (e.g., "NR-A1B2")            │
│       Subscribe: /topic/room/{roomId}                            │
│       Share code with friend                                     │
│                                                                  │
│ Opponent: POST /rooms/join {"code": "NR-A1B2"}                   │
│           Subscribe: /topic/room/{roomId}                        │
│                                                                  │
│ Both receive: PLAYER_JOINED → ROOM_READY (contains gameId)       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. CONNECT WEBSOCKET                                             │
├─────────────────────────────────────────────────────────────────┤
│ Connect to ws://localhost:8080/ws with Authorization header       │
│ Subscribe: /topic/game/{gameId}/placement                        │
│ Subscribe: /topic/game/{gameId}/events                           │
│ Subscribe: /topic/game/{gameId}/attack (optional, legacy)        │
│ Send: /app/game/{gameId}/register (IMPORTANT!)                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. PLACE SHIPS                                                   │
├─────────────────────────────────────────────────────────────────┤
│ POST /games/{gameId}/ships (5 ships, all validations apply)      │
│                                                                  │
│ If first to place → opponent receives OPPONENT_READY             │
│ If second to place → both receive GAME_STARTED                   │
│   (contains firstTurn UUID and turnTimeout: 60)                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. BATTLE PHASE (loop until game over)                           │
├─────────────────────────────────────────────────────────────────┤
│ Current turn player sends: /app/game/{gameId}/attack             │
│   {"cell": "C4"}                                                 │
│                                                                  │
│ Server publishes (in order):                                     │
│   1. ATTACK_RESULT {attackerId, cell, hit}                       │
│   2. (if sunk) SHIP_SUNK {ownerId, shipType, positions}          │
│   3a. (if game over) GAME_OVER {winnerId, loserId, reason}       │
│   3b. (if continues) TURN_CHANGE {nextTurn, turnTimeout}         │
│                                                                  │
│ If player doesn't attack within 60s:                             │
│   Server publishes: TURN_TIMEOUT → TURN_CHANGE                   │
│                                                                  │
│ If player disconnects:                                           │
│   Server publishes: OPPONENT_DISCONNECTED (30s to reconnect)     │
│   If reconnects: OPPONENT_RECONNECTED (timer resumes)            │
│   If timeout: GAME_OVER (reason: OPPONENT_DISCONNECTED)          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ 6. GAME END                                                      │
├─────────────────────────────────────────────────────────────────┤
│ Both receive GAME_OVER event                                     │
│ GET /games/{gameId}/result → detailed stats for result screen    │
│                                                                  │
│ Stats updated automatically:                                     │
│   - Winner: victories++, totalGames++                            │
│   - Loser: defeats++, totalGames++                               │
│                                                                  │
│ Game is removed from server memory after GAME_OVER               │
└─────────────────────────────────────────────────────────────────┘
```

### Reconnection Flow

If the client loses connection during a game:

1. Server detects disconnect via `SessionDisconnectEvent`
2. Server pauses the turn timer
3. Server publishes `OPPONENT_DISCONNECTED` to the remaining player (30s countdown)
4. **To reconnect:**
   - Establish a new WebSocket connection (with valid token)
   - Subscribe to game topics again
   - Send `/app/game/{gameId}/register` — this triggers reconnection logic
   - Server cancels the 30s timeout
   - Server publishes `OPPONENT_RECONNECTED`
   - Server resumes the turn timer with remaining time
5. Call `GET /games/{gameId}/state` to rebuild the full board state

### Game Cleanup

- Games in `WAITING_OPPONENT` or `PLACING_SHIPS` for more than **15 minutes** are automatically removed from memory (scheduled every 5 minutes)
- Once a game finishes, results are persisted to the database and the in-memory Game object is deleted

---

## Cell Notation Reference

The grid is 10×10. Cells are referenced as a letter (row) + number (column):

```
     1   2   3   4   5   6   7   8   9   10
A  [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
B  [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
C  [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
D  [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
E  [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
F  [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
G  [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
H  [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
I  [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
J  [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ] [ ]
```

**Conversion:**
- Letter → row index: A=0, B=1, C=2, ..., J=9
- Number → col index: 1=0, 2=1, 3=2, ..., 10=9

**Examples:**
| Cell | Position (row, col) |
|------|-------------------|
| A1 | (0, 0) |
| A10 | (0, 9) |
| C4 | (2, 3) |
| J10 | (9, 9) |
| E5 | (4, 4) |

---

## Quick Reference: All Subscriptions

| Topic | When to Subscribe | Events Received |
|-------|-------------------|-----------------|
| `/topic/room/{roomId}` | After creating/joining room | PLAYER_JOINED, ROOM_READY, PLAYER_LEFT |
| `/topic/game/{gameId}/placement` | After receiving ROOM_READY | OPPONENT_READY, GAME_STARTED |
| `/topic/game/{gameId}/events` | After ROOM_READY or GAME_STARTED | ATTACK_RESULT, TURN_CHANGE, TURN_TIMEOUT, SHIP_SUNK, GAME_OVER, OPPONENT_DISCONNECTED, OPPONENT_RECONNECTED |
| `/topic/game/{gameId}/attack` | (optional, legacy) | AttackResponse flat object |

## Quick Reference: All Messages (Client → Server)

| Destination | When to Send | Body |
|-------------|-------------|------|
| `/app/game/{gameId}/attack` | During your turn | `{"cell": "C4"}` |
| `/app/game/{gameId}/register` | After connecting to game WS | `{}` (empty) |

## Quick Reference: Endpoint Authentication

| Endpoint | Auth Required |
|----------|---------------|
| POST /auth/register | ❌ No |
| POST /auth/login | ❌ No |
| GET /users/{id} | ❌ No |
| GET /users/me | ✅ Yes |
| GET /users/me/matches | ✅ Yes |
| PATCH /users/me/nickname | ✅ Yes |
| PATCH /users/me/password | ✅ Yes |
| POST /rooms | ✅ Yes |
| POST /rooms/join | ✅ Yes |
| GET /rooms/{roomId} | ✅ Yes |
| DELETE /rooms/{roomId} | ✅ Yes |
| POST /games/{gameId}/ships | ✅ Yes |
| GET /games/{gameId}/state | ✅ Yes |
| GET /games/{gameId}/result | ✅ Yes |
| GET /ranking | ✅ Yes |
| WebSocket /ws | ✅ Yes (STOMP CONNECT header) |
| Swagger UI (/swagger-ui/**) | ❌ No |
