#!/usr/bin/env sh
set -eu

ROOT="${1:-./sunat-resources/xsd}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

URL21="${SUNAT_XSD21_URL:-https://cpe.sunat.gob.pe/sites/default/files/inline-files/Archivos%20XSD%20%281%29.zip}"
URL20="${SUNAT_XSD20_URL:-https://cpe.sunat.gob.pe/sites/default/files/inline-files/XSL%20-%20UBL%202.0%20%281%29%20%281%29.zip}"
SHA21="${SUNAT_XSD21_SHA256:-}"
SHA20="${SUNAT_XSD20_SHA256:-}"
ALLOW_UNPINNED="${SUNAT_XSD_ALLOW_UNPINNED:-false}"
STAGE="$TMP/stage"
mkdir -p "$STAGE/ubl21" "$STAGE/ubl20"

verify_sha() {
  file="$1"
  expected="$2"
  label="$3"
  if [ -z "$expected" ]; then
    if [ "$ALLOW_UNPINNED" = "true" ]; then
      echo "ADVERTENCIA: $label sin checksum fijado (solo permitido por SUNAT_XSD_ALLOW_UNPINNED=true)." >&2
      return 0
    fi
    echo "Falta checksum $label. Configure SUNAT_XSD21_SHA256 y SUNAT_XSD20_SHA256." >&2
    exit 2
  fi
  actual="$(sha256sum "$file" | awk '{print $1}')"
  if [ "$actual" != "$expected" ]; then
    echo "Checksum inválido para $label. Esperado=$expected actual=$actual" >&2
    exit 3
  fi
}

download_zip() {
  url="$1"
  dest="$2"
  sha="$3"
  label="$4"
  echo "Descargando $label desde SUNAT..."
  curl --fail --location --proto '=https' --tlsv1.2 --retry 3 --retry-all-errors "$url" -o "$dest"
  unzip -tq "$dest" >/dev/null
  verify_sha "$dest" "$sha" "$label"
}

assert_schema_in_stage() {
  name="$1"
  if ! find "$STAGE" -type f -name "$name" -print -quit | grep -q .; then
    echo "No se encontró el esquema obligatorio: $name" >&2
    exit 4
  fi
}

download_zip "$URL21" "$TMP/ubl21.zip" "$SHA21" "SUNAT XSD UBL 2.1"
download_zip "$URL20" "$TMP/ubl20.zip" "$SHA20" "SUNAT XSD UBL 2.0"

unzip -oq "$TMP/ubl21.zip" -d "$STAGE/ubl21"
unzip -oq "$TMP/ubl20.zip" -d "$STAGE/ubl20"

assert_schema_in_stage 'UBL-Invoice-2.1.xsd'
assert_schema_in_stage 'UBL-CreditNote-2.1.xsd'
assert_schema_in_stage 'UBL-DebitNote-2.1.xsd'
assert_schema_in_stage 'SummaryDocuments-1.xsd'
assert_schema_in_stage 'VoidedDocuments-1.xsd'

cat > "$STAGE/INSTALLATION.sha256" <<MANIFEST
ubl21_zip_sha256=$(sha256sum "$TMP/ubl21.zip" | awk '{print $1}')
ubl20_zip_sha256=$(sha256sum "$TMP/ubl20.zip" | awk '{print $1}')
installed_at_utc=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
MANIFEST

(
  cd "$STAGE"
  find ubl21 ubl20 -type f -name '*.xsd' -print0 \
    | sort -z \
    | xargs -0 sha256sum > XSD_FILES.sha256
)

mkdir -p "$ROOT"
BACKUP="$TMP/previous"
mkdir -p "$BACKUP"
for name in ubl21 ubl20 INSTALLATION.sha256 XSD_FILES.sha256; do
  if [ -e "$ROOT/$name" ]; then
    mv "$ROOT/$name" "$BACKUP/$name"
  fi
done

rollback() {
  for name in ubl21 ubl20 INSTALLATION.sha256 XSD_FILES.sha256; do
    rm -rf "$ROOT/$name"
    if [ -e "$BACKUP/$name" ]; then
      mv "$BACKUP/$name" "$ROOT/$name"
    fi
  done
}

if ! mv "$STAGE/ubl21" "$ROOT/ubl21" \
  || ! mv "$STAGE/ubl20" "$ROOT/ubl20" \
  || ! mv "$STAGE/INSTALLATION.sha256" "$ROOT/INSTALLATION.sha256" \
  || ! mv "$STAGE/XSD_FILES.sha256" "$ROOT/XSD_FILES.sha256"; then
  rollback
  echo "No se pudo activar la nueva instalación XSD; se restauró la anterior." >&2
  exit 5
fi

if ! (cd "$ROOT" && sha256sum -c XSD_FILES.sha256 >/dev/null); then
  rollback
  echo "La verificación posterior de XSD falló; se restauró la instalación anterior." >&2
  exit 6
fi

echo "XSD SUNAT UBL 2.1 y UBL 2.0 instalados y verificados en $ROOT"
