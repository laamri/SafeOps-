import { expect, test } from '@playwright/test';
import { login } from './helpers/auth';
import { passwordFor, users } from './helpers/users';

test('clinic agent sees only the clinic tenant', async ({ page }) => {
  await login(page, users.agent, passwordFor('agent'));
  await page.goto('/tenant');

  await expect(page.getByText('Clinique Al Amal').first()).toBeVisible();
  await expect(page.getByText('Agence Guéliz')).toHaveCount(0);
  await expect(page.getByTestId('tenant-switch')).toBeDisabled();
});

test('agency agent sees only the agency tenant and cannot reach the clinic through the UI', async ({ page }) => {
  await login(page, users.agencyAgent, passwordFor('agencyAgent'));
  await page.goto('/tenant');

  await expect(page.getByText('Agence Guéliz').first()).toBeVisible();
  await expect(page.getByText('Clinique Al Amal')).toHaveCount(0);
  await expect(page.getByTestId('tenant-switch')).toBeDisabled();
});

test('client has no access to the tenant console', async ({ page }) => {
  await login(page, users.client, passwordFor('client'));
  await page.goto('/tenant');

  await expect(page.getByTestId('access-screen-title')).toHaveText('Accès non autorisé');
  await expect(page.getByText('Clinique Al Amal')).toHaveCount(0);
  await expect(page.getByText('Agence Guéliz')).toHaveCount(0);
});

test('staff without a site fails closed', async ({ page }) => {
  await login(page, users.noSiteAgent, passwordFor('noSiteAgent'));
  await page.goto('/tenant');

  await expect(page.getByTestId('access-screen-title')).toHaveText('Aucun établissement associé');
  await expect(page.getByText('Clinique Al Amal')).toHaveCount(0);
  await expect(page.getByText('Agence Guéliz')).toHaveCount(0);
});
