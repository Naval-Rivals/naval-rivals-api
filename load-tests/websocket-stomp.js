import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ─── Métricas customizadas ─────────────────────────────────────────────────
const wsConnectDuration = new Trend('ws_connect_duration', true);
const wsMessagesSent = new Counter('ws_messages_sent');
const wsMessagesReceived = new Counter('ws_messages_received');
const wsErrors = new Counter('ws_errors');

// ─── Configuração ──────────────────────────────────────────────────────────
export const options = {
  stages: [
    { duration: '20s', target: 10 },   // ramp-up
    { duration: '1m', target: 30 },    // carga moderada
    { duration: '30s', target: 50 },   // pico
    { duration: '1m', target: 50 },    // sustenta
    { duration: '20s', target: 0 },    // ramp-down
  ],
  thresholds: {
    ws_connect_duration: ['p(95)<3000'],
    ws_errors: ['count<10'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws';

// ─── Helpers STOMP ─────────────────────────────────────────────────────────
const NULL_CHAR = String.fromCharCode(0);

function stompConnect(token) {
  return 'CONNECT\naccept-version:1.2\nheart-beat:10000,10000\nAuthorization:Bearer ' + token + '\n\n' + NULL_CHAR;
}

function stompSubscribe(id, destination) {
  return 'SUBSCRIBE\nid:' + id + '\ndestination:' + destination + '\n\n' + NULL_CHAR;
}

function stompSend(destination, body) {
  const content = JSON.stringify(body);
  return 'SEND\ndestination:' + destination + '\ncontent-type:application/json\ncontent-length:' + content.length + '\n\n' + content + NULL_CHAR;
}

function stompDisconnect() {
  return 'DISCONNECT\nreceipt:close\n\n' + NULL_CHAR;
}

// ─── Setup ─────────────────────────────────────────────────────────────────
export function setup() {
  const users = [];
  const totalPairs = 25; // 25 pares = 50 usuários

  for (let i = 1; i <= totalPairs * 2; i++) {
    const email = `wstest_user${i}@test.com`;
    const password = 'WsTest@123';
    const nickname = `wstest_user${i}`;

    const registerRes = http.post(`${BASE_URL}/auth/register`, JSON.stringify({
      nickname, email, password, passwordConfirmation: password,
    }), { headers: { 'Content-Type': 'application/json' } });

    users.push({ email, password, nickname });
  }

  console.log(`[SETUP] ${users.length} usuários preparados para teste WebSocket`);
  return { users };
}

// ─── Fluxo principal ───────────────────────────────────────────────────────
export default function (data) {
  const userIndex = (__VU - 1) % data.users.length;
  const user = data.users[userIndex];

  // Login para obter token
  const loginRes = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    login: user.email,
    password: user.password,
  }), { headers: { 'Content-Type': 'application/json' } });

  if (loginRes.status !== 200) {
    wsErrors.add(1);
    sleep(2);
    return;
  }

  const token = loginRes.json('token');

  // Criar uma sala para ter contexto
  const roomRes = http.post(`${BASE_URL}/rooms`, JSON.stringify({
    gameMode: 'CLASSIC',
  }), {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
  });

  let roomId = null;
  if (roomRes.status === 201) {
    roomId = roomRes.json('id');
  }

  // Conectar via WebSocket STOMP
  const startTime = Date.now();
  const res = ws.connect(WS_URL, null, function (socket) {
    let connected = false;

    socket.on('open', () => {
      wsConnectDuration.add(Date.now() - startTime);
      // Enviar STOMP CONNECT com token no native header
      socket.send(stompConnect(token));
      wsMessagesSent.add(1);
    });

    socket.on('message', (msg) => {
      wsMessagesReceived.add(1);

      if (msg.indexOf('CONNECTED') === 0) {
        connected = true;

        // Subscribe no tópico da sala
        if (roomId) {
          socket.send(stompSubscribe('sub-room', '/topic/room/' + roomId));
          wsMessagesSent.add(1);

          // Registrar sessão na sala
          socket.send(stompSend('/app/room/' + roomId + '/register', {}));
          wsMessagesSent.add(1);
        }
      }

      if (msg.indexOf('ERROR') === 0) {
        wsErrors.add(1);
      }
    });

    socket.on('error', (e) => {
      wsErrors.add(1);
    });

    // Mantém conectado por 30 segundos simulando um jogador na sala
    sleep(30);

    // Disconnect graceful
    socket.send(stompDisconnect());
    wsMessagesSent.add(1);
    socket.close();
  });

  check(res, {
    'ws connection status 101': (r) => r && r.status === 101,
  });

  // Cleanup: deletar sala criada
  if (roomId) {
    http.del(`${BASE_URL}/rooms/${roomId}`, null, {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
    });
  }

  sleep(2);
}

export function teardown(data) {
  console.log('[TEARDOWN] Teste de carga WebSocket finalizado');
}
