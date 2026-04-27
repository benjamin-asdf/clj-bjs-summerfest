#!/usr/bin/env bash
# Local dev: starts the http server (and an embedded nREPL on NREPL_PORT).
# PORT defaults to 3030 because 3000 is often taken by other dev servers.
set -euo pipefail

cd "$(dirname "$0")/.."

export PORT="${PORT:-3030}"
export NREPL_PORT="${NREPL_PORT:-7889}"

echo "Starting Summer Fest dev server on http://localhost:$PORT (nREPL on $NREPL_PORT)..."
clojure -M:prod
