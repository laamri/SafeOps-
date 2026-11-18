#!/usr/bin/env bash
# Deletes only the tickets and staff accounts recorded by the E2E run, then repairs last_number
# on the queues those tickets belonged to. Never truncates a table, never deletes a fixture
# user: only the rows the tests created are touched.
set -euo pipefail
# Git Bash would rewrite container-side paths like /tmp/e2e-cleanup.config into Windows paths.
export MSYS_NO_PATHCONV=1

ROOT=$(cd "$(dirname "$0")/.." && pwd)
FILE="$ROOT/e2e/test-results/created-tickets.txt"
USERS_FILE="$ROOT/e2e/test-results/created-users.txt"
CONTAINER="${E2E_POSTGRES_CONTAINER:-tiko-audit-postgres-1}"
KEYCLOAK_CONTAINER="${E2E_KEYCLOAK_CONTAINER:-tiko-audit-keycloak-1}"

cleanup_users() {
  [ -s "$USERS_FILE" ] || return 0
  local usernames username id admin password config=/tmp/e2e-cleanup.config
  usernames=$(tr -d '\r' < "$USERS_FILE" | grep -E '^[a-z0-9][a-z0-9._-]{2,63}$' | sort -u || true)
  [ -n "$usernames" ] || { : > "$USERS_FILE"; return 0; }

  admin="${KC_ADMIN:-}"; password="${KC_ADMIN_PASSWORD:-}"
  if [ -z "$admin" ] || [ -z "$password" ]; then
    # read from .env without echoing: the credentials are only passed to kcadm
    admin=$(sed -n 's/^KC_ADMIN=//p' "$ROOT/.env" | head -n1)
    password=$(sed -n 's/^KC_ADMIN_PASSWORD=//p' "$ROOT/.env" | head -n1)
  fi
  [ -n "$admin" ] && [ -n "$password" ] || { echo "e2e cleanup: no Keycloak admin credentials"; return 0; }

  if ! docker exec "$KEYCLOAK_CONTAINER" /opt/keycloak/bin/kcadm.sh config credentials \
    --config "$config" --server http://localhost:8080 --realm master \
    --user "$admin" --password "$password" >/dev/null 2>&1; then
    # keep the list: a later run can still delete these accounts
    echo "e2e cleanup: Keycloak login failed, test users kept for a retry"
    return 0
  fi
  : > "$USERS_FILE"

  for username in $usernames; do
    id=$(docker exec "$KEYCLOAK_CONTAINER" /opt/keycloak/bin/kcadm.sh get users -r tiko \
      -q "username=$username" -q exact=true --fields id --format csv --noquotes \
      --config "$config" 2>/dev/null | head -n1 | tr -d '\r' || true)
    if [ -n "$id" ]; then
      docker exec "$KEYCLOAK_CONTAINER" /opt/keycloak/bin/kcadm.sh delete "users/$id" -r tiko \
        --config "$config" >/dev/null 2>&1
      echo "e2e cleanup: removed test user $username"
    fi
  done
}

cleanup_users

if [ ! -s "$FILE" ]; then
  echo "e2e cleanup: nothing recorded"
  exit 0
fi

IDS=$(tr -d '\r' < "$FILE" | grep -E '^[0-9a-fA-F-]{36}$' | sort -u | tr '\n' ' ')
: > "$FILE"
if [ -z "$IDS" ]; then
  echo "e2e cleanup: nothing recorded"
  exit 0
fi

LIST=$(printf "'%s'," $IDS)
LIST=${LIST%,}
COUNT=$(echo "$IDS" | wc -w)

# The DELETE and the UPDATE share one snapshot, so the recomputed MAX must exclude the ids
# being deleted explicitly; otherwise it counts the very rows this statement removes.
docker exec -i "$CONTAINER" psql -U postgres -d tiko -v ON_ERROR_STOP=1 >/dev/null <<SQL
WITH deleted AS (
    DELETE FROM ticket WHERE id IN ($LIST)
    RETURNING queue_id
)
UPDATE service_queue q
SET last_number = COALESCE(
    (SELECT MAX(t.number) FROM ticket t WHERE t.queue_id = q.id AND t.id NOT IN ($LIST)),
    0)
WHERE q.id IN (SELECT queue_id FROM deleted);
SQL

echo "e2e cleanup: removed $COUNT test ticket(s)"
