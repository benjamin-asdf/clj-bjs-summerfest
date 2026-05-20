#!/usr/bin/env bash
# Reload summerfest.i18n in the running dev nREPL so new translation strings
# take effect without restarting the server. `t` reads `translations` at call
# time, so no view re-require is needed — a browser refresh picks them up.
set -euo pipefail

NREPL_PORT="${NREPL_PORT:-7889}"
clj-nrepl-eval -p "$NREPL_PORT" "(require 'summerfest.i18n :reload) :ok"
