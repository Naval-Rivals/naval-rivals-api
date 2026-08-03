import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ─── Métricas customizadas ─────────────────────────────────────────────────
const loginDuration = new Trend('login_duration', true);
const authEndpointDuration = new Trend('auth_endpoint_duration', true);
const failedLogins = new Counter('failed_logins');

// ─── Configuração de carga ─────────────────────────────────────────────────
export const options = {
  stages: [
    { duration: '30s', target: 10 },   // ramp-up
    { duration: '1m', target: 25 },    // carga moderada
    { duration: '30s', target: 50 },   // pico
    { duration: '1m', target: 50 },    // sustenta pico
    { duration: '30s', target: 0 },    // ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000'],  // 95% das requests < 2s
    http_req_failed: ['rate<0.05'],     // menos de 5% de falhas
    login_duration: ['p(95)<1000'],     // login < 1s em p95
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// ─── Setup: cria usuários de teste ─────────────────────────────────────────
export function setup() {
  const users = [];
  const totalUsers = 50;

  for (let i = 1; i <= totalUsers; i++) {
    const email = `loadtest_user${i}@test.com`;
    const password = 'LoadTest@123';
    const nickname = `loadtest_user${i}`;

    // Tenta registrar (ignora se já existir)
    const registerRes = http.post(`${BASE_URL}/auth/register`, JSON.stringify({
      nickname: nickname,
      email: email,
      password: password,
      passwordConfirmation: password,
    }), { headers: { 'Content-Type': 'application/json' } });

    if (registerRes.status === 201 || registerRes.status === 409 || registerRes.status === 400) {
      users.push({ email, password, nickname });
    }
  }

  console.log(`[SETUP] ${users.length} usuários preparados para o teste`);
  return { users };
}

// ─── Fluxo principal por VU ────────────────────────────────────────────────
export default function (data) {
  const userIndex = (__VU - 1) % data.users.length;
  const user = data.users[userIndex];

  let token = null;

  // ─── Login ─────────────────────────────────────────────────────────────
  group('Auth - Login', () => {
    const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
      login: user.email,
      password: user.password,
    }), { headers: { 'Content-Type': 'application/json' } });

    loginDuration.add(res.timings.duration);

    const success = check(res, {
      'login status 200': (r) => r.status === 200,
      'login retorna token': (r) => {
        try { return r.json('token') !== undefined; } catch (e) { return false; }
      },
    });

    if (success) {
      token = res.json('token');
    } else {
      failedLogins.add(1);
    }
  });

  if (!token) {
    sleep(1);
    return;
  }

  const authHeaders = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  };

  // ─── Endpoints autenticados ────────────────────────────────────────────
  group('User - GET /users/me', () => {
    const res = http.get(`${BASE_URL}/users/me`, authHeaders);
    authEndpointDuration.add(res.timings.duration);
    check(res, {
      'users/me status 200': (r) => r.status === 200,
      'users/me retorna nickname': (r) => {
        try { return r.json('nickname') !== undefined; } catch (e) { return false; }
      },
    });
  });

  sleep(0.5);

  group('User - GET /users/me/matches', () => {
    const res = http.get(`${BASE_URL}/users/me/matches?page=0&size=10`, authHeaders);
    authEndpointDuration.add(res.timings.duration);
    check(res, {
      'matches status 200': (r) => r.status === 200,
    });
  });

  sleep(0.5);

  group('Ranking - GET /ranking', () => {
    const res = http.get(`${BASE_URL}/ranking?page=0&size=20`, authHeaders);
    check(res, {
      'ranking status 200': (r) => r.status === 200,
      'ranking retorna content': (r) => {
        try { return r.json('content') !== undefined; } catch (e) { return false; }
      },
    });
  });

  sleep(0.5);

  group('Rooms - GET /rooms (lista salas)', () => {
    const res = http.get(`${BASE_URL}/rooms`, authHeaders);
    check(res, {
      'rooms status 200': (r) => r.status === 200,
    });
  });

  sleep(0.5);

  // ─── Fluxo de criação e deleção de sala ────────────────────────────────
  group('Rooms - Criar e deletar sala', () => {
    const createRes = http.post(`${BASE_URL}/rooms`, JSON.stringify({
      gameMode: 'CLASSIC',
    }), authHeaders);

    const created = check(createRes, {
      'criar sala status 201': (r) => r.status === 201,
    });

    if (created) {
      const roomId = createRes.json('id');
      sleep(0.3);

      // Consultar sala criada
      const getRes = http.get(`${BASE_URL}/rooms/${roomId}`, authHeaders);
      check(getRes, {
        'get sala status 200': (r) => r.status === 200,
      });

      sleep(0.3);

      // Deletar sala (limpeza)
      const delRes = http.del(`${BASE_URL}/rooms/${roomId}`, null, authHeaders);
      check(delRes, {
        'delete sala status 204': (r) => r.status === 204,
      });
    }
  });

  sleep(1); // think time
}

// ─── Teardown ──────────────────────────────────────────────────────────────
export function teardown(data) {
  console.log('[TEARDOWN] Teste de carga REST finalizado');
}
