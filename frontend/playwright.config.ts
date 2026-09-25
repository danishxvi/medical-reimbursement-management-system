import { defineConfig } from '@playwright/test'

/*
 * End to end tests against a real backend and the real app.
 *
 * Playwright starts its own API (dev profile: demo data, eSign simulator)
 * with an in memory database, and its own Vite server, on ports that do not
 * clash with a running development setup. PW_CHANNEL=msedge or chrome uses
 * an installed browser instead of Playwright's own Chromium.
 */
const API_PORT = 8090
const WEB_PORT = 5174
const WEB = `http://localhost:${WEB_PORT}`

export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: WEB,
    channel: process.env.PW_CHANNEL || undefined,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: [
    {
      command: 'mvn -q -f ../backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev',
      url: `http://localhost:${API_PORT}/api/legal/privacy`,
      timeout: 300_000,
      reuseExistingServer: false,
      stdout: 'ignore',
      env: {
        MRMS_PORT: String(API_PORT),
        SPRING_DATASOURCE_URL:
          'jdbc:h2:mem:mrms-e2e;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        MRMS_STORAGE_ROOT: '../backend/target/e2e-storage',
        MRMS_CORS_ALLOWEDORIGINS: WEB,
        MRMS_ESIGN_ESPURL: `${WEB}/api/dev/esp/sign`,
        MRMS_ESIGN_PUBLICBASEURL: WEB,
        MRMS_SECURITY_LOGINREQUESTSPERMINUTE: '1000',
        MRMS_SECURITY_APIREQUESTSPERMINUTE: '10000',
      },
    },
    {
      command: `npm run dev -- --port ${WEB_PORT} --strictPort`,
      url: WEB,
      timeout: 120_000,
      reuseExistingServer: false,
      env: { MRMS_API: `http://localhost:${API_PORT}` },
    },
  ],
})
