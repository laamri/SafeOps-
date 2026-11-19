import { expect, type Page } from '@playwright/test';

const KEYCLOAK_AUTH = /\/realms\/tiko\/protocol\/openid-connect\/auth/;

/** Logs in through the real Keycloak form and waits for the SPA to render. */
export async function login(page: Page, username: string, password: string): Promise<void> {
  await page.goto('/home');
  await page.waitForURL(KEYCLOAK_AUTH, { timeout: 45_000 });
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await expect(page.locator('main').first()).toBeVisible({ timeout: 45_000 });
}
