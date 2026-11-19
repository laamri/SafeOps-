export const users = {
  client: 'client',
  agent: 'agent',
  admin: 'admin',
  agencyAgent: 'agent-agency',
  noSiteAgent: 'agent-nosite',
} as const;

export type FixtureUser = keyof typeof users;

const passwordEnv: Record<FixtureUser, string> = {
  client: 'E2E_CLIENT_PASSWORD',
  agent: 'E2E_AGENT_PASSWORD',
  admin: 'E2E_ADMIN_PASSWORD',
  agencyAgent: 'E2E_AGENCY_AGENT_PASSWORD',
  noSiteAgent: 'E2E_NOSITE_AGENT_PASSWORD',
};

export function passwordFor(user: FixtureUser): string {
  const value = process.env[passwordEnv[user]];
  if (!value) throw new Error(`missing ${passwordEnv[user]} in the test environment`);
  return value;
}
