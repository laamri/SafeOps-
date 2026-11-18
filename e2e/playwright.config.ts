import { defineConfig } from '@playwright/test';

// Runs against the real Docker stack. Inside a container on Docker Desktop, Chromium maps
// "localhost" to the host gateway so the SPA's http://localhost origins keep matching the
// Keycloak redirect URIs; CI runs with host networking and leaves E2E_HOST_RESOLVER unset.
export default defineConfig({
  testDir: './tests',
  outputDir: process.env.E2E_OUTPUT_DIR ?? 'test-results',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:13001',
    // traces record request headers (Authorization: Bearer ...) and the Keycloak token
    // response body, and videos add nothing that the failure screenshot does not show
    trace: 'off',
    screenshot: 'only-on-failure',
    video: 'off',
    launchOptions: process.env.E2E_HOST_RESOLVER
      ? { args: [`--host-resolver-rules=MAP localhost ${process.env.E2E_HOST_RESOLVER}`] }
      : {},
  },
});
