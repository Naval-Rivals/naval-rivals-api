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
   - [Tactical Mode Events](#tactical-mode-events)
8. [Complete Game Flow](#complete-game-flow)
9. [Torpedo Mechanic](#torpedo-mechanic)
10. [Tactical Mode](#tactical-mode)

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

### GameMode
```
CLASSIC | TACTICAL
```

### AbilityType (Tactical Mode only)
```
TORPEDO | RADAR | SHIELD | EMP_NAVAL
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
  "login": "player@example.com",
  "password": "secret123"
}
```

or using nickname:
```json
{
  "login": "PlayerOne",
  "password": "secret123"
}
```

| Field | Type | Validations | Description |
|-------|------|------------|-------------|
| login | String | Required | Email or nickname (case-sensitive) |
| password | String | Required | |

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
- 401: "Credenciais inválidas"

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

**Request Body:** (optional)
```json
{
  "gameMode": "CLASSIC"
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| gameMode | String | `"CLASSIC"` | Game mode: `"CLASSIC"` or `"TACTICAL"` |

If no body is sent, defaults to `CLASSIC` mode.

**Response: 201 Created**
```json
{
  "id": "880e8400-e29b-41d4-a716-446655440000",
  "code": "NR-A1B2",
  "status": "WAITING",
  "gameMode": "CLASSIC",
  "host": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "nickname": "PlayerOne"
  },
  "opponent": null,
  "gameId": null,
  "createdAt": "2026-07-03T14:00:00Z"
}
```

**Business Rules:**
- Room code is auto-generated with format `NR-XXXX` (4 alphanumeric chars)
- Room starts with status `WAITING`
- The creator is the `host`
- `gameId` is `null` when the room is still waiting for an opponent
- The `gameMode` determines which abilities are available during the match

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
  "gameMode": "CLASSIC",
  "host": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "nickname": "PlayerOne"
  },
  "opponent": {
    "id": "770e8400-e29b-41d4-a716-446655440000",
    "nickname": "PlayerTwo"
  },
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2026-07-03T14:00:00Z"
}
```

**Business Rules:**
- Code lookup is case-insensitive (converted to uppercase)
- Host cannot join their own room
- Room must not be full
- When an opponent joins:
  1. Room status changes to `FULL`
  2. A Game is automatically created (in-memory) with the room's `gameMode`
  3. Both players join the game (status becomes `PLACING_SHIPS`)
  4. WebSocket events `PLAYER_JOINED` and `ROOM_READY` are published
  5. The `gameId` is included in the HTTP response (so the opponent has it immediately without needing WebSocket)

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
  "gameMode": "TACTICAL",
  "host": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "nickname": "PlayerOne"
  },
  "opponent": {
    "id": "770e8400-e29b-41d4-a716-446655440000",
    "nickname": "PlayerTwo"
  },
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2026-07-03T14:00:00Z"
}
```

**Notes:**
- `gameId` will be `null` if the room is still in `WAITING` status
- `gameId` is present when the room is `FULL` and a game has been created
- Useful for reconnection scenarios (client can retrieve the gameId if lost)

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
  "gameMode": "TACTICAL",
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
  ],
  "torpedoAvailable": true,
  "abilities": {
    "radarAvailable": true,
    "shieldCharges": 2,
    "shieldActive": false,
    "empNavalAvailable": true,
    "empDisabledTurns": 0
  }
}
```

**Business Rules:**
- Player must belong to the game
- `myShips` = the player's own ships with positions and sunk status
- `myShotsReceived` = shots the opponent made on the player's board
- `myShotsMade` = shots the player made on the opponent's board
- `torpedoAvailable` = whether the player can still use their torpedo (each player has 1 per match)
- `abilities` = tactical mode abilities info (**null if gameMode is CLASSIC**)
- This endpoint is useful for reconnection (rebuild the entire board state)

**Abilities field (only present in TACTICAL mode):**

| Field | Type | Description |
|-------|------|-------------|
| radarAvailable | boolean | Whether radar can still be used (1 use) |
| shieldCharges | int | Remaining shield activations (starts at 2) |
| shieldActive | boolean | Whether a shield is currently active (blocks next incoming shot) |
| empNavalAvailable | boolean | Whether EMP can still be used (1 use) |
| empDisabledTurns | int | Turns remaining where YOUR abilities are disabled (by opponent's EMP) |

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
  "gameMode": "CLASSIC",
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
**User Topic:** `/user/topic/game/{gameId}/events` (private events, e.g., radar results)

Subscribe to both topics during the battle phase. Public events are broadcast to all players. Private events (like radar results) are sent only to the relevant player via the user topic.

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
    "hit": true,
    "attackType": "NORMAL"
  }
}
```

| Payload Field | Type | Description |
|---------------|------|-------------|
| attackerId | UUID | Who made the attack |
| cell | String | Cell notation (e.g., "A1", "J10") |
| hit | boolean | Whether the shot hit a ship |
| attackType | String | Type of attack: `"NORMAL"` or `"TORPEDO"` |

---

#### TURN_CHANGE

Published after each attack (miss) or timeout. Informs who plays next. Note: if the attack was a hit, `nextTurn` will be the same player who just attacked (they get another turn).

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
| nextTurn | UUID | Player who should attack next (can be the same player if they hit) |
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

**Frontend action:** Show victory/defeat screen. Call `GET /games/{gameId}/result` for detailed stats (only available if the game was in `IN_PROGRESS` — not available for disconnects during `PLACING_SHIPS`).

---

#### OPPONENT_DISCONNECTED

Published when the opponent's WebSocket connection drops during `PLACING_SHIPS` or `IN_PROGRESS` phase.

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

**If the opponent does NOT reconnect within 30 seconds:**
- During `IN_PROGRESS`: `GAME_OVER` is published with reason `OPPONENT_DISCONNECTED` (W.O. victory). Game result is persisted.
- During `PLACING_SHIPS`: `GAME_OVER` is published with reason `OPPONENT_DISCONNECTED`, then `PLAYER_LEFT` is published on `/topic/room/{roomId}`. The room is reset to `WAITING` status (opponent removed, gameId cleared). No game result is persisted.

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
  "nextTurn": "770e8400-e29b-41d4-a716-446655440000",
  "attackType": "NORMAL"
}
```

---

### Tactical Mode Events

These events are published on `/topic/game/{gameId}/events` only during **TACTICAL** mode games.

---

#### SHIELD_ACTIVATED

Published when a player activates their shield. Both players see this.

```json
{
  "event": "SHIELD_ACTIVATED",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "playerId": "550e8400-e29b-41d4-a716-446655440000",
    "remainingCharges": 1
  }
}
```

| Payload Field | Type | Description |
|---------------|------|-------------|
| playerId | UUID | Player who activated the shield |
| remainingCharges | int | Shield activations remaining after this use |

**Frontend action:** Show shield icon/indicator on the player's board. Opponent knows a shield is active.

---

#### SHIELD_BLOCKED

Published when an incoming shot (normal or torpedo) is blocked by an active shield. This event is published **before** the `ATTACK_RESULT` event.

```json
{
  "event": "SHIELD_BLOCKED",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "defenderId": "550e8400-e29b-41d4-a716-446655440000",
    "cell": "C4"
  }
}
```

| Payload Field | Type | Description |
|---------------|------|-------------|
| defenderId | UUID | Player whose shield blocked the shot |
| cell | String | The cell that was targeted |

**Event order when shield blocks:**
1. `SHIELD_BLOCKED` — shield consumed
2. `ATTACK_RESULT` — `hit: false` (miss, regardless of whether it would have hit a ship)
3. `TURN_CHANGE` — turn passes to opponent (since hit is false)

**Frontend action:** Show shield-block animation. The shot registers as a miss even if it would have hit a ship. After this event, the shield is consumed (`shieldActive = false`).

---

#### RADAR_USED

Published to **all players** on `/topic/game/{gameId}/events` when a player uses their radar. Notifies the opponent that radar was used, but does **NOT** reveal ship positions.

```json
{
  "event": "RADAR_USED",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "playerId": "550e8400-e29b-41d4-a716-446655440000",
    "centerCell": "E5"
  }
}
```

| Payload Field | Type | Description |
|---------------|------|-------------|
| playerId | UUID | Player who used the radar |
| centerCell | String | Center of the 3×3 scan area |

**Frontend action (opponent):** Show a visual indicator that the opponent used radar on that area.

---

#### RADAR_RESULT

Published **only to the player who used radar** on `/user/topic/game/{gameId}/events`. Contains the revealed ship positions.

> ⚠️ The frontend MUST subscribe to `/user/topic/game/{gameId}/events` to receive this event.

```json
{
  "event": "RADAR_RESULT",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "playerId": "550e8400-e29b-41d4-a716-446655440000",
    "centerCell": "E5",
    "revealedCells": ["D4", "E5", "E6"]
  }
}
```

| Payload Field | Type | Description |
|---------------|------|-------------|
| playerId | UUID | Player who used the radar |
| centerCell | String | Center of the 3×3 scan area |
| revealedCells | String[] | Cells within the 3×3 area that contain a ship (can be empty) |

**Frontend action (radar user):** Highlight the 3×3 scan area. Mark revealed cells (ships detected). This consumes the player's turn.

---

#### EMP_ACTIVATED

Published when a player uses their EMP Naval. Both players see this.

```json
{
  "event": "EMP_ACTIVATED",
  "gameId": "660e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "playerId": "550e8400-e29b-41d4-a716-446655440000",
    "targetId": "770e8400-e29b-41d4-a716-446655440000",
    "disabledTurns": 2
  }
}
```

| Payload Field | Type | Description |
|---------------|------|-------------|
| playerId | UUID | Player who used EMP |
| targetId | UUID | Player whose abilities are now disabled |
| disabledTurns | int | Number of turns abilities are disabled (always 2) |

**Frontend action:** Show EMP effect on opponent. Disable ability buttons for the affected player. This consumes the attacker's turn.

---

### Sending Messages (Client → Server)

#### Send Attack

**Destination:** `/app/game/{gameId}/attack`

```json
{
  "cell": "C4",
  "type": "NORMAL"
}
```

| Field | Type | Description |
|-------|------|-------------|
| cell | String | Target cell in notation format (letter A-J + number 1-10) |
| type | String | (Optional) Attack type: `"NORMAL"` (default) or `"TORPEDO"` |

**Cell notation:**
- Row: A=row 0, B=row 1, ..., J=row 9
- Column: 1=col 0, 2=col 1, ..., 10=col 9
- Examples: `"A1"` = (0,0), `"C4"` = (2,3), `"J10"` = (9,9)

**Business rules:**
- Must be your turn
- Game must be in `IN_PROGRESS` status
- Cannot attack the same cell twice
- **If the shot HITS:** the same player keeps the turn (plays again)
- **If the shot MISSES:** the turn passes to the opponent
- After a successful attack, the server publishes multiple events: ATTACK_RESULT, then either SHIP_SUNK + GAME_OVER (if game ends) or TURN_CHANGE (if turn changes or same player continues)

**Torpedo rules:**
- Each player has **1 torpedo per match**
- Send `"type": "TORPEDO"` to fire a torpedo
- If the torpedo **hits** any position of a ship, the **entire ship is sunk instantly** (regardless of ship size)
- If the torpedo **misses**, it behaves like a normal shot (registers miss, turn passes to opponent)
- If the torpedo has already been used, the server returns an error: `"Torpedo já foi utilizado nesta partida"`
- If `type` is omitted or `null`, defaults to `"NORMAL"`

**Errors:**
- `"Não é o turno desse jogador"` — not your turn
- `"Partida não está em andamento"` — game not in progress
- `"Jogador já atirou nessa posição"` — cell already attacked
- `"Torpedo já foi utilizado nesta partida"` — torpedo already used this match

---

#### Use Ability (Tactical Mode only)

**Destination:** `/app/game/{gameId}/ability`

```json
{
  "ability": "RADAR",
  "cell": "E5"
}
```

| Field | Type | Description |
|-------|------|-------------|
| ability | String | Required. One of: `"SHIELD"`, `"RADAR"`, `"EMP_NAVAL"` |
| cell | String | Required for RADAR (center of 3×3 scan). Not needed for other abilities. |

**Turn consumption rules:**
| Ability | Consumes Turn | Description |
|---------|---------------|-------------|
| SHIELD | ❌ No | Activate before attacking — you still attack this turn |
| RADAR | ✅ Yes | Replaces your attack for this turn |
| EMP_NAVAL | ✅ Yes | Replaces your attack for this turn |

**Business rules:**
- Only available in `TACTICAL` mode games
- Must be your turn
- Game must be in `IN_PROGRESS` status
- Cannot use any ability while affected by EMP (`empDisabledTurns > 0`)
- Torpedo is used via the attack endpoint (`"type": "TORPEDO"`), not this ability endpoint

**Errors:**
- `"Habilidades só estão disponíveis no modo tático"` — game is CLASSIC mode
- `"Não é o turno desse jogador"` — not your turn
- `"Habilidades desativadas por EMP"` — affected by EMP
- `"Escudo não disponível"` — no shield charges left
- `"Radar já foi utilizado"` — already used
- `"EMP Naval já foi utilizado"` — already used
- `"Posição central do radar é obrigatória"` — radar used without cell

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
│ Subscribe: /user/topic/game/{gameId}/events (private events)     │
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
│   {"cell": "C4", "type": "NORMAL"}  (or "TORPEDO")              │
│                                                                  │
│ Server publishes (in order):                                     │
│   1. ATTACK_RESULT {attackerId, cell, hit, attackType}           │
│   2. (if sunk) SHIP_SUNK {ownerId, shipType, positions}          │
│   3a. (if game over) GAME_OVER {winnerId, loserId, reason}       │
│   3b. (if miss) TURN_CHANGE {nextTurn=opponent, turnTimeout}     │
│   3c. (if hit, not game over) TURN_CHANGE {nextTurn=same player} │
│                                                                  │
│ TURN RULE: If you HIT, you get another turn. Miss = turn passes. │
│                                                                  │
│ TORPEDO: Each player has 1 torpedo per match.                    │
│   If torpedo hits → entire ship sinks instantly.                 │
│   If torpedo misses → behaves like normal miss, turn passes.     │
│   Check torpedoAvailable in GET /games/{gameId}/state.           │
│                                                                  │
│ If player doesn't attack within 60s:                             │
│   Server publishes: TURN_TIMEOUT → TURN_CHANGE                   │
│                                                                  │
│ If player disconnects (during PLACING_SHIPS or IN_PROGRESS):     │
│   Server publishes: OPPONENT_DISCONNECTED (30s to reconnect)     │
│   If reconnects: OPPONENT_RECONNECTED (timer resumes)            │
│   If timeout during IN_PROGRESS:                                 │
│     GAME_OVER (reason: OPPONENT_DISCONNECTED, W.O.)              │
│   If timeout during PLACING_SHIPS:                               │
│     GAME_OVER + PLAYER_LEFT on room topic (room reset to WAITING)│
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

If the client loses connection during a game (`PLACING_SHIPS` or `IN_PROGRESS`):

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

**If reconnection fails (30s timeout expires):**
- **During `IN_PROGRESS`:** Game ends with W.O. victory for the connected player. Result is persisted to database.
- **During `PLACING_SHIPS`:** Game is cancelled (not persisted). `PLAYER_LEFT` is emitted on the room topic. The room is reset to `WAITING` status so the host can wait for a new opponent.

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

## Torpedo Mechanic

### Overview

Each player has **1 torpedo per match**. The torpedo is a special attack that, if it hits any position of a ship, **sinks the entire ship instantly** — regardless of the ship's size or how many positions have already been hit.

### Rules

| Rule | Description |
|------|-------------|
| Quantity | 1 torpedo per player per match |
| Hit behavior | If the torpedo hits a ship, ALL positions of that ship are marked as hit and the ship is immediately sunk |
| Miss behavior | Behaves exactly like a normal shot: registers a miss, turn passes to the opponent |
| Turn on hit | Same as normal: if torpedo hits, the player keeps the turn |
| Availability | Check `torpedoAvailable` field in `GET /games/{gameId}/state` response |
| Error on reuse | If torpedo already used: `"Torpedo já foi utilizado nesta partida"` |

### How to Use

1. Check that `torpedoAvailable` is `true` in the game state
2. Send an attack with `"type": "TORPEDO"`:
   ```json
   {
     "cell": "C4",
     "type": "TORPEDO"
   }
   ```
3. The server response and events will include `"attackType": "TORPEDO"` so the frontend can render a different animation

### Event Flow (Torpedo Hit)

```
Client sends: {"cell": "C4", "type": "TORPEDO"}

Server publishes:
  1. ATTACK_RESULT {attackerId, cell: "C4", hit: true, attackType: "TORPEDO"}
  2. SHIP_SUNK {ownerId, shipType: "CARRIER", positions: ["C1","C2","C3","C4","C5"]}
  3. TURN_CHANGE {nextTurn: same player} or GAME_OVER (if all ships sunk)
```

### Event Flow (Torpedo Miss)

```
Client sends: {"cell": "C4", "type": "TORPEDO"}

Server publishes:
  1. ATTACK_RESULT {attackerId, cell: "C4", hit: false, attackType: "TORPEDO"}
  2. TURN_CHANGE {nextTurn: opponent}
```

### Notes for Frontend

- The torpedo is consumed even if it misses
- After using the torpedo, `torpedoAvailable` in game state will be `false`
- The `attackType` field in `ATTACK_RESULT` event and `AttackResponse` indicates when a torpedo was used — use this to trigger torpedo-specific animations
- When a torpedo sinks a ship, the `SHIP_SUNK` event contains all positions of the destroyed ship (same as normal sink)

---

## Tactical Mode

### Overview

Tactical Mode is an alternative game mode that adds **5 special abilities** to the classic battleship gameplay. Each ability can be used a limited number of times per match, adding strategic depth to every turn.

The game mode is chosen by the **room host** when creating the room (`"gameMode": "TACTICAL"`). All game rules from Classic mode still apply (same ships, same 10×10 grid, same turn timer, hit = extra turn).

### Abilities

| Ability | Uses | Consumes Turn | Effect |
|---------|------|---------------|--------|
| **Torpedo** | 1× | Yes (attack) | Instantly sinks the entire ship if it hits. If it misses, behaves like a normal shot. |
| **Radar** | 1× | Yes | Scans a 3×3 area centered on the chosen cell. Reveals which cells contain ships (without revealing ship type). |
| **Shield** | 2× | No | Activates a shield on your board. The next incoming shot is blocked (registers as miss even if it would hit). Also blocks torpedoes. |
| **EMP Naval** | 1× | Yes | Disables ALL abilities of the opponent for their next 2 turns. They can only fire normal shots during that time. |

### Detailed Ability Rules

#### Torpedo (Attack)
- Used via the **attack endpoint** with `"type": "TORPEDO"` (same as Classic mode)
- If EMP is active on the shooter, torpedo cannot be used
- **Blocked by:** Shield
- If blocked, the torpedo is consumed but registers as a miss

#### Radar (Utility)
- Used via the **ability endpoint** with `"ability": "RADAR", "cell": "E5"`
- The `cell` field is the **center** of the 3×3 scan area
- Returns a list of cells in the 3×3 area that contain a non-sunken ship
- Does NOT reveal ship type or whether a cell has already been hit
- Edge cells: the 3×3 area is clipped to the grid boundaries (cells outside 0-9 are ignored)
- **Consumes the turn** — the player cannot attack after using radar

#### Shield (Defensive)
- Used via the **ability endpoint** with `"ability": "SHIELD"` (no cell needed)
- Activates immediately — the next incoming shot on this board will be blocked
- Only **one shield can be active** at a time (cannot stack)
- 2 total charges per match (can activate shield twice across the game)
- **Does NOT consume the turn** — player can still attack normally after activating
- Blocks both normal shots AND torpedoes
- Once triggered (shot blocked), the shield is consumed

#### EMP Naval (Offensive)
- Used via the **ability endpoint** with `"ability": "EMP_NAVAL"` (no cell needed)
- Immediately disables the opponent's abilities for their **next 2 turns**
- During those 2 turns, the opponent cannot: use torpedo, use radar, activate shield, or use EMP
- Already-active defenses (shield) remain active — EMP only prevents NEW activations
- The EMP counter decrements at the **start of each affected turn**
- **Consumes the turn** — the player cannot attack after using EMP

### Interaction Priority (Incoming Attack)

When a shot or torpedo hits a board with active defenses:

```
Incoming TORPEDO:
  1. Check Shield → if active, BLOCK (shield consumed)
  2. Normal torpedo logic (hit = sink ship, miss = miss)

Incoming NORMAL shot:
  1. Check Shield → if active, BLOCK (shield consumed, registers as miss)
  2. Normal shot logic (hit or miss)
```

**Event publication order when blocked:**
```
SHIELD_BLOCKED → ATTACK_RESULT (hit: false) → TURN_CHANGE
```

Only ONE block event is emitted per attack. The `ATTACK_RESULT` always follows with `hit: false`.

### Tactical Mode — Turn Structure

```
Your Turn:
  ├── [Optional] Activate SHIELD (does not end turn)
  └── Choose ONE of:
      ├── Normal attack → send to /app/game/{gameId}/attack
      ├── Torpedo attack → send to /app/game/{gameId}/attack with type "TORPEDO"
      ├── Use RADAR → send to /app/game/{gameId}/ability (ends turn)
      └── Use EMP_NAVAL → send to /app/game/{gameId}/ability (ends turn)
```

A player can activate defensive abilities AND attack in the same turn. But they can only use ONE turn-consuming action (attack OR radar OR EMP).

### Example Flow: Tactical Mode Game

```
Turn 1 (Player A):
  → Activates SHIELD (no turn consumed, 1 charge remaining)
  → Fires normal shot at B3 (hit!)
  → Gets another turn (hit rule)
  → Fires normal shot at B4 (miss)
  → Turn passes to Player B

Turn 2 (Player B):
  → Fires TORPEDO at E5 (hit! Sinks CRUISER)
  → Gets another turn
  → Uses RADAR centered at H5
  → Receives revealed cells: ["G5", "H4", "H5"]
  → Turn passes to Player A (radar consumed turn)

Turn 3 (Player A):
  → Uses EMP_NAVAL
  → Player B's abilities disabled for 2 turns
  → Turn passes to Player B

Turn 4 (Player B):  [EMP: 2 turns remaining]
  → Cannot use abilities! Can only fire normal shots.
  → Fires normal shot at A1 (miss, but SHIELD blocks it → registers as miss)
  → Turn passes to Player A

Turn 5 (Player A):
  → Fires normal shot at H4 (hit! found via radar)
  → Gets another turn...
```

---

## Quick Reference: All Subscriptions

| Topic | When to Subscribe | Events Received |
|-------|-------------------|-----------------|
| `/topic/room/{roomId}` | After creating/joining room | PLAYER_JOINED, ROOM_READY, PLAYER_LEFT |
| `/topic/game/{gameId}/placement` | After receiving ROOM_READY | OPPONENT_READY, GAME_STARTED |
| `/topic/game/{gameId}/events` | After ROOM_READY or GAME_STARTED | ATTACK_RESULT, TURN_CHANGE, TURN_TIMEOUT, SHIP_SUNK, GAME_OVER, OPPONENT_DISCONNECTED, OPPONENT_RECONNECTED, SHIELD_ACTIVATED, SHIELD_BLOCKED, RADAR_USED, EMP_ACTIVATED |
| `/topic/game/{gameId}/attack` | (optional, legacy) | AttackResponse flat object |

## Quick Reference: All Messages (Client → Server)

| Destination | When to Send | Body |
|-------------|-------------|------|
| `/app/game/{gameId}/attack` | During your turn | `{"cell": "C4", "type": "NORMAL"}` |
| `/app/game/{gameId}/ability` | During your turn (Tactical mode) | `{"ability": "RADAR", "cell": "E5"}` |
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
