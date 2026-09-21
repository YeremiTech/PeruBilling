#!/usr/bin/env bash
set -euo pipefail

: "${DATABASE_URL:?DATABASE_URL es obligatorio}"
: "${BACKUP_PATH:?BACKUP_PATH debe apuntar al directorio del backup}"
: "${RESTORE_CONFIRM:?Defina RESTORE_CONFIRM=YES para permitir restauración destructiva}"
[[ "$RESTORE_CONFIRM" == "YES" ]] || { echo "RESTORE_CONFIRM debe ser YES" >&2; exit 1; }

DEFAULT_ARTIFACTS_BACKEND="filesystem"
if [[ ",${SPRING_PROFILES_ACTIVE:-}," == *,prod,* ]]; then DEFAULT_ARTIFACTS_BACKEND="database"; fi
ARTIFACTS_BACKEND="${ARTIFACTS_BACKEND:-$DEFAULT_ARTIFACTS_BACKEND}"
ARTIFACTS_ROOT="${ARTIFACTS_ROOT:-./data/artifacts}"
command -v pg_restore >/dev/null || { echo "pg_restore no está instalado" >&2; exit 1; }
command -v sha256sum >/dev/null || { echo "sha256sum no está instalado" >&2; exit 1; }

PG_DATABASE_URL="${DATABASE_URL#jdbc:}"
case "$PG_DATABASE_URL" in
  postgresql://*|postgres://*) ;;
  *) echo "DATABASE_URL debe usar jdbc:postgresql://, postgresql:// o postgres://" >&2; exit 1 ;;
esac
if [[ -n "${DATABASE_USERNAME:-}" ]]; then export PGUSER="$DATABASE_USERNAME"; fi
if [[ -n "${DATABASE_PASSWORD:-}" ]]; then export PGPASSWORD="$DATABASE_PASSWORD"; fi

cd "$BACKUP_PATH"
[[ -s SHA256SUMS && -s METADATA.txt && -s database.dump ]] || { echo "Backup incompleto" >&2; exit 1; }
sha256sum -c SHA256SUMS

backup_format="$(awk -F= '$1 == "format" {print $2}' METADATA.txt)"
backup_backend="$(awk -F= '$1 == "artifacts_backend" {print $2}' METADATA.txt)"
[[ "$backup_format" == "perubilling-backup-v3" ]] || { echo "Formato de backup no soportado: $backup_format" >&2; exit 1; }
[[ "$backup_backend" == "$ARTIFACTS_BACKEND" ]] || {
  echo "Backend de artefactos incompatible: backup=$backup_backend restore=$ARTIFACTS_BACKEND" >&2
  exit 1
}

pg_restore --exit-on-error --clean --if-exists --no-owner --no-acl --dbname "$PG_DATABASE_URL" database.dump

if [[ "$ARTIFACTS_BACKEND" == "filesystem" ]]; then
  command -v tar >/dev/null || { echo "tar no está instalado" >&2; exit 1; }
  [[ -f artifacts.tar.gz ]] || { echo "Falta artifacts.tar.gz para backend filesystem" >&2; exit 1; }
  mkdir -p "$ARTIFACTS_ROOT"
  find "$ARTIFACTS_ROOT" -mindepth 1 -maxdepth 1 -exec rm -rf {} +
  tar -C "$ARTIFACTS_ROOT" -xzf artifacts.tar.gz
fi

echo "Restauración completada. Ejecute health checks, verificación funcional y validación de artefactos antes de habilitar tráfico."
