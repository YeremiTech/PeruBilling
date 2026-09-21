#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec "$ROOT_DIR/scripts/verify-sunat-beta-matrix.sh" "${1:-$ROOT_DIR/sunat-resources/beta-fixtures}"
