#!/usr/bin/env sh
set -eu
ROOT="${1:-./sunat-resources/rules}"
mkdir -p "$ROOT"
URL="${SUNAT_RULES_URL:-https://cpe.sunat.gob.pe/sites/default/files/2026-08/Reglas%20de%20validaci%C3%B3n%20-%20actualizado%20al%2026.08.2026.xlsx}"
OUT="$ROOT/reglas-validacion-cpe-2026-08-26.xlsx"
EXPECTED="${SUNAT_RULES_SHA256:-}"
ALLOW_UNPINNED="${SUNAT_RULES_ALLOW_UNPINNED:-false}"
TMP="$(mktemp "$ROOT/.rules-XXXXXX")"
trap 'rm -f "$TMP"' EXIT

echo "Descargando reglas oficiales SUNAT CPE 2026-08-26..."
curl --fail --location --proto '=https' --tlsv1.2 --retry 3 --retry-all-errors "$URL" -o "$TMP"
ACTUAL="$(sha256sum "$TMP" | awk '{print $1}')"

if [ -z "$EXPECTED" ] && [ "$ALLOW_UNPINNED" != "true" ]; then
  echo "SUNAT_RULES_SHA256 es obligatorio para una instalación reproducible." >&2
  echo "Hash descargado para revisión manual: $ACTUAL" >&2
  exit 2
fi
if [ -n "$EXPECTED" ] && [ "$ACTUAL" != "$EXPECTED" ]; then
  echo "Checksum de reglas SUNAT inválido. esperado=$EXPECTED actual=$ACTUAL" >&2
  exit 3
fi
mv -f "$TMP" "$OUT"
trap - EXIT
printf '%s  %s\n' "$ACTUAL" "$(basename "$OUT")" > "$OUT.sha256"
echo "Reglas instaladas y verificadas: $OUT"
