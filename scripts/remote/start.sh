#!/bin/sh
# Start the Summer Fest app on the remote. Idempotent — refuses if already up.
# Reads env passed in by the SSH caller (REMOTE_ROOT, APP_PORT, NREPL_PORT, ...).
set -eu

: "${REMOTE_ROOT:?must be set}"
: "${APP_PORT:?must be set}"
: "${NREPL_PORT:?must be set}"
: "${BASE_PATH:?must be set}"

CODE_DIR="$REMOTE_ROOT/code"
RUN_DIR="$REMOTE_ROOT/run"
LOG_DIR="$REMOTE_ROOT/log"
UPLOAD_DIR="$REMOTE_ROOT/uploads"
PID_FILE="$RUN_DIR/app.pid"
LOG_FILE="$LOG_DIR/app.log"
ENV_FILE="$REMOTE_ROOT/.env"

mkdir -p "$RUN_DIR" "$LOG_DIR" "$UPLOAD_DIR"

# Server-side secrets (SESSION_SECRET, ...). Generated once per environment;
# never shipped from the dev machine. See README for bootstrap.
if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: $ENV_FILE missing. Bootstrap with:"
  echo "  umask 077 && echo \"SESSION_SECRET=\$(openssl rand -base64 32)\" >> $ENV_FILE"
  exit 1
fi
# shellcheck disable=SC1090
. "$ENV_FILE"
: "${SESSION_SECRET:?must be set in $ENV_FILE}"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Already running (pid $(cat "$PID_FILE")). Use stop.sh first."
  exit 0
fi

cd "$CODE_DIR"

# Pre-warm classpath/deps cache. The Alpine `clojure` package ships an old
# tools.deps CLI (1.9.0.315) which only knows `-R` (resolve-deps) / `-C`
# (classpath) — not `-M`, `-A`, or `-P`. So we use `-R:prod -Spath`.
clojure -Spath -R:prod >/dev/null

export PORT="$APP_PORT"
export BIND_IP="${BIND_IP:-127.0.0.1}"
export NREPL_PORT
export BASE_PATH
export UPLOAD_DIR
export DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD
export SESSION_SECRET
# Optional Google Sheets integration — sheets.clj silently skips when unset.
[ -n "${GOOGLE_SERVICE_KEY_JSON:-}" ] && export GOOGLE_SERVICE_KEY_JSON
[ -n "${GOOGLE_SHEETS_ID:-}" ] && export GOOGLE_SHEETS_ID

# `-R:prod` pulls nrepl into the classpath; `-m summerfest.core` runs main.
nohup clojure -J-Xmx512m -J-Xms128m -R:prod -m summerfest.core \
  >"$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"

# Wait for the http port to come up so the script reflects real status.
# Cold start on a small VPS can take 30-40s while deps + nREPL load.
i=0
while [ "$i" -lt 60 ]; do
  if nc -z 127.0.0.1 "$APP_PORT" 2>/dev/null; then
    echo "Started (pid $(cat "$PID_FILE")). App on 127.0.0.1:$APP_PORT, nREPL on 127.0.0.1:$NREPL_PORT."
    exit 0
  fi
  i=$((i + 1))
  sleep 1
done

echo "WARN: process started (pid $(cat "$PID_FILE")) but $APP_PORT not listening yet — check $LOG_FILE."
exit 1
