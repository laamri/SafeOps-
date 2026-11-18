import { appendFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

// Only rows recorded here are deleted by e2e/cleanup.sh; the suite never truncates tables.
const outputDir = process.env.E2E_OUTPUT_DIR ?? 'test-results';
const ticketsFile = join(outputDir, 'created-tickets.txt');
const usersFile = join(outputDir, 'created-users.txt');

export function recordCreatedTicket(id: string): void {
  mkdirSync(outputDir, { recursive: true });
  appendFileSync(ticketsFile, `${id}\n`);
}

/** Keycloak usernames created by the suite; cleanup deletes exactly these accounts. */
export function recordCreatedUser(username: string): void {
  mkdirSync(outputDir, { recursive: true });
  appendFileSync(usersFile, `${username}\n`);
}
