#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
VERSION=8.11.1
# Official Gradle release-checksums reference, verified 2026-10-05.
EXPECTED_SHA256=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6
CACHE="$ROOT/.gradle-bootstrap"
DIST="$CACHE/gradle-$VERSION"
ZIP="$CACHE/gradle-$VERSION-bin.zip"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$CACHE"
  URL="https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
  echo "Téléchargement de Gradle $VERSION depuis $URL"
  if command -v curl >/dev/null 2>&1; then curl -fL "$URL" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP" "$URL"
  else echo "curl ou wget est requis." >&2; exit 2; fi
  if command -v sha256sum >/dev/null 2>&1; then ACTUAL=$(sha256sum "$ZIP" | awk '{print $1}')
  elif command -v shasum >/dev/null 2>&1; then ACTUAL=$(shasum -a 256 "$ZIP" | awk '{print $1}')
  else echo "Outil SHA-256 requis : aucun code téléchargé ne sera exécuté." >&2; exit 2; fi
  if [ "$ACTUAL" != "$EXPECTED_SHA256" ]; then
    rm -f "$ZIP"; echo "Empreinte Gradle incorrecte : archive refusée." >&2; exit 3
  fi
  if command -v unzip >/dev/null 2>&1; then unzip -q -o "$ZIP" -d "$CACHE"
  else echo "unzip est requis." >&2; exit 2; fi
fi
exec "$DIST/bin/gradle" -p "$ROOT" "$@"
