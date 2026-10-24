#!/bin/bash
# Build gost (go-gost/gost) binaries:
#   - Android (4 ABIs) -> app/src/main/jniLibs/<abi>/libgost.so (bundled in APK)
#   - Linux amd64      -> /tmp/gost/gost (server side for dev)
# Requires: go 1.24+ (e.g. via mise), network access to go module proxy
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
# go via mise (dev box) or system PATH
export PATH="$HOME/.local/share/mise/shims:$HOME/.local/share/mise/installs/go/1.26.3/bin:$PATH"
command -v go >/dev/null || { echo "go not found (mise or PATH)" >&2; exit 1; }
GOST_DIR="${GOST_DIR:-/tmp/gost-src}"
GOST_REF="${GOST_REF:-v3.3.1}"

if [ ! -d "$GOST_DIR" ]; then
  git clone --depth 1 --branch "$GOST_REF" https://github.com/go-gost/gost.git "$GOST_DIR"
fi

export GOPROXY="${GOPROXY:-https://goproxy.cn,direct}"
LD="-s -w -buildid="
mkdir -p /tmp/gost

cd "$GOST_DIR"
git checkout -q "$GOST_REF" 2>/dev/null || true

build_android() {
  local abi=$1 goarch=$2 out="$ROOT/app/src/main/jniLibs/$1/libgost.so"
  local cgo=0 cc_extra=() goarm=""
  # 32-bit android targets require cgo + NDK clang
  case $goarch in
    arm)  cgo=1; goarm=7; cc_extra=(CC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi21-clang") ;;
    386)  cgo=1; cc_extra=(CC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/i686-linux-android21-clang") ;;
    amd64) cgo=1; cc_extra=(CC="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android21-clang") ;;
  esac
  mkdir -p "$(dirname "$out")"
  env CGO_ENABLED=$cgo GOOS=android GOARCH=$goarch ${goarm:+GOARM=$goarm} \
    "${cc_extra[@]}" go build -trimpath -ldflags="$LD" -o "$out" ./cmd/gost
  echo ">> android $abi: $out"
}

: "${ANDROID_NDK_HOME:=$HOME/Android/Sdk/ndk/25.0.8775105}"
build_android arm64-v8a arm64
build_android armeabi-v7a arm
build_android x86_64 amd64
build_android x86 386

CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags="$LD" -o /tmp/gost/gost ./cmd/gost
echo ">> linux amd64: /tmp/gost/gost"
