#!/bin/sh
# Stop the Summer Fest app on the remote.
set -eu

: "${REMOTE_ROOT:?must be set}"
: "${APP_PORT:?must be set}"

PID_FILE="$REMOTE_ROOT/run/app.pid"

stop_pid() {
  pid="$1"
  kill -0 "$pid" 2>/dev/null || return 0
  kill "$pid" 2>/dev/null || true
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 1
  done
  kill -9 "$pid" 2>/dev/null || true
}

# 1. Try the PID file.
if [ -f "$PID_FILE" ]; then
  pid="$(cat "$PID_FILE")"
  if kill -0 "$pid" 2>/dev/null; then
    echo "Stopping pid $pid"
    stop_pid "$pid"
  else
    echo "Stale PID $pid — removing PID file."
  fi
  rm -f "$PID_FILE"
fi

# 2. Sweep up any orphaned process still holding our port (e.g. from a crash
#    where the PID file went away but the JVM is still around).
orphan_pids="$(netstat -ltnp 2>/dev/null \
                | awk -v p=":$APP_PORT" '$4 ~ p {print $7}' \
                | sed 's|/.*||' | sort -u)"
for pid in $orphan_pids; do
  [ -z "$pid" ] && continue
  echo "Killing orphan pid $pid still bound to :$APP_PORT"
  stop_pid "$pid"
done

echo "Stopped."
