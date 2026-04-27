#!/usr/bin/env bash
# Apply database migrations.
#
#   ./scripts/migrate.sh             # local DB (uses local DB_* env or defaults)
#   ./scripts/migrate.sh --remote    # live remote server, via the running nREPL
#
# Local mode spawns a one-shot Clojure process and calls (summerfest.db/migrate!).
# Remote mode evals (summerfest.db/migrate!) on the running production app
# through scripts/repl-tunnel.sh — useful when you've added a migration and
# don't want to restart the live server.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

if [ "${1:-}" = "--remote" ]; then
  echo "Running migrations on remote via nREPL tunnel..."
  exec "$SCRIPT_DIR/repl-tunnel.sh" --eval \
    "(do (require 'summerfest.db) (summerfest.db/migrate!))"
fi

cd "$PROJECT_ROOT"
echo "Running migrations on local DB (${DB_HOST:-localhost}:${DB_PORT:-5432}/${DB_NAME:-summerfest})..."
clojure -M -e "(require 'summerfest.db) (summerfest.db/migrate!)"
