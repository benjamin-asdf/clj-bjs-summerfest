#!/usr/bin/env bash
# Tail the remote app log.
. "$(dirname "$0")/_lib.sh"

ssh -t "$REMOTE_HOST" "tail -F '$REMOTE_ROOT/log/app.log'"
