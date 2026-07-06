/**
 * Fatia 7.2 — Teste de Leitura: GET /accounts/{id}/balance e /statement
 *
 * Mede o caminho de leitura CQRS com cache Redis.
 * Dois cenários:
 *   cache-warm  — leitura constante (cache quente), mede p95 com cache ativo
 *   cache-miss  — leitura de contas alternadas para provocar cache miss periódico
 *
 * Uso:
 *   k6 run k6/read-load.js
 *   k6 run k6/read-load.js -e BASE_URL=http://localhost:8080 -e NUM_USERS=10
 */

import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { getBalance, getStatement } from './lib/api.js';
import { prepareUsers } from './lib/setup.js';

// ---- Métricas customizadas ------------------------------------------------

const balanceDuration   = new Trend('balance_duration_ms', true);
const statementDuration = new Trend('statement_duration_ms', true);
const readErrors        = new Counter('read_errors');

// ---- Opções ----------------------------------------------------------------

const NUM_USERS = parseInt(__ENV.NUM_USERS || '10');

export const options = {
  scenarios: {
    /**
     * Cache quente — 50 VUs lendo os mesmos accountIds repetidamente.
     * Após a primeira leitura, Redis serve o dado. Espera-se p95 < 100ms.
     */
    'cache-warm': {
      executor: 'constant-vus',
      vus: 50,
      duration: '2m',
      tags: { scenario: 'cache-warm' },
    },

    /**
     * Cache frio — 30 VUs cada um lendo um accountId diferente (rotação),
     * forçando cache miss a cada N iterações.
     */
    'cache-miss': {
      executor: 'ramping-vus',
      startTime: '2m30s',
      stages: [
        { duration: '30s', target: 30 },
        { duration: '1m',  target: 30 },
        { duration: '30s', target: 0  },
      ],
      tags: { scenario: 'cache-miss' },
    },
  },

  thresholds: {
    balance_duration_ms:   ['p(95)<150', 'p(99)<300'],
    statement_duration_ms: ['p(95)<300', 'p(99)<600'],
    read_errors:           ['count<10'],
  },
};

// ---- Setup -----------------------------------------------------------------

export function setup() {
  return { users: prepareUsers(NUM_USERS) };
}

// ---- Cenário principal -----------------------------------------------------

export default function (data) {
  const { users } = data;
  if (!users || users.length === 0) return;

  // cache-warm: todos os VUs usam os mesmos primeiros usuários (mesmo accountId)
  // cache-miss: VU rotaciona entre diferentes contas via __ITER
  const userIndex = (__VU + __ITER) % users.length;
  const user = users[userIndex];

  // Leitura de saldo
  const balanceRes = getBalance(user.token, user.sourceAccountId);
  if (balanceRes.status === 200) {
    balanceDuration.add(balanceRes.timings.duration);
  } else {
    readErrors.add(1);
  }

  check(balanceRes, { 'balance 200': (r) => r.status === 200 });

  sleep(0.2);

  // Leitura de extrato (mais pesado — busca movimentações + paginação)
  const stmtRes = getStatement(user.token, user.sourceAccountId);
  if (stmtRes.status === 200) {
    statementDuration.add(stmtRes.timings.duration);
  } else {
    readErrors.add(1);
  }

  check(stmtRes, { 'statement 200': (r) => r.status === 200 });

  sleep(0.5);
}

// ---- Resumo ----------------------------------------------------------------

export function handleSummary(data) {
  const balP95  = data.metrics.balance_duration_ms?.values?.['p(95)']   ?? 0;
  const balP99  = data.metrics.balance_duration_ms?.values?.['p(99)']   ?? 0;
  const stmtP95 = data.metrics.statement_duration_ms?.values?.['p(95)'] ?? 0;
  const stmtP99 = data.metrics.statement_duration_ms?.values?.['p(99)'] ?? 0;
  const errors  = data.metrics.read_errors?.values?.count ?? 0;

  console.log('\n====== Resumo — Teste de Leitura (Balance + Statement) ======');
  console.log(`Balance   p95: ${balP95.toFixed(1)} ms  | p99: ${balP99.toFixed(1)} ms`);
  console.log(`Statement p95: ${stmtP95.toFixed(1)} ms | p99: ${stmtP99.toFixed(1)} ms`);
  console.log(`Erros de leitura: ${errors}`);
  console.log('=============================================================\n');

  return {
    'k6/results/read-load-summary.json': JSON.stringify(data, null, 2),
  };
}
