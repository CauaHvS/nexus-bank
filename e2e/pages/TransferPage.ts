import { Page, expect } from '@playwright/test'

export type PaymentType = 'PIX' | 'TED' | 'INTERNAL'

const TYPE_LABEL: Record<PaymentType, string> = {
  PIX: 'Pix',
  TED: 'TED',
  INTERNAL: 'Interna',
}

export interface TransferData {
  sourceAccountId: string
  targetAccountId: string
  amount: number
  type?: PaymentType
  description?: string
}

export class TransferPage {
  constructor(private readonly page: Page) {}

  async goto() {
    await this.page.goto('/transferencias')
  }

  async fillStep1(data: TransferData) {
    const type = data.type ?? 'INTERNAL'

    // Aguarda as contas carregarem no select
    await expect(this.page.locator('#sourceAccountId')).toBeVisible({ timeout: 8000 })
    await this.page.locator('#sourceAccountId').selectOption(data.sourceAccountId)

    // Tipo de transferência (tabs com role="tab")
    await this.page.getByRole('tab', { name: TYPE_LABEL[type] }).click()

    // Conta de destino
    await this.page.locator('#targetAccountId').fill(data.targetAccountId)

    // Valor
    await this.page.locator('#amount').fill(String(data.amount))

    // Descrição (opcional)
    if (data.description) {
      await this.page.locator('#description').fill(data.description)
    }

    // Avançar para passo 2
    await this.page.getByRole('button', { name: 'Revisar transferencia' }).click()
  }

  async confirmStep2() {
    // Aguarda o passo 2 aparecer antes de confirmar
    await expect(this.page.getByText('Confirme a transferencia')).toBeVisible({ timeout: 5000 })
    await this.page.getByRole('button', { name: 'Confirmar transferencia' }).click()
  }

  async expectSuccess(timeout = 15000) {
    // A transferência pode resolver de duas formas:
    // 1. Toast de sucesso aparece (dura 2.5s) e depois navega para /extrato
    // 2. Diretamente navega para /extrato (quando toast desaparece antes da assertion)
    // Aceita qualquer uma das duas condições
    await Promise.race([
      expect(
        this.page.getByRole('alert').filter({ hasText: /sucesso|agendada/i }),
      ).toBeVisible({ timeout }),
      expect(this.page).toHaveURL(/extrato/, { timeout }),
    ])
  }

  async expectRedirectToStatement(timeout = 15000) {
    await expect(this.page).toHaveURL(/extrato/, { timeout })
  }
}
