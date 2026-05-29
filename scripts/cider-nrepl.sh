#!/usr/bin/env bash
# Standalone CIDER nREPL (no http server). Connect from Emacs, then
# (require 'summerfest.core) and (summerfest.core/start!) to boot the app.
#
# Uses inline -Sdeps to pull cider-nrepl, and the user-level aliases
# :lib/tools-deps+slf4j-nop and :dev/snitch from ~/.clojure/deps.edn.
set -euo pipefail

cd "$(dirname "$0")/.."

ENV_FILE=".env"
if [ ! -f "$ENV_FILE" ]; then
  echo "Bootstrapping $ENV_FILE with a fresh SESSION_SECRET..."
  umask 077
  echo "SESSION_SECRET=$(openssl rand -base64 32)" > "$ENV_FILE"
fi

set -a
# shellcheck disable=SC1090
. "$ENV_FILE"
set +a

export PORT="${PORT:-3030}"
export NREPL_PORT="${NREPL_PORT:-7889}"

exec /usr/bin/clojure -J-XX:-OmitStackTraceInFastThrow \
  -Sdeps '{:deps {nrepl/nrepl {:mvn/version "1.3.1"} cider/cider-nrepl {:mvn/version "0.57.0"}} :aliases {:cider/nrepl {:jvm-opts ["-Djdk.attach.allowAttachSelf"], :main-opts ["-m" "nrepl.cmdline" "--middleware" "[cider.nrepl/cider-middleware]"]}}}' \
  -M:lib/tools-deps+slf4j-nop:dev/snitch:cider/nrepl
