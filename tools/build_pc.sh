#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p "$ROOT/desktop/dist" "$TMP/classes"
# Both packaged Web copies are rebuilt from the canonical source before compilation.
node "$ROOT/tools/sync_web.cjs"
javac --release 17 -d "$TMP/classes" "$ROOT/desktop/src/com/franchiseguide/desktop/"*.java
jar --create --file "$ROOT/desktop/dist/FranchiseGuide-PC.jar" --main-class com.franchiseguide.desktop.Main -C "$TMP/classes" . -C "$ROOT/desktop/resources" .
java -jar "$ROOT/desktop/dist/FranchiseGuide-PC.jar" --probe
