#!/usr/bin/env bash
# Sync code to the remote and (re)start the app.
#
# Usage:
#   scripts/deploy.sh           # rsync + restart
#   scripts/deploy.sh --no-restart   # rsync only
. "$(dirname "$0")/_lib.sh"

NO_RESTART=0
for arg in "$@"; do
  case "$arg" in
    --no-restart) NO_RESTART=1 ;;
    *) echo "unknown arg: $arg"; exit 2 ;;
  esac
done

echo "==> Ensuring remote dirs at $REMOTE_HOST:$REMOTE_ROOT"
remote "mkdir -p '$REMOTE_ROOT/code' '$REMOTE_ROOT/run' '$REMOTE_ROOT/log' '$REMOTE_ROOT/uploads'"

echo "==> Syncing code"
# Strip local-only files; keep uploads/run/log on the remote.
rsync -az --delete \
  --exclude='.git' \
  --exclude='.cpcache' \
  --exclude='.clj-kondo' \
  --exclude='.lsp' \
  --exclude='target' \
  --exclude='*.gpg' \
  --exclude='serviceaccount' \
  --exclude='uploads' \
  --exclude='scripts/deploy.local.env' \
  -e ssh \
  "$PROJECT_ROOT/" \
  "$REMOTE_HOST:$REMOTE_ROOT/code/"

if [ "$NO_RESTART" -eq 1 ]; then
  echo "==> Skipping restart (--no-restart)."
  exit 0
fi

echo "==> Restarting"
remote_sh <<EOF
sh '$REMOTE_ROOT/code/scripts/remote/stop.sh' || true
sh '$REMOTE_ROOT/code/scripts/remote/start.sh'
EOF

echo "==> Deployed. Tail logs with: scripts/logs.sh"
