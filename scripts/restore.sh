#!/usr/bin/env bash
# Restores a dump made by scripts/backup.sh into the running database, REPLACING its current contents.
#   scripts/restore.sh backups/workhub-20260101-020000.dump
# Stop the backend first (docker compose stop backend) so nothing writes while restoring. COMPOSE_FILE as in backup.sh.
set -euo pipefail
cd "$(dirname "$0")/.."

FILE="${1:?usage: scripts/restore.sh <dump-file>}"
[ -f "$FILE" ] || { echo "No such file: $FILE" >&2; exit 1; }
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
DB_USER="${DB_USERNAME:-workhub}"

if [ "${ASSUME_YES:-}" != "1" ]; then
  read -r -p "This replaces ALL data in the '$COMPOSE_FILE' database with $FILE. Type 'restore' to continue: " answer
  [ "$answer" = "restore" ] || { echo "Cancelled."; exit 1; }
fi

# Start from an empty schema so objects created after the dump do not survive the restore.
docker compose -f "$COMPOSE_FILE" exec -T postgres psql -U "$DB_USER" -d workhub -v ON_ERROR_STOP=1 -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
docker compose -f "$COMPOSE_FILE" exec -T postgres pg_restore -U "$DB_USER" -d workhub --no-owner --exit-on-error < "$FILE"
echo "Restored $FILE"
