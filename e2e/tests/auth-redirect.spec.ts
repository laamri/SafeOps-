import { expect, test } from '@playwright/test';

const KEYCLOAK_AUTH = /\/realms\/tiko\/protocol\/openid-connect\/auth/;

test('unauthenticated /tenant redirects to the login page', async ({ page }) => {
  await page.goto('/tenant');
  await page.waitForURL(KEYCLOAK_AUTH, { timeout: 45_000 });
  await expect(page.locator('#username')).toBeVisible();
});

test('unauthenticated /provider redirects to the login page', async ({ page }) => {
  await page.goto('/provider');
  await page.waitForURL(KEYCLOAK_AUTH, { timeout: 45_000 });
  await expect(page.locator('#username')).toBeVisible();
});
