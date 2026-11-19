import { expect, test } from '@playwright/test';
import { login } from './helpers/auth';
import { recordCreatedTicket } from './helpers/created';
import { passwordFor, users } from './helpers/users';

test('client takes a ticket and follows it', async ({ page }) => {
  await login(page, users.client, passwordFor('client'));
  await expect(page.getByRole('heading', { name: /Bonjour/ })).toBeVisible();

  await page.getByTestId('card-establishment-clinic').click();
  await expect(page.getByRole('heading', { name: 'Clinique Al Amal' })).toBeVisible();

  await page.getByRole('button', { name: /Retrait de documents/ }).click();
  await page.getByTestId('take-ticket').click();

  await page.waitForURL(/\/success\?ticket=/, { timeout: 30_000 });
  const ticketId = new URL(page.url()).searchParams.get('ticket');
  expect(ticketId, 'the success page must carry the created ticket id').toBeTruthy();
  recordCreatedTicket(ticketId!);

  await expect(page.getByRole('heading', { name: "C'est noté !" })).toBeVisible();

  await page.getByRole('link', { name: 'Suivre mon ticket' }).click();
  await expect(page).toHaveURL(/\/tickets$/);
  await expect(page.getByRole('heading', { name: 'Mes tickets' })).toBeVisible();
  await expect(page.getByText(/^B-\d{3}$/).first()).toBeVisible();
});
