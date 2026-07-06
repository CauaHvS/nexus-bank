import { Page, expect } from '@playwright/test'

export class AccountsPage {
  constructor(private readonly page: Page) {}

  async goto() {
    await this.page.goto('/contas')
  }

  async openAccountModal(type: 'CHECKING' | 'SAVINGS' = 'CHECKING') {
    // Clicar no botão "Abrir conta" (pode estar no header ou no estado vazio)
    await this.page.getByRole('button', { name: 'Abrir conta' }).first().click()
    if (type === 'SAVINGS') {
      await this.page.getByRole('combobox').selectOption('SAVINGS')
    }
  }

  async confirmModal() {
    await this.page.getByRole('button', { name: 'Confirmar' }).click()
  }

  async openAccount(type: 'CHECKING' | 'SAVINGS' = 'CHECKING') {
    await this.openAccountModal(type)
    await this.confirmModal()
  }

  async expectAccountVisible() {
    await expect(
      this.page.getByText('Conta Corrente').or(this.page.getByText('Conta Poupanca')),
    ).toBeVisible({ timeout: 5000 })
  }

  async expectModalError() {
    // O erro de 409 aparece como alert dentro do modal
    await expect(this.page.getByRole('alert')).toBeVisible({ timeout: 5000 })
  }

  async expectModalStillOpen() {
    // Modal permanece aberto após erro
    await expect(this.page.getByRole('button', { name: 'Confirmar' })).toBeVisible()
  }
}
