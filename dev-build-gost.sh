#!/bin/bash
# Build gost (go-gost/gost) binaries:
#   - Android arm64  -> app/src/main/jniLibs/arm64-v8a/libgost.so (bundled in APK)
#   - Linux amd64    -> /tmp/gost/gost (server side for dev)
# Requires: go 1.24+ (e.g. via mise), network access to go module proxy
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
GOST_DIR="${GOST_DIR:-/tmp/gost-src}"
GOST_REF="${GOST_REF:-v3.3.1}"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a/libgost.so"

if [ ! -d "$GOST_DIR" ]; then
  git clone --depth 1 --branch "$GOST_REF" https://github.com/go-gost/gost.git "$GOST_DIR"
fi

export GOPROXY="${GOPROXY:-https://goproxy.cn,direct}"
LD="-s -w -buildid="
mkdir -p "$(dirname "$OUT")" /tmp/gost

cd "$GOST_DIR"
git checkout -q "$GOST_REF" 2>/dev/null || true

CGO_ENABLED=0 GOOS=android GOARCH=arm64 go build -trimpath -ldflags="$LD" -o "$OUT" ./cmd/gost
echo ">> android arm64: $OUT"

CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="$LD" -o /tmp/gost/gost ./cmd/gost
echo ">> linux amd64: /tmp/gost/gost"
