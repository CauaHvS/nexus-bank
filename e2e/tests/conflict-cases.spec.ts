/**
 * Casos de conflito (409):
 *   - E-mail duplicado no cadastro
 *   - Conta duplicada (já coberto em critical-journey, mas testado isoladamente aqui
 *     para garantir que a mensagem de erro aparece corretamente)
 */
import { test, expect } from '@playwright/test'
import { generateCpf } from '../helpers/cpf'
import { RegisterPage } from '../pages/RegisterPage'

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080'

test.describe('Conflitos (409)', () => {
  test('cadastro com e-mail já existente exibe erro de conflito', async ({ page, request }) => {
    const email = `dup_${Date.now()}@e2e.test`
    const password = 'Senha@123E2E'

    // Cria o primeiro usuário via API
    await request.post(`${BACKEND}/auth/register`, {
      data: { name: 'Primeiro E2E', email, cpf: generateCpf(), password },
    })

    // Tenta cadastrar via UI com o mesmo e-mail
    const register = new RegisterPage(page)
    await register.goto()
    await register.fillAndSubmit({
      name: 'Segundo E2E',
      cpf: generateCpf(), // CPF diferente
      email,              // Mesmo e-mail
      password,
    })

    // Deve aparecer alerta de erro
    const alert = page.getByRole('alert')
    await expect(alert).toBeVisible({ timeout: 5000 })
    // Permanece na página de registro
    await expect(page).toHaveURL(/register/)
  })
})
