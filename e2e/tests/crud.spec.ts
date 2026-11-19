import { expect, test, type Page } from '@playwright/test';
import { callApi, tokenAfterLogin } from './helpers/api';
import { login } from './helpers/auth';
import { recordCreatedQueue, recordCreatedTicket } from './helpers/created';
import { passwordFor, users } from './helpers/users';

// Every run creates rows with a fresh suffix; cleanup removes exactly what was recorded.
const suffix = Date.now().toString(36);

const clinicProfile = {
  name: 'Clinique Al Amal',
  category: 'Clinique',
  city: 'Casablanca',
  shortName: 'CA',
  color: '#41a98a',
  tint: '#e0f4ec',
  address: 'Rue Abou Alaa Zahar, Casablanca',
  opensAt: '08:30',
  closesAt: '16:30',
};

async function adminToken(page: Page): Promise<string> {
  return tokenAfterLogin(page, async () => {
    await login(page, users.admin, passwordFor('admin'));
    await page.goto('/tenant');
  });
}

async function firstQueueId(page: Page, token: string, site: string): Promise<number> {
  const response = await callApi(page, token, 'GET', `/establishments/${site}`);
  expect(response.status).toBe(200);
  const id = response.body?.queues?.[0]?.id;
  expect(typeof id).toBe('number');
  return id;
}

test('clinic admin creates, renames and deletes a queue', async ({ page }) => {
  const token = await adminToken(page);
  const name = `E2E file ${suffix}`;

  const created = await callApi(page, token, 'POST', '/establishments/clinic/queues', {
    name, prefix: 'Z', avgServiceMinutes: 4, counter: 'Guichet E2E',
  });
  expect(created.status).toBe(201);
  expect(created.body.name).toBe(name);
  const queueId = created.body.id as number;
  recordCreatedQueue(queueId);

  const listed = await callApi(page, token, 'GET', '/establishments/clinic');
  expect(listed.body.queues.map((queue: { id: number }) => queue.id)).toContain(queueId);

  const renamed = await callApi(page, token, 'PUT', `/queues/${queueId}`, {
    name: `${name} bis`, prefix: 'Z', avgServiceMinutes: 6, counter: 'Guichet E2E',
  });
  expect(renamed.status).toBe(200);
  expect(renamed.body.name).toBe(`${name} bis`);

  const removed = await callApi(page, token, 'DELETE', `/queues/${queueId}`);
  expect(removed.status).toBe(204);

  const after = await callApi(page, token, 'GET', '/establishments/clinic');
  expect(after.body.queues.map((queue: { id: number }) => queue.id)).not.toContain(queueId);
});

test('clinic admin updates the profile, suspends the clinic, then new tickets resume', async ({ page }) => {
  const token = await adminToken(page);
  const queueId = await firstQueueId(page, token, 'clinic');

  const updated = await callApi(page, token, 'PUT', '/establishments/clinic', {
    ...clinicProfile, address: `Rue E2E ${suffix}`,
  });
  expect(updated.status).toBe(200);
  expect(updated.body.address).toBe(`Rue E2E ${suffix}`);
  const restored = await callApi(page, token, 'PUT', '/establishments/clinic', clinicProfile);
  expect(restored.status).toBe(200);
  expect(restored.body.address).toBe(clinicProfile.address);

  const suspended = await callApi(page, token, 'PUT', '/establishments/clinic/status', { active: false });
  expect(suspended.status).toBe(200);
  expect(suspended.body.active).toBe(false);

  const refused = await callApi(page, token, 'POST', '/tickets', { queueId });
  expect(refused.status).toBe(409);
  expect(String(refused.body.message)).toContain('suspended');

  const reactivated = await callApi(page, token, 'PUT', '/establishments/clinic/status', { active: true });
  expect(reactivated.status).toBe(200);
  expect(reactivated.body.active).toBe(true);

  // the queue may have been left closed by another spec; open it before taking a ticket
  await callApi(page, token, 'PUT', `/queues/${queueId}/status`, { open: true });
  const accepted = await callApi(page, token, 'POST', '/tickets', { queueId });
  expect(accepted.status).toBe(201);
  recordCreatedTicket(accepted.body.id);
  const cancelled = await callApi(page, token, 'POST', `/tickets/${accepted.body.id}/cancel`);
  expect(cancelled.status).toBe(200);
});

test('a clinic admin cannot create anything or write anywhere in the agency', async ({ page }) => {
  const token = await adminToken(page);
  const agencyQueueId = await firstQueueId(page, token, 'agency');

  const agencyProfile = {
    name: 'Agence Guéliz', category: 'Administration', city: 'Marrakech', shortName: 'AG',
    color: '#df8d62', tint: '#fff0e6', address: 'Avenue Mohammed V, Marrakech',
    opensAt: '08:30', closesAt: '16:30',
  };
  expect((await callApi(page, token, 'PUT', '/establishments/agency', agencyProfile)).status).toBe(403);
  expect((await callApi(page, token, 'PUT', '/establishments/agency/status', { active: false })).status).toBe(403);
  expect((await callApi(page, token, 'POST', '/establishments/agency/queues', {
    name: 'Intruse', prefix: 'X', avgServiceMinutes: 3, counter: 'G',
  })).status).toBe(403);
  expect((await callApi(page, token, 'PUT', `/queues/${agencyQueueId}`, {
    name: 'Détournée', prefix: 'B', avgServiceMinutes: 2, counter: 'G',
  })).status).toBe(403);
  expect((await callApi(page, token, 'DELETE', `/queues/${agencyQueueId}`)).status).toBe(403);
  // a site the caller does not hold cannot even get a row
  expect((await callApi(page, token, 'POST', '/establishments', { id: 'agency', ...agencyProfile })).status).toBe(403);
});

test('an agency user cannot write anything in the clinic', async ({ page }) => {
  const token = await tokenAfterLogin(page, async () => {
    await login(page, users.agencyAgent, passwordFor('agencyAgent'));
    await page.goto('/tenant');
  });
  const clinicQueueId = await firstQueueId(page, token, 'clinic');

  expect((await callApi(page, token, 'PUT', '/establishments/clinic', clinicProfile)).status).toBe(403);
  expect((await callApi(page, token, 'PUT', '/establishments/clinic/status', { active: false })).status).toBe(403);
  expect((await callApi(page, token, 'POST', '/establishments/clinic/queues', {
    name: 'Intruse', prefix: 'X', avgServiceMinutes: 3, counter: 'G',
  })).status).toBe(403);
  expect((await callApi(page, token, 'PUT', `/queues/${clinicQueueId}`, {
    name: 'Détournée', prefix: 'A', avgServiceMinutes: 3, counter: 'Guichet 3',
  })).status).toBe(403);
  expect((await callApi(page, token, 'DELETE', `/queues/${clinicQueueId}`)).status).toBe(403);
});
