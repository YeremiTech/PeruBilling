#!/usr/bin/env bash
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL es obligatorio}"
DEFAULT_ARTIFACTS_BACKEND="filesystem"
if [[ ",${SPRING_PROFILES_ACTIVE:-}," == *,prod,* ]]; then DEFAULT_ARTIFACTS_BACKEND="database"; fi
ARTIFACTS_BACKEND="${ARTIFACTS_BACKEND:-$DEFAULT_ARTIFACTS_BACKEND}"
ARTIFACTS_ROOT="${ARTIFACTS_ROOT:-./data/artifacts}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
TARGET="$BACKUP_DIR/perubilling-$STAMP"

command -v pg_dump >/dev/null || { echo "pg_dump no está instalado" >&2; exit 1; }
command -v sha256sum >/dev/null || { echo "sha256sum no está instalado" >&2; exit 1; }

PG_DATABASE_URL="${DATABASE_URL#jdbc:}"
case "$PG_DATABASE_URL" in
  postgresql://*|postgres://*) ;;
  *) echo "DATABASE_URL debe usar jdbc:postgresql://, postgresql:// o postgres://" >&2; exit 1 ;;
esac
if [[ -n "${DATABASE_USERNAME:-}" ]]; then export PGUSER="$DATABASE_USERNAME"; fi
if [[ -n "${DATABASE_PASSWORD:-}" ]]; then export PGPASSWORD="$DATABASE_PASSWORD"; fi

mkdir -p "$TARGET"

echo "[1/3] Backup PostgreSQL"
pg_dump --format=custom --no-owner --no-acl --file "$TARGET/database.dump" "$PG_DATABASE_URL"

files=(database.dump)
if [[ "$ARTIFACTS_BACKEND" == "filesystem" ]]; then
  command -v tar >/dev/null || { echo "tar no está instalado" >&2; exit 1; }
  echo "[2/3] Backup artefactos filesystem"
  if [[ ! -d "$ARTIFACTS_ROOT" ]]; then
    echo "Directorio de artefactos inexistente: $ARTIFACTS_ROOT" >&2
    exit 1
  fi
  tar -C "$ARTIFACTS_ROOT" -czf "$TARGET/artifacts.tar.gz" .
  files+=(artifacts.tar.gz)
else
  echo "[2/3] Artefactos incluidos dentro de PostgreSQL (backend=$ARTIFACTS_BACKEND)"
fi

cat > "$TARGET/METADATA.txt" <<META
created_at_utc=$STAMP
artifacts_backend=$ARTIFACTS_BACKEND
artifacts_root=$ARTIFACTS_ROOT
format=perubilling-backup-v3
META
files+=(METADATA.txt)

echo "[3/3] Manifest de integridad"
(
  cd "$TARGET"
  sha256sum "${files[@]}" > SHA256SUMS
)

echo "Backup creado: $TARGET"
