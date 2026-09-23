#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./scripts/verify-project-auto.sh

XSD_ROOT="sunat-resources/xsd"
RULES_FILE="sunat-resources/rules/reglas-validacion-cpe-2026-08-26.xlsx"
RULES_HASH="$RULES_FILE.sha256"

[[ -f "$XSD_ROOT/XSD_FILES.sha256" ]] || { echo "ERROR: falta XSD_FILES.sha256; instale XSD oficiales fijados por SHA-256." >&2; exit 10; }
[[ -f "$XSD_ROOT/INSTALLATION.sha256" ]] || { echo "ERROR: falta INSTALLATION.sha256 de XSD." >&2; exit 11; }
(
  cd "$XSD_ROOT"
  sha256sum -c XSD_FILES.sha256
)

[[ -f "$RULES_FILE" && -f "$RULES_HASH" ]] || {
  echo "ERROR: faltan reglas SUNAT oficiales o su checksum para baseline 2026-08-26." >&2
  exit 12
}
(
  cd "$(dirname "$RULES_FILE")"
  sha256sum -c "$(basename "$RULES_HASH")"
)

./scripts/verify-sunat-beta-evidence.sh

echo "Regulatory release gate OK: código, XSD, reglas y evidencia SUNAT BETA verificados."
