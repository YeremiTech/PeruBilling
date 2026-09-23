#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${PERUBILLING_VERIFY_IMAGE:-maven:3.9.16-eclipse-temurin-25}"
M2_VOLUME="${PERUBILLING_M2_VOLUME:-perubilling_m2_cache}"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker es requerido para la verificación contenerizada." >&2
  exit 20
fi

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker está instalado pero el daemon no está disponible para el usuario actual." >&2
  exit 21
fi

if [[ ! -S /var/run/docker.sock ]]; then
  echo "ERROR: Testcontainers requiere acceso a /var/run/docker.sock." >&2
  exit 22
fi

docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -e TESTCONTAINERS_RYUK_DISABLED=false \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$ROOT_DIR:/workspace" \
  -v "$M2_VOLUME:/root/.m2" \
  -w /workspace \
  "$IMAGE" \
  bash -lc './scripts/verify-project.sh'
