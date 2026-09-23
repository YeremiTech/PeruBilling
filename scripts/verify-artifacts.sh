#!/usr/bin/env bash
set -euo pipefail

DEFAULT_ARTIFACTS_BACKEND="filesystem"
if [[ ",${SPRING_PROFILES_ACTIVE:-}," == *,prod,* ]]; then DEFAULT_ARTIFACTS_BACKEND="database"; fi
ARTIFACTS_BACKEND="${ARTIFACTS_BACKEND:-$DEFAULT_ARTIFACTS_BACKEND}"

if [[ "$ARTIFACTS_BACKEND" == "database" ]]; then
  : "${DATABASE_URL:?DATABASE_URL es obligatorio para verificar artefactos database}"
  command -v psql >/dev/null || { echo "psql no está instalado" >&2; exit 1; }
  PG_DATABASE_URL="${DATABASE_URL#jdbc:}"
  case "$PG_DATABASE_URL" in
    postgresql://*|postgres://*) ;;
    *) echo "DATABASE_URL debe usar jdbc:postgresql://, postgresql:// o postgres://" >&2; exit 1 ;;
  esac
  if [[ -n "${DATABASE_USERNAME:-}" ]]; then export PGUSER="$DATABASE_USERNAME"; fi
  if [[ -n "${DATABASE_PASSWORD:-}" ]]; then export PGPASSWORD="$DATABASE_PASSWORD"; fi

  result="$(psql "$PG_DATABASE_URL" -v ON_ERROR_STOP=1 -Atc "
      SELECT count(*)
      FROM artifact_blob
      WHERE content_length <> octet_length(content)
         OR sha256 <> encode(sha256(content), 'hex');")"
  [[ "$result" =~ ^[0-9]+$ ]] || { echo "Respuesta inesperada verificando artifact_blob: $result" >&2; exit 1; }
  if [[ "$result" != "0" ]]; then
    echo "Se detectaron $result artefactos corruptos en PostgreSQL" >&2
    exit 1
  fi
  echo "Integridad de artifact_blob OK."
  exit 0
fi

if [[ "$ARTIFACTS_BACKEND" != "filesystem" ]]; then
  echo "ARTIFACTS_BACKEND no soportado por verify-artifacts.sh: $ARTIFACTS_BACKEND" >&2
  exit 1
fi

ARTIFACTS_ROOT="${ARTIFACTS_ROOT:-./data/artifacts}"
[[ -d "$ARTIFACTS_ROOT" ]] || { echo "No existe $ARTIFACTS_ROOT" >&2; exit 1; }
fail=0
checked=0
while IFS= read -r -d '' sum; do
  checked=$((checked + 1))
  dir="$(dirname "$sum")"
  if ! (cd "$dir" && sha256sum -c "$(basename "$sum")" >/dev/null); then
    echo "Checksum inválido: $sum" >&2
    fail=1
  fi
done < <(find "$ARTIFACTS_ROOT" -type f -name '*.sha256' -print0)
if [[ "$checked" -eq 0 ]]; then
  echo "No se encontraron manifests .sha256 en $ARTIFACTS_ROOT" >&2
  exit 1
fi
if [[ "$fail" -eq 0 ]]; then
  echo "Integridad filesystem OK: $checked manifests verificados."
fi
exit "$fail"
