#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v node >/dev/null || { echo "Node 22 ou plus recent est requis."; exit 1; }
node -e 'if(Number(process.versions.node.split(".")[0])<22){console.error("Node 22+ requis");process.exit(1)}'
exec node server/start.cjs
