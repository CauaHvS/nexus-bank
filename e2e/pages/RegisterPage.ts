import { Page } from '@playwright/test'

export interface RegisterData {
  name: string
  cpf: string
  email: string
  password: string
}

export class RegisterPage {
  constructor(private readonly page: Page) {}

  async goto() {
    await this.page.goto('/register')
  }

  async fill(data: RegisterData) {
    await this.page.getByLabel('Nome completo').fill(data.name)
    await this.page.getByLabel('CPF').fill(data.cpf)
    await this.page.getByLabel('E-mail').fill(data.email)
    await this.page.locator('#password').fill(data.password)
    await this.page.locator('#confirmPassword').fill(data.password)
    await this.page.locator('#terms').check()
  }

  async submit() {
    await this.page.getByRole('button', { name: 'Criar conta' }).click()
  }

  async fillAndSubmit(data: RegisterData) {
    await this.fill(data)
    await this.submit()
  }
}
