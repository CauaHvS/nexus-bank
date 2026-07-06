/**
 * Jornada crítica ponta a ponta:
 *   setup via API → login injetado → contas → transferência → extrato → notificações
 *
 * O setup é feito via API para garantir isolamento e velocidade.
 * O injectAuth simula o login sem repetir o fluxo de UI (já coberto em auth.spec.ts).
 */
import { test, expect } from '@playwright/test'
import { generateCpf } from '../helpers/cpf'
import {
  apiRegister,
  apiLogin,
  apiOpenAccount,
  apiDeposit,
  type AuthResult,
} from '../helpers/api'
import { injectAuth, type AuthState } from '../helpers/auth'
import { AccountsPage } from '../pages/AccountsPage'
import { TransferPage } from '../pages/TransferPage'
import { StatementPage } from '../pages/StatementPage'
import { NotificationsPage } from '../pages/NotificationsPage'

// Estado compartilhado entre os testes do describe — criado no beforeAll
let user1Auth: AuthState
let account1Id: string
let account2Id: string
const DESCRIPTION = `E2E-${Date.now()}`

test.describe('Jornada crítica', () => {
  test.beforeAll(async ({ request }) => {
    // --- Usuário 1 (origem) ---
    const email1 = `u1_${Date.now()}@e2e.test`
    const password = 'Senha@123E2E'

    const user1 = await apiRegister(request, {
      name: 'E2E User 1',
      email: email1,
      cpf: generateCpf(),
      password,
    })
    const tokens1 = await apiLogin(request, email1, password)
    user1Auth = {
      accessToken: tokens1.accessToken,
      refreshToken: tokens1.refreshToken,
      user: { id: user1.id, email: email1, name: 'E2E User 1' },
    }

    account1Id = await apiOpenAccount(request, tokens1.accessToken, 'CHECKING')
    await apiDeposit(request, tokens1.accessToken, account1Id, 5000)

    // --- Usuário 2 (destino) ---
    const email2 = `u2_${Date.now()}@e2e.test`
    await apiRegister(request, {
      name: 'E2E User 2',
      email: email2,
      cpf: generateCpf(),
      password,
    })
    const tokens2 = await apiLogin(request, email2, password)
    account2Id = await apiOpenAccount(request, tokens2.accessToken, 'CHECKING')
  })

  test('página de contas exibe a conta criada', async ({ page }) => {
    await injectAuth(page, user1Auth)
    const accounts = new AccountsPage(page)
    await accounts.goto()
    await accounts.expectAccountVisible()
  })

  test('tentar abrir conta CHECKING duplicada exibe erro', async ({ page }) => {
    await injectAuth(page, user1Auth)
    const accounts = new AccountsPage(page)
    await accounts.goto()
    await accounts.expectAccountVisible()
    // Abre modal e confirma — deve falhar com 409
    await accounts.openAccount('CHECKING')
    await accounts.expectModalError()
    // Modal permanece aberto para o usuário corrigir
    await accounts.expectModalStillOpen()
  })

  test('realiza transferência interna com sucesso', async ({ page }) => {
    await injectAuth(page, user1Auth)
    const transfer = new TransferPage(page)
    await transfer.goto()
    await transfer.fillStep1({
      sourceAccountId: account1Id,
      targetAccountId: account2Id,
      amount: 100,
      type: 'INTERNAL',
      description: DESCRIPTION,
    })
    await transfer.confirmStep2()
    await transfer.expectSuccess()
  })

  test('extrato mostra a transferência realizada', async ({ page }) => {
    await injectAuth(page, user1Auth)
    const statement = new StatementPage(page)
    await statement.goto()
    // Verifica presença de pelo menos um lançamento de débito (a transferência realizada)
    await statement.expectTransferEntry()
  })

  test('central de notificações exibe notificação de transferência', async ({ page }) => {
    await injectAuth(page, user1Auth)
    const notifications = new NotificationsPage(page)
    await notifications.goto()
    // O microsserviço de notificações processa de forma assíncrona
    await notifications.expectTransferNotification()
  })
})
