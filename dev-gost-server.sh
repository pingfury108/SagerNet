#!/bin/bash
# Start a gost relay+ssh server for testing (dev box)
# Usage: bash dev-gost-server.sh [listen_port] [user] [pass] [upstream]
#   upstream: optional gost forwarder URL, e.g. http://192.168.32.190:8026
set -e
PORT="${1:-2233}"
USER="${2:-tester}"
PASS="${3:-test123}"
UPSTREAM="$4"
BIN="${GOST_BIN:-/data/gost/gost}"

if [ ! -x "$BIN" ]; then
  echo "gost binary not found at $BIN (build via dev-build-gost.sh, then copy to $BIN)" >&2
  exit 1
fi

if [ -f /data/gost/server.pid ]; then
  kill "$(cat /data/gost/server.pid)" 2>/dev/null || true
  sleep 1
fi

mkdir -p /data/gost
ARGS=(-L "relay+ssh://$USER:$PASS@:$PORT")
if [ -n "$UPSTREAM" ]; then
  ARGS+=(-F "$UPSTREAM")
fi

nohup "$BIN" "${ARGS[@]}" > /data/gost/server.log 2>&1 &
echo $! > /data/gost/server.pid
sleep 2
echo "gost relay+ssh listening on :$PORT (pid $(cat /data/gost/server.pid)), upstream: ${UPSTREAM:-direct}"
tail -2 /data/gost/server.log
