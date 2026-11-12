import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests/e2e', workers: 1, fullyParallel: false,
  timeout: 45_000, expect: { timeout: 10_000 },
  reporter: [['list']],
  projects: [
    { name: 'mock', testIgnore: ['**/real-api.spec.ts', '**/donor-api.spec.ts'] },
    // Run against a real-mode Next server; test-only HTTP routes simulate B07, not a live DB.
    { name: 'donor-api', testMatch: '**/donor-api.spec.ts' },
    { name: 'real', testMatch: '**/real-api.spec.ts', use: {
      baseURL: process.env.REAL_UI_BASE_URL ?? process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:3000',
      trace: 'off', screenshot: 'off', video: 'off',
    } },
  ],
  use: { baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:3000',
    headless: true, trace: 'retain-on-failure', screenshot: 'only-on-failure',
    launchOptions: process.env.PLAYWRIGHT_CHROME_PATH ? { executablePath: process.env.PLAYWRIGHT_CHROME_PATH } : {},
  },
});
