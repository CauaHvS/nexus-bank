import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  // Serial para evitar conflitos de dados entre testes
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  // Em CI, a stack já está rodando via Docker Compose; não sobe o dev server.
  // Localmente, sobe o Vite automaticamente se não estiver em execução.
  webServer: process.env.CI
    ? undefined
    : {
        command: 'npm run dev',
        cwd: '../frontend',
        url: 'http://localhost:5173',
        reuseExistingServer: true,
        timeout: 30000,
      },
})
