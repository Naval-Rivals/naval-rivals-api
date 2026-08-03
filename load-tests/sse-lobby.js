import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// ─── Métricas customizadas ─────────────────────────────────────────────────
const sseConnections = new Counter('sse_connections_opened');
const sseErrors = new Counter('sse_errors');
const sseConnectDuration = new Trend('sse_connect_duration', true);

// ─── Configuração ──────────────────────────────────────────────────────────
// Testa muitas conexões SSE simultâneas — simula lobby com muitos jogadores
export const options = {
  stages: [
    { duration: '15s', target: 20 },   // ramp-up
    { duration: '1m', target: 50 },    // 50 conexões simultâneas
    { duration: '30s', target: 100 },  // pico: 100 conexões SSE
    { duration: '1m', target: 100 },   // sustenta pico
    { duration: '20s', target: 0 },    // ramp-down
  ],
  thresholds: {
    sse_errors: ['count<5'],
    sse_connect_duration: ['p(95)<2000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// ─── Fluxo principal ───────────────────────────────────────────────────────
// O endpoint /lobby/events é público (sem auth), então não precisa de login
export default function () {
  const startTime = Date.now();

  // SSE: k6 não tem suporte nativo a EventSource, mas o endpoint SSE
  // responde com text/event-stream. Fazemos um GET com timeout longo
  // para simular uma conexão SSE que fica aberta.
  const res = http.get(`${BASE_URL}/lobby/events`, {
    headers: {
      'Accept': 'text/event-stream',
      'Cache-Control': 'no-cache',
    },
    timeout: '65s', // Mantém a conexão por até 65s (simula cliente real)
  });

  const connectTime = Date.now() - startTime;
  sseConnectDuration.add(connectTime);
  sseConnections.add(1);

  const success = check(res, {
    'sse status 200': (r) => r.status === 200,
    'sse content-type event-stream': (r) =>
      r.headers['Content-Type'] && r.headers['Content-Type'].includes('text/event-stream'),
  });

  if (!success) {
    sseErrors.add(1);
  }

  // Simula um intervalo antes de reconectar (comportamento real do EventSource)
  sleep(2);
}

export function teardown() {
  console.log('[TEARDOWN] Teste de carga SSE finalizado');
}
