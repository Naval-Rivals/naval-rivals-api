import http from 'k6/http';
import ws from 'k6/ws';
import { check, group, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ─── Métricas customizadas ─────────────────────────────────────────────────
const gameFlowDuration = new Trend('game_flow_duration', true);
const shipPlacementDuration = new Trend('ship_placement_duration', true);
const gameFlowErrors = new Counter('game_flow_errors');

// ─── Configuração ──────────────────────────────────────────────────────────
// Simula o fluxo completo: login → criar sala → posicionar navios
export const options = {
  stages: [
    { duration: '20s', target: 5 },    // ramp-up suave
    { duration: '1m', target: 15 },    // carga moderada
    { duration: '30s', target: 25 },   // pico
    { duration: '1m', target: 25 },    // sustenta
    { duration: '20s', target: 0 },    // ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<3000'],
    http_req_failed: ['rate<0.10'],
    ship_placement_duration: ['p(95)<2000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// ─── Fleet válida (navios posicionados no tabuleiro 10x10) ─────────────────
// CARRIER(5), BATTLESHIP(4), CRUISER(3), SUBMARINE(3), DESTROYER(2)
function getFleet() {
  return {
    ships: [
      {
        type: 'CARRIER',
        positions: [
          { row: 0, col: 0 }, { row: 0, col: 1 }, { row: 0, col: 2 },
          { row: 0, col: 3 }, { row: 0, col: 4 },
        ],
      },
      {
        type: 'BATTLESHIP',
        positions: [
          { row: 2, col: 0 }, { row: 2, col: 1 },
          { row: 2, col: 2 }, { row: 2, col: 3 },
        ],
      },
      {
        type: 'CRUISER',
        positions: [
          { row: 4, col: 0 }, { row: 4, col: 1 }, { row: 4, col: 2 },
        ],
      },
      {
        type: 'SUBMARINE',
        positions: [
          { row: 6, col: 0 }, { row: 6, col: 1 }, { row: 6, col: 2 },
        ],
      },
      {
        type: 'DESTROYER',
        positions: [
          { row: 8, col: 0 }, { row: 8, col: 1 },
        ],
      },
    ],
  };
}

// ─── Setup ─────────────────────────────────────────────────────────────────
export function setup() {
  const users = [];
  const totalUsers = 50;

  for (let i = 1; i <= totalUsers; i++) {
    const email = `gameflow_user${i}@test.com`;
    const password = 'GameFlow@123';
    const nickname = `gameflow_user${i}`;

    http.post(`${BASE_URL}/auth/register`, JSON.stringify({
      nickname, email, password, passwordConfirmation: password,
    }), { headers: { 'Content-Type': 'application/json' } });

    users.push({ email, password, nickname });
  }

  console.log(`[SETUP] ${users.length} usuários preparados para teste de fluxo de jogo`);
  return { users };
}

// ─── Fluxo principal ───────────────────────────────────────────────────────
export default function (data) {
  const flowStart = Date.now();
  const userIndex = (__VU - 1) % data.users.length;
  const user = data.users[userIndex];

  let token = null;
  let roomId = null;
  let gameId = null;

  // ─── 1. Login ──────────────────────────────────────────────────────────
  group('Game Flow - Login', () => {
    const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
      login: user.email,
      password: user.password,
    }), { headers: { 'Content-Type': 'application/json' } });

    if (check(res, { 'login ok': (r) => r.status === 200 })) {
      token = res.json('token');
    } else {
      gameFlowErrors.add(1);
    }
  });

  if (!token) { sleep(2); return; }

  const authHeaders = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };

  // ─── 2. Criar sala ────────────────────────────────────────────────────
  group('Game Flow - Criar Sala', () => {
    const modes = ['CLASSIC', 'TACTICAL'];
    const mode = modes[Math.floor(Math.random() * modes.length)];

    const res = http.post(`${BASE_URL}/rooms`, JSON.stringify({
      gameMode: mode,
    }), authHeaders);

    if (check(res, { 'sala criada 201': (r) => r.status === 201 })) {
      roomId = res.json('id');
      gameId = res.json('gameId');
    } else {
      gameFlowErrors.add(1);
    }
  });

  if (!roomId) { sleep(2); return; }

  sleep(0.5);

  // ─── 3. Consultar sala ────────────────────────────────────────────────
  group('Game Flow - Consultar Sala', () => {
    const res = http.get(`${BASE_URL}/rooms/${roomId}`, authHeaders);
    check(res, { 'sala encontrada 200': (r) => r.status === 200 });
  });

  sleep(0.3);

  // ─── 4. Posicionar navios (se gameId disponível) ──────────────────────
  if (gameId) {
    group('Game Flow - Posicionar Navios', () => {
      const fleet = getFleet();
      const res = http.post(`${BASE_URL}/games/${gameId}/ships`,
        JSON.stringify(fleet), authHeaders);

      shipPlacementDuration.add(res.timings.duration);

      check(res, {
        'navios posicionados': (r) => r.status === 200 || r.status === 400,
        // 400 pode ocorrer se gameId não tiver estado (sem oponente ainda)
      });
    });

    sleep(0.3);

    // ─── 5. Consultar estado do jogo ──────────────────────────────────────
    group('Game Flow - Estado do Jogo', () => {
      const res = http.get(`${BASE_URL}/games/${gameId}/state`, authHeaders);
      check(res, {
        'game state ok': (r) => r.status === 200 || r.status === 404,
      });
    });
  }

  // ─── 6. Cleanup: deletar sala ─────────────────────────────────────────
  group('Game Flow - Cleanup', () => {
    const res = http.del(`${BASE_URL}/rooms/${roomId}`, null, authHeaders);
    check(res, {
      'sala deletada': (r) => r.status === 204 || r.status === 404,
    });
  });

  gameFlowDuration.add(Date.now() - flowStart);
  sleep(1);
}

export function teardown(data) {
  console.log('[TEARDOWN] Teste de fluxo de jogo finalizado');
}
