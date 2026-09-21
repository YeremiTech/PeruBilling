#!/bin/sh
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROPS="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"
[ -f "$PROPS" ] || { echo "Missing $PROPS" >&2; exit 1; }
prop() { sed -n "s/^$1=//p" "$PROPS" | tail -n 1; }
URL=$(prop distributionUrl)
EXPECTED=$(prop distributionSha256Sum)
MAVEN_VERSION=$(printf '%s' "$URL" | sed -n 's#.*apache-maven/\([^/]*\)/apache-maven-.*#\1#p')
[ -n "$MAVEN_VERSION" ] || { echo "Cannot infer Maven version" >&2; exit 1; }
MAVEN_HOME_BASE=${MAVEN_USER_HOME:-"$HOME/.m2"}/wrapper/dists/perubilling-maven-$MAVEN_VERSION
MAVEN_HOME="$MAVEN_HOME_BASE/apache-maven-$MAVEN_VERSION"
ZIP="$MAVEN_HOME_BASE/apache-maven-$MAVEN_VERSION-bin.zip"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$MAVEN_HOME_BASE"
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then curl -fL "$URL" -o "$ZIP";
    elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "$URL";
    else echo "curl o wget es requerido para descargar Maven" >&2; exit 1; fi
  fi
  if command -v sha256sum >/dev/null 2>&1; then ACTUAL=$(sha256sum "$ZIP" | awk '{print $1}');
  elif command -v shasum >/dev/null 2>&1; then ACTUAL=$(shasum -a 256 "$ZIP" | awk '{print $1}');
  else echo "sha256sum o shasum es requerido" >&2; exit 1; fi
  [ "$ACTUAL" = "$EXPECTED" ] || { echo "Checksum Maven inválido" >&2; rm -f "$ZIP"; exit 1; }
  command -v unzip >/dev/null 2>&1 || { echo "unzip es requerido" >&2; exit 1; }
  rm -rf "$MAVEN_HOME"
  unzip -q "$ZIP" -d "$MAVEN_HOME_BASE"
fi
exec "$MAVEN_HOME/bin/mvn" "$@"
