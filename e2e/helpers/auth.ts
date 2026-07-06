import { Page } from '@playwright/test'

export interface AuthState {
  accessToken: string
  refreshToken: string
  user: { id: string; email: string; name: string }
}

/**
 * Injeta tokens de autenticação no localStorage do browser antes da primeira
 * navegação, simulando um login já realizado e evitando repetir o fluxo de UI.
 *
 * Deve ser chamado ANTES de page.goto().
 */
export async function injectAuth(page: Page, auth: AuthState): Promise<void> {
  await page.addInitScript((data: AuthState) => {
    localStorage.setItem('access_token', data.accessToken)
    localStorage.setItem('refresh_token', data.refreshToken)
    localStorage.setItem('auth_user', JSON.stringify(data.user))
  }, auth)
}
