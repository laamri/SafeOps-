import { expect, type Page, type Request } from '@playwright/test';

export type ApiResult = { status: number; body: any };

/**
 * Calls the API from the SPA origin, exactly as the app does: nginx forwards /api to the
 * gateway. Requests made inside the page also get the container's localhost mapping, which a
 * Node-side request context would not. The token is never printed.
 */
export async function callApi(
  page: Page,
  token: string,
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  body?: unknown,
): Promise<ApiResult> {
  return page.evaluate(async ({ token, method, path, body }) => {
    const response = await fetch(`/api${path}`, {
      method,
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    let parsed: unknown = null;
    try { parsed = text ? JSON.parse(text) : null; } catch { parsed = text; }
    return { status: response.status, body: parsed };
  }, { token, method, path, body });
}

/**
 * Runs the real login, then captures the bearer token the SPA received, so API-level specs can
 * exercise the same token the browser uses. Only the token value is kept; nothing is logged.
 */
export async function tokenAfterLogin(page: Page, login: () => Promise<void>): Promise<string> {
  let token: string | null = null;
  const listener = (request: Request) => {
    const header = request.headers()['authorization'];
    if (!token && header?.startsWith('Bearer ')) {
      token = header.slice('Bearer '.length);
    }
  };
  page.on('request', listener);
  try {
    await login();
    await expect.poll(() => token, { timeout: 30_000 }).not.toBeNull();
  } finally {
    page.off('request', listener);
  }
  if (!token) throw new Error('no bearer token was observed');
  return token;
}
