#!/usr/bin/env bash
# Deletes only the tickets recorded by the E2E run, then repairs last_number on the queues those
# tickets belonged to. Never truncates a table and never touches rows the tests did not create.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
FILE="$ROOT/e2e/test-results/created-tickets.txt"
CONTAINER="${E2E_POSTGRES_CONTAINER:-tiko-audit-postgres-1}"

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
