import { expect, test, type Page } from '@playwright/test';
import { callApi, tokenAfterLogin } from './helpers/api';
import { login } from './helpers/auth';
import { passwordFor, users } from './helpers/users';

async function adminToken(page: Page): Promise<string> {
  return tokenAfterLogin(page, async () => {
    await login(page, users.admin, passwordFor('admin'));
    await page.goto('/tenant');
  });
}

test('a suspended establishment disappears from the client catalogue', async ({ page, browser }) => {
  const token = await adminToken(page);
  const suspended = await callApi(page, token, 'PUT', '/establishments/clinic/status', { active: false });
  expect(suspended.status).toBe(200);
  expect(suspended.body.active).toBe(false);

  try {
    const context = await browser.newContext({ baseURL: test.info().project.use.baseURL as string });
    const clientPage = await context.newPage();
    await login(clientPage, users.client, passwordFor('client'));

    await clientPage.goto('/search');
    await expect(clientPage.getByText('Agence Guéliz').first()).toBeVisible();
    await expect(clientPage.getByText('Clinique Al Amal')).toHaveCount(0);

    await clientPage.goto('/home');
    await expect(clientPage.getByText('Clinique Al Amal')).toHaveCount(0);

    await context.close();
  } finally {
    const restored = await callApi(page, token, 'PUT', '/establishments/clinic/status', { active: true });
    expect(restored.status).toBe(200);
    expect(restored.body.active).toBe(true);
  }
});
