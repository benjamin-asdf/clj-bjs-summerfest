#!/usr/bin/env bash
# Stop the running app on the remote.
. "$(dirname "$0")/_lib.sh"

remote_sh <<EOF
sh '$REMOTE_ROOT/code/scripts/remote/stop.sh'
EOF
