import { register, login, createAccount, deposit } from './api.js';

/**
 * Cria N usuários com dois pares de contas (source/target) e faz depósito
 * inicial na source para viabilizar transferências.
 *
 * Retorna array de { token, sourceAccountId, targetAccountId, email }
 */
export function prepareUsers(count, depositAmount = 999999) {
  // Semente baseada em timestamp para CPFs únicos entre execuções
  const seed = String(Date.now()).slice(-7); // 7 dígitos
  const users = [];

  for (let i = 0; i < count; i++) {
    const idx = String(i).padStart(4, '0');
    // CPF: 4 dígitos de índice + 7 dígitos de seed = 11 chars
    const cpf = (idx + seed).slice(0, 11);
    const email = `k6-${seed}-${i}@load.test`;
    const name = `K6 Usuario ${i}`;
    const password = 'Senha@123!';

    const regRes = register(name, email, cpf, password);
    if (regRes.status !== 201) {
      console.error(`[setup] Registro do usuário ${i} falhou: ${regRes.status} ${regRes.body}`);
      continue;
    }

    const token = login(email, password);
    if (!token) {
      console.error(`[setup] Login do usuário ${i} falhou`);
      continue;
    }

    const sourceAccountId = createAccount(token);
    const targetAccountId = createAccount(token);
    if (!sourceAccountId || !targetAccountId) {
      console.error(`[setup] Criação de contas do usuário ${i} falhou`);
      continue;
    }

    const depRes = deposit(token, sourceAccountId, depositAmount);
    if (depRes.status !== 200 && depRes.status !== 201) {
      console.error(`[setup] Depósito para usuário ${i} falhou: ${depRes.status}`);
    }

    users.push({ token, sourceAccountId, targetAccountId, email });
  }

  console.log(`[setup] ${users.length}/${count} usuários prontos`);
  return users;
}
