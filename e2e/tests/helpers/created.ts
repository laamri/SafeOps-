import { appendFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

// Only rows recorded here are deleted by e2e/cleanup.sh; the suite never truncates tables.
const outputDir = process.env.E2E_OUTPUT_DIR ?? 'test-results';
const ticketsFile = join(outputDir, 'created-tickets.txt');
const usersFile = join(outputDir, 'created-users.txt');
const queuesFile = join(outputDir, 'created-queues.txt');

export function recordCreatedTicket(id: string): void {
  mkdirSync(outputDir, { recursive: true });
  appendFileSync(ticketsFile, `${id}\n`);
}

/** ServiceQueue ids created by the suite; cleanup deletes only these, and only while empty. */
export function recordCreatedQueue(id: number): void {
  mkdirSync(outputDir, { recursive: true });
  appendFileSync(queuesFile, `${id}\n`);
}

/** Keycloak usernames created by the suite; cleanup deletes exactly these accounts. */
export function recordCreatedUser(username: string): void {
  mkdirSync(outputDir, { recursive: true });
  appendFileSync(usersFile, `${username}\n`);
}
