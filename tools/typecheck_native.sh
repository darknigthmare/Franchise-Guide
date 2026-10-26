#!/usr/bin/env bash
set -euo pipefail
export JAVA_OPTS="${JAVA_OPTS:--Xmx2g}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KOTLIN_HOME="${KOTLIN_HOME:-$(cd "$(dirname "$(command -v kotlinc)")/.." && pwd)}"
COROUTINES_JAR="${COROUTINES_JAR:-$KOTLIN_HOME/lib/kotlinx-coroutines-core-jvm.jar}"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
src="$ROOT/app/src/main/java/com/franchiseguide/app"
kotlinc "$ROOT"/tests/typecheck-stubs/*.kt "$src"/{model,franchise,data,network}/*.kt -cp "$COROUTINES_JAR" -d "$TMP/non-ui.jar"
echo 'PASS: non-UI Kotlin compiles against signature stubs. This is NOT an Android build or parser runtime test.'
