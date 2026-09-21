#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
FAKEBIN="$TMP/bin"
BACKUPS="$TMP/backups"
mkdir -p "$FAKEBIN" "$BACKUPS"

cat > "$FAKEBIN/pg_dump" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
[[ "${PGUSER:-}" == "perubilling_test" ]]
[[ "${PGPASSWORD:-}" == "test-password" ]]
[[ "${*: -1}" == "postgresql://db.internal:5432/perubilling" ]]
out=""
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "--file" ]]; then out="$2"; shift 2; continue; fi
  shift
done
[[ -n "$out" ]]
printf 'fake-postgres-dump' > "$out"
SH

cat > "$FAKEBIN/pg_restore" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
[[ "${PGUSER:-}" == "perubilling_test" ]]
[[ "${PGPASSWORD:-}" == "test-password" ]]
printf '%s\n' "$*" | grep -q -- '--dbname postgresql://db.internal:5432/perubilling'
SH

cat > "$FAKEBIN/psql" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
[[ "${PGUSER:-}" == "perubilling_test" ]]
[[ "${PGPASSWORD:-}" == "test-password" ]]
[[ "$1" == "postgresql://db.internal:5432/perubilling" ]]
printf '0\n'
SH
chmod +x "$FAKEBIN/pg_dump" "$FAKEBIN/pg_restore" "$FAKEBIN/psql"

COMMON_ENV=(
  "PATH=$FAKEBIN:$PATH"
  "SPRING_PROFILES_ACTIVE=prod"
  "DATABASE_URL=jdbc:postgresql://db.internal:5432/perubilling"
  "DATABASE_USERNAME=perubilling_test"
  "DATABASE_PASSWORD=test-password"
  "ARTIFACTS_BACKEND=database"
)

env "${COMMON_ENV[@]}" BACKUP_DIR="$BACKUPS" bash "$ROOT_DIR/scripts/backup.sh" >/dev/null
backup_path="$(find "$BACKUPS" -mindepth 1 -maxdepth 1 -type d -print -quit)"
[[ -n "$backup_path" && -s "$backup_path/database.dump" && -s "$backup_path/SHA256SUMS" ]]

env "${COMMON_ENV[@]}" BACKUP_PATH="$backup_path" RESTORE_CONFIRM=YES \
  bash "$ROOT_DIR/scripts/restore.sh" >/dev/null

env "${COMMON_ENV[@]}" bash "$ROOT_DIR/scripts/verify-artifacts.sh" >/dev/null

echo "Operational scripts gate OK: JDBC URL normalization, credentials and database artifact verification."
