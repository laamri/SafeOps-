import { expect, test } from '@playwright/test';
import { login } from './helpers/auth';
import { recordCreatedUser } from './helpers/created';
import { passwordFor, users } from './helpers/users';

// Unique per run and never reused: cleanup deletes exactly the accounts recorded here.
const invitedUsername = `e2e-agent-${Date.now().toString(36)}`;

test('clinic admin lists the staff and invites an agent with a one-time temporary password', async ({ page }) => {
  await login(page, users.admin, passwordFor('admin'));
  await page.goto('/tenant');

  await page.getByRole('button', { name: 'Agents' }).click();
  await expect(page.getByTestId('agent-username-agent')).toHaveText('agent');

  await page.getByTestId('agents-invite').click();
  await page.getByTestId('agents-first-name').fill('E2E');
  await page.getByTestId('agents-last-name').fill('Invité');
  await page.getByTestId('agents-username').fill(invitedUsername);
  await page.getByTestId('agents-email').fill(`${invitedUsername}@tiko.test`);
  await page.getByTestId('agents-submit').click();

  await expect(page.getByTestId('invitation-username')).toHaveText(invitedUsername);
  const temporaryPassword = await page.getByTestId('invitation-password').textContent();
  expect(temporaryPassword?.length ?? 0).toBeGreaterThanOrEqual(16);
  recordCreatedUser(invitedUsername);

  await expect(page.getByTestId(`agent-row-${invitedUsername}`)).toBeVisible();
  await expect(page.getByTestId(`agent-username-${invitedUsername}`)).toHaveText(invitedUsername);
});

test('an agent is not offered the staff administration screen', async ({ page }) => {
  await login(page, users.agent, passwordFor('agent'));
  await page.goto('/tenant');

  await expect(page.getByTestId('tenant-switch')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Agents' })).toHaveCount(0);
  await expect(page.getByTestId('agents-invite')).toHaveCount(0);
});
