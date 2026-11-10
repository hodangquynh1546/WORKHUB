import { defineConfig, devices } from '@playwright/test'

// End-to-end tests drive the real app: the Spring Boot backend (in-memory H2, so every run starts empty) and the Vite
// dev server, which proxies /api and /ws to it. Locally the installed Google Chrome is used; CI installs Chromium.
// Already running servers are reused, so `npm run dev` + `./mvnw spring-boot:run` can stay up between runs.
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'vi-VN',
  },
  projects: [{ name: 'chrome', use: { ...devices['Desktop Chrome'], channel: process.env.CI ? undefined : 'chrome' } }],
  webServer: [
    {
      command: './mvnw -q spring-boot:run',
      cwd: '../backend',
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: true,
      timeout: 180_000,
      env: {
        // The sign-up limiter would stop the suite (every test registers users from one address).
        RATE_LIMIT_ENABLED: 'false',
        JWT_SECRET: 'e2e-only-secret-not-for-use-0123456789abcdef',
        FRONTEND_BASE_URL: 'http://localhost:5173',
        MAIL_ENABLED: 'false',
      },
    },
    { command: 'npm run dev -- --strictPort', url: 'http://localhost:5173', reuseExistingServer: true, timeout: 60_000 },
  ],
})
