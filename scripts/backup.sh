#!/usr/bin/env bash
# Dumps the WORKHUB database to a compressed custom-format file (restore with scripts/restore.sh).
#   scripts/backup.sh [output-dir]            default: ./backups
# Uses the postgres container of the compose file in COMPOSE_FILE (default docker-compose.yml; use
# docker-compose.prod.yml for production). Keeps the newest BACKUP_KEEP files (default 14).
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
OUT_DIR="${1:-backups}"
KEEP="${BACKUP_KEEP:-14}"
DB_USER="${DB_USERNAME:-workhub}"
STAMP="$(date +%Y%m%d-%H%M%S)"
FILE="$OUT_DIR/workhub-$STAMP.dump"

mkdir -p "$OUT_DIR"
docker compose -f "$COMPOSE_FILE" exec -T postgres pg_dump -U "$DB_USER" -d workhub --format=custom --compress=6 > "$FILE.partial"
mv "$FILE.partial" "$FILE"
echo "Backup written: $FILE ($(du -h "$FILE" | cut -f1))"

# Retention: only ever touches this script's own workhub-*.dump files inside OUT_DIR, newest $KEEP stay.
ls -1t "$OUT_DIR"/workhub-*.dump 2>/dev/null | tail -n +"$((KEEP + 1))" | while read -r old; do
  case "$old" in
    "$OUT_DIR"/workhub-*.dump) rm -f -- "$old"; echo "Removed old backup: $old" ;;
  esac
done
