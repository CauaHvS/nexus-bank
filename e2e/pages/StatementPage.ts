import { Page, expect } from '@playwright/test'

export class StatementPage {
  constructor(private readonly page: Page) {}

  async goto() {
    await this.page.goto('/extrato')
  }

  async expectEntry(text: string | RegExp, timeout = 8000) {
    await expect(this.page.getByText(text)).toBeVisible({ timeout })
  }

  async expectTransferEntry(timeout = 8000) {
    // Verifica se aparece pelo menos uma entrada com valor monetário (débito ou crédito)
    // O StatementList renderiza valores como "-R$ 100,00" ou "+R$ 100,00"
    const entry = this.page.getByText(/[+-]R\$|Transferencia|transferencia/i).first()
    await expect(entry).toBeVisible({ timeout })
  }
}
