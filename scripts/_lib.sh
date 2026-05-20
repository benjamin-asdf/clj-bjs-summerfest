# Shared helpers for deploy scripts. Source from other scripts via:
#   . "$(dirname "$0")/_lib.sh"
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Load shared env, then optional local override.
. "$SCRIPT_DIR/deploy.env"
if [ -f "$SCRIPT_DIR/deploy.local.env" ]; then
  . "$SCRIPT_DIR/deploy.local.env"
fi

remote() {
  ssh "$REMOTE_HOST" "$@"
}

remote_sh() {
  # Runs the given shell snippet on the remote with our env exported.
  # SESSION_SECRET is intentionally NOT shipped from the dev machine — it
  # lives on the server in $REMOTE_ROOT/.env and is sourced by start.sh.
  ssh "$REMOTE_HOST" \
    "REMOTE_ROOT='$REMOTE_ROOT' \
     BASE_PATH='$BASE_PATH' \
     APP_PORT='$APP_PORT' \
     NREPL_PORT='$NREPL_PORT' \
     DB_HOST='$DB_HOST' \
     DB_PORT='$DB_PORT' \
     DB_NAME='$DB_NAME' \
     DB_USER='$DB_USER' \
     DB_PASSWORD='$DB_PASSWORD' \
     sh -s" -- "$@"
}
