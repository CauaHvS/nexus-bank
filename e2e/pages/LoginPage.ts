import { Page, expect } from '@playwright/test'

export class LoginPage {
  constructor(private readonly page: Page) {}

  async goto() {
    await this.page.goto('/login')
  }

  async login(email: string, password: string) {
    await this.page.getByLabel('E-mail').fill(email)
    await this.page.locator('#password').fill(password)
    await this.page.getByRole('button', { name: 'Entrar' }).click()
  }

  async expectDashboard() {
    await expect(this.page).toHaveURL(/dashboard/, { timeout: 8000 })
  }

  async expectError(timeout = 10000) {
    await expect(this.page.getByRole('alert')).toBeVisible({ timeout })
  }
}
