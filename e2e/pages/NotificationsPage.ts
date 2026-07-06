import { Page, expect } from '@playwright/test'

export class NotificationsPage {
  constructor(private readonly page: Page) {}

  async goto() {
    await this.page.goto('/notificacoes')
  }

  async expectNotificationContaining(text: string | RegExp, timeout = 15000) {
    await expect(this.page.getByText(text)).toBeVisible({ timeout })
  }

  async expectTransferNotification(timeout = 15000) {
    // O serviço de notificações processa de forma assíncrona; timeout maior
    await expect(
      this.page.getByText(/transferência|transferencia/i),
    ).toBeVisible({ timeout })
  }
}
