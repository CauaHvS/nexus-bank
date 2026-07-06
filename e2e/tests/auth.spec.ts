import { test, expect } from '@playwright/test'
import { generateCpf } from '../helpers/cpf'
import { LoginPage } from '../pages/LoginPage'
import { RegisterPage } from '../pages/RegisterPage'

const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080'

test.describe('Autenticação via UI', () => {
  test('cadastro bem-sucedido redireciona para a página de login', async ({ page }) => {
    // O useRegister redireciona para /login após o cadastro bem-sucedido
    const register = new RegisterPage(page)
    await register.goto()
    await register.fillAndSubmit({
      name: 'Teste E2E Cadastro',
      cpf: generateCpf(),
      email: `cadastro_${Date.now()}@e2e.test`,
      password: 'Senha@123E2E',
    })
    await expect(page).toHaveURL(/login/, { timeout: 8000 })
  })

  test('login com credenciais corretas redireciona para o dashboard', async ({ page, request }) => {
    const email = `login_${Date.now()}@e2e.test`
    const password = 'Senha@123E2E'

    // Cria o usuário via API para não depender do fluxo de UI do cadastro
    await request.post(`${BACKEND}/auth/register`, {
      data: { name: 'Login E2E', email, cpf: generateCpf(), password },
    })

    const login = new LoginPage(page)
    await login.goto()
    await login.login(email, password)
    await login.expectDashboard()
  })

  test('login com senha incorreta exibe mensagem de erro', async ({ page, request }) => {
    const email = `errado_${Date.now()}@e2e.test`

    await request.post(`${BACKEND}/auth/register`, {
      data: { name: 'Erro E2E', email, cpf: generateCpf(), password: 'Senha@123E2E' },
    })

    const login = new LoginPage(page)
    await login.goto()
    await login.login(email, 'SenhaErrada99!')
    await login.expectError()
    // Deve continuar na página de login
    await expect(page).toHaveURL(/login/)
  })
})
