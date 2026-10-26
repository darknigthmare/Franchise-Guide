#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
exec java -jar dist/FranchiseGuide-PC.jar
