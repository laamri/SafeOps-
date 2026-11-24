#!/usr/bin/env bash
# Runs the Playwright suite against an already-running stack. No local Node: the tests run in
# the Playwright container. Credentials come from .env and are passed as container env only.
set -euo pipefail

# Git Bash would rewrite container-side paths like /e2e/test-results into Windows paths.
export MSYS_NO_PATHCONV=1

ROOT=$(cd "$(dirname "$0")/.." && pwd)
# With path conversion disabled the Docker CLI needs a Windows-style path on Windows.
if command -v cygpath >/dev/null 2>&1; then ROOT=$(cygpath -m "$ROOT"); fi
cd "$ROOT"

OUT="$ROOT/e2e/test-results"
REPORT="$ROOT/e2e/playwright-report"
rm -rf "$OUT" "$REPORT"
mkdir -p "$OUT" "$REPORT"

set -a
# shellcheck disable=SC1091
. ./.env
set +a

docker build -t tiko/e2e:local "$ROOT/e2e"

status=0
docker run --rm \
  --add-host host.docker.internal:host-gateway \
  -e E2E_BASE_URL="${E2E_BASE_URL:-http://localhost:13001}" \
  -e E2E_HOST_RESOLVER="${E2E_HOST_RESOLVER-host.docker.internal}" \
  -e E2E_CLIENT_PASSWORD="$TIKO_CLIENT_PASSWORD" \
  -e E2E_AGENT_PASSWORD="$TIKO_AGENT_PASSWORD" \
  -e E2E_ADMIN_PASSWORD="$TIKO_ADMIN_PASSWORD" \
  -e E2E_AGENCY_AGENT_PASSWORD="$TIKO_AGENCY_AGENT_PASSWORD" \
  -e E2E_NOSITE_AGENT_PASSWORD="$TIKO_NOSITE_AGENT_PASSWORD" \
  -e E2E_OUTPUT_DIR=/e2e/test-results \
  -v "$OUT:/e2e/test-results" \
  -v "$REPORT:/e2e/playwright-report" \
  tiko/e2e:local npx playwright test || status=$?

bash "$ROOT/e2e/cleanup.sh"
exit "$status"
