import http from 'k6/http';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function jsonHeaders() {
  return { 'Content-Type': 'application/json' };
}

export function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
}

export function register(name, email, cpf, password) {
  return http.post(
    `${BASE_URL}/auth/register`,
    JSON.stringify({ name, email, cpf, password }),
    { headers: jsonHeaders(), tags: { endpoint: 'register' } }
  );
}

export function login(email, password) {
  const res = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email, password }),
    { headers: jsonHeaders(), tags: { endpoint: 'login' } }
  );
  if (res.status !== 200) {
    console.error(`Login falhou (${res.status}): ${res.body}`);
    return null;
  }
  return JSON.parse(res.body).accessToken;
}

export function createAccount(token, type = 'CHECKING', currency = 'BRL') {
  const res = http.post(
    `${BASE_URL}/accounts`,
    JSON.stringify({ type, currency }),
    { headers: authHeaders(token), tags: { endpoint: 'create-account' } }
  );
  if (res.status !== 201) {
    console.error(`Criação de conta falhou (${res.status}): ${res.body}`);
    return null;
  }
  return JSON.parse(res.body).accountId;
}

export function deposit(token, accountId, amount) {
  return http.post(
    `${BASE_URL}/accounts/${accountId}/deposit`,
    JSON.stringify({ amount }),
    { headers: authHeaders(token), tags: { endpoint: 'deposit' } }
  );
}

export function initiateTransfer(token, sourceAccountId, targetAccountId, amount, idempotencyKey) {
  return http.post(
    `${BASE_URL}/transfers`,
    JSON.stringify({
      sourceAccountId,
      targetAccountId,
      amount,
      currency: 'BRL',
      type: 'INTERNAL',
      description: 'Teste de carga k6',
    }),
    {
      headers: {
        ...authHeaders(token),
        'Idempotency-Key': idempotencyKey,
      },
      tags: { endpoint: 'transfer' },
    }
  );
}

export function getBalance(token, accountId) {
  return http.get(
    `${BASE_URL}/accounts/${accountId}/balance`,
    { headers: authHeaders(token), tags: { endpoint: 'balance' } }
  );
}

export function getStatement(token, accountId, page = 0, size = 20) {
  return http.get(
    `${BASE_URL}/accounts/${accountId}/statement?page=${page}&size=${size}`,
    { headers: authHeaders(token), tags: { endpoint: 'statement' } }
  );
}
