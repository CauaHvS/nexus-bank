/**
 * Fatia 7.2 — Teste de Carga: POST /transfers
 *
 * Dois cenários:
 *   load   — carga nominal, mede p95/p99 de transferências bem-sucedidas
 *   stress — ultrapassa rate limiter e bulkhead para achar o ponto de ruptura
 *
 * Uso:
 *   k6 run k6/transfer-load.js
 *   k6 run k6/transfer-load.js -e BASE_URL=http://localhost:8080 -e NUM_USERS=20
 */

import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { initiateTransfer } from './lib/api.js';
import { prepareUsers } from './lib/setup.js';

// ---- Métricas customizadas ------------------------------------------------

const transferDuration = new Trend('transfer_duration_ms', true);
const transfersCreated  = new Counter('transfers_created');
const rateLimited       = new Counter('transfers_rate_limited');
const conflicted        = new Counter('transfers_conflict');
const bulkheadFull      = new Counter('transfers_bulkhead_full');
const serverErrors      = new Counter('transfers_server_error');

// ---- Opções ----------------------------------------------------------------

const NUM_USERS = parseInt(__ENV.NUM_USERS || '20');

export const options = {
  scenarios: {
    /**
     * Carga nominal — 20 VUs constantes por 2 minutos.
     * Cada VU faz ~0.7 req/s (sleep 1.5s), total ~14 req/s.
     * Espera-se: alguns 429s (rate limiter 10 req/s), p95 < 600ms.
     */
    load: {
      executor: 'ramping-vus',
      startTime: '0s',
      stages: [
        { duration: '30s', target: 10 },
        { duration: '2m',  target: 20 },
        { duration: '30s', target: 0  },
      ],
      tags: { scenario: 'load' },
    },

    /**
     * Stress — sobe até 60 VUs para estressar rate limiter (10 req/s) e
     * bulkhead (20 simultâneas). Roda após o cenário load.
     */
    stress: {
      executor: 'ramping-vus',
      startTime: '3m30s',
      stages: [
        { duration: '30s', target: 30  },
        { duration: '1m',  target: 60  },
        { duration: '30s', target: 0   },
      ],
      tags: { scenario: 'stress' },
    },
  },

  thresholds: {
    // p95 de transferências aceitas (201) deve ser < 600ms
    transfer_duration_ms: ['p(95)<600', 'p(99)<1200'],
    // Erros 5xx devem ser quase zero — 429/409 são esperados e não contam aqui
    transfers_server_error: ['count<5'],
  },
};

// ---- Setup -----------------------------------------------------------------

export function setup() {
  return { users: prepareUsers(NUM_USERS) };
}

// ---- Cenário principal -----------------------------------------------------

export default function (data) {
  const { users } = data;
  if (!users || users.length === 0) {
    console.error('Nenhum usuário disponível. Verifique o setup.');
    return;
  }

  const user = users[__VU % users.length];
  // Idempotency-Key único por VU + iteração + timestamp (sem colisão mesmo com sleep baixo)
  const idempotencyKey = `k6-${__VU}-${__ITER}-${Date.now()}`;

  const res = initiateTransfer(
    user.token,
    user.sourceAccountId,
    user.targetAccountId,
    10.00,
    idempotencyKey
  );

  // Classifica a resposta
  switch (res.status) {
    case 201:
      transferDuration.add(res.timings.duration);
      transfersCreated.add(1);
      break;
    case 429:
      rateLimited.add(1);
      break;
    case 409:
      conflicted.add(1);
      break;
    case 503:
      bulkheadFull.add(1);
      break;
    default:
      if (res.status >= 500) serverErrors.add(1);
  }

  check(res, {
    'sem erro de servidor (5xx)': (r) => r.status < 500,
    'resposta esperada (201/409/429/503)': (r) =>
      [201, 409, 429, 503].includes(r.status),
  });

  sleep(1.5);
}

// ---- Resumo ----------------------------------------------------------------

export function handleSummary(data) {
  const created   = data.metrics.transfers_created?.values?.count ?? 0;
  const limited   = data.metrics.transfers_rate_limited?.values?.count ?? 0;
  const conflicts = data.metrics.transfers_conflict?.values?.count ?? 0;
  const bulkhead  = data.metrics.transfers_bulkhead_full?.values?.count ?? 0;
  const errors    = data.metrics.transfers_server_error?.values?.count ?? 0;

  const p95 = data.metrics.transfer_duration_ms?.values?.['p(95)'] ?? 0;
  const p99 = data.metrics.transfer_duration_ms?.values?.['p(99)'] ?? 0;

  const total = created + limited + conflicts + bulkhead + errors;

  console.log('\n====== Resumo — Teste de Carga (Transferências) ======');
  console.log(`Total de requisições : ${total}`);
  console.log(`201 Criadas          : ${created}  (${pct(created, total)}%)`);
  console.log(`429 Rate Limited     : ${limited}  (${pct(limited, total)}%)`);
  console.log(`409 Conflito (OCC)   : ${conflicts} (${pct(conflicts, total)}%)`);
  console.log(`503 Bulkhead Full    : ${bulkhead}  (${pct(bulkhead, total)}%)`);
  console.log(`5xx Erros Servidor   : ${errors}`);
  console.log(`p95 latência (201)   : ${p95.toFixed(1)} ms`);
  console.log(`p99 latência (201)   : ${p99.toFixed(1)} ms`);
  console.log('======================================================\n');

  return {
    'k6/results/transfer-load-summary.json': JSON.stringify(data, null, 2),
  };
}

function pct(part, total) {
  if (total === 0) return '0.0';
  return ((part / total) * 100).toFixed(1);
}
