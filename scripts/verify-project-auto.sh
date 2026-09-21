#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

java_major=""
if command -v java >/dev/null 2>&1; then
  java_major="$(java -version 2>&1 | awk -F'\"' '/version/ {print $2}' | awk -F. '{print ($1 == "1" ? $2 : $1)}')"
fi

if [[ -n "$java_major" && "$java_major" -ge 25 ]]; then
  echo "Usando JDK local ${java_major}."
  exec ./scripts/verify-project.sh
fi

if command -v docker >/dev/null 2>&1; then
  echo "JDK 25 local no disponible; usando verificación reproducible en Docker."
  exec ./scripts/verify-project-docker.sh
fi

cat >&2 <<MSG
ERROR: no existe un entorno de verificación compatible.
Instale JDK 25 o Docker y ejecute nuevamente:
  ./scripts/verify-project-auto.sh
MSG
exit 23
