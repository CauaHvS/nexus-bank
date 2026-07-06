import { APIRequestContext } from '@playwright/test'

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080'

export interface UserCredentials {
  name: string
  email: string
  cpf: string
  password: string
}

export interface AuthResult {
  accessToken: string
  refreshToken: string
  user: { id: string; email: string; name: string }
}

export async function apiRegister(
  request: APIRequestContext,
  user: UserCredentials,
): Promise<{ id: string; email: string; name: string }> {
  const res = await request.post(`${BACKEND}/auth/register`, { data: user })
  if (!res.ok()) throw new Error(`Registro falhou (${res.status()}): ${await res.text()}`)
  return res.json()
}

export async function apiLogin(
  request: APIRequestContext,
  email: string,
  password: string,
): Promise<AuthResult> {
  const res = await request.post(`${BACKEND}/auth/login`, { data: { email, password } })
  if (!res.ok()) throw new Error(`Login falhou (${res.status()}): ${await res.text()}`)
  const body = await res.json()
  // O backend retorna accessToken e refreshToken, mas não 'user' no login
  // O 'user' é preenchido pelo caller com os dados do registro
  return body
}

export async function apiOpenAccount(
  request: APIRequestContext,
  token: string,
  type: 'CHECKING' | 'SAVINGS' = 'CHECKING',
): Promise<string> {
  const res = await request.post(`${BACKEND}/accounts`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { type, currency: 'BRL' },
  })
  if (!res.ok()) throw new Error(`Abrir conta falhou (${res.status()}): ${await res.text()}`)
  const body = await res.json()
  return body.accountId
}

export async function apiDeposit(
  request: APIRequestContext,
  token: string,
  accountId: string,
  amount: number,
): Promise<void> {
  const res = await request.post(`${BACKEND}/accounts/${accountId}/deposit`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { amount },
  })
  if (!res.ok()) throw new Error(`Depósito falhou (${res.status()}): ${await res.text()}`)
}
