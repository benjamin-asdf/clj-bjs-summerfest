#!/usr/bin/env bash
# Start the app on the remote without re-syncing code.
. "$(dirname "$0")/_lib.sh"

remote_sh <<EOF
sh '$REMOTE_ROOT/code/scripts/remote/start.sh'
EOF
