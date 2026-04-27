#!/usr/bin/env bash
# Open an SSH tunnel to the remote nREPL so you can connect locally.
#
# Default mapping: localhost:7888 -> remote 127.0.0.1:7888
# Connect with: clj-nrepl-eval -p $LOCAL_NREPL_PORT "(...)"
# Or any nREPL client (Cider, Calva, ...).
#
# Pass --eval "<form>" to evaluate a single form via clj-nrepl-eval and exit.
. "$(dirname "$0")/_lib.sh"

EVAL=""
while [ $# -gt 0 ]; do
  case "$1" in
    --eval) EVAL="$2"; shift 2 ;;
    -h|--help)
      sed -n '2,9p' "$0" | sed 's/^# //;s/^#//'
      exit 0 ;;
    *) echo "unknown arg: $1"; exit 2 ;;
  esac
done

if [ -n "$EVAL" ]; then
  if ! command -v clj-nrepl-eval >/dev/null; then
    echo "clj-nrepl-eval not found in PATH; falling back to nREPL via socat is unsupported here."
    echo "Install clj-nrepl-eval, or open the tunnel and use your editor."
    exit 1
  fi
  # Open a short-lived tunnel just for this eval.
  ssh -fNT -o ExitOnForwardFailure=yes \
      -L "$LOCAL_NREPL_PORT:127.0.0.1:$NREPL_PORT" \
      "$REMOTE_HOST"
  TUNNEL_PID=$(pgrep -nf "ssh -fNT.*-L $LOCAL_NREPL_PORT:127.0.0.1:$NREPL_PORT $REMOTE_HOST")
  trap 'kill "$TUNNEL_PID" 2>/dev/null || true' EXIT
  clj-nrepl-eval -p "$LOCAL_NREPL_PORT" "$EVAL"
  exit $?
fi

echo "Tunneling localhost:$LOCAL_NREPL_PORT -> $REMOTE_HOST:127.0.0.1:$NREPL_PORT"
echo "Connect with:  clj-nrepl-eval -p $LOCAL_NREPL_PORT \"(your-code)\""
echo "Ctrl-C to disconnect."
exec ssh -NT -o ExitOnForwardFailure=yes \
     -L "$LOCAL_NREPL_PORT:127.0.0.1:$NREPL_PORT" \
     "$REMOTE_HOST"
