#!/bin/bash
# Build libcore.aar on a remote dev box (pcw): mise go 1.19 + NDK 25 + temurin-17
# Usage: bash dev-build-core.sh   (repo root)
set -e
cd "$(dirname "$0")"

export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/25.0.8775105"
export NDK="$ANDROID_NDK_HOME"
export JAVA_HOME="$HOME/.local/share/mise/installs/java/temurin-17"
export PATH="$JAVA_HOME/bin:$HOME/.local/share/mise/installs/go/1.19.13/bin:$PATH"
export GOPROXY="https://goproxy.cn,direct"

export PROJECT="$(realpath .)"
export TOOLCHAIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
export PATH="$PATH:$(go env GOPATH)/bin"

cd library/core

# init (rsynced tree: no git submodule/reset here)
go get -v -d
# pinned tools; gomobile init is skipped: it force-installs gobind@latest
# (v0.1.13 needs go >= 1.23, incompatible with go 1.19) and is not
# required for gomobile bind in module mode
go install github.com/sagernet/gomobile/cmd/gobind@v0.0.0-20221130124640-349ebaa752ca
go install github.com/sagernet/gomobile/cmd/gomobile@v0.0.0-20221130124640-349ebaa752ca

# build
export CGO_ENABLED=1
export GO386=softfloat

BUILD="../libcore_build"
rm -rf "$BUILD"/android "$BUILD"/java "$BUILD"/javac-output "$BUILD"/src*
gomobile bind -v -cache "$(realpath "$BUILD")" -androidapi 21 -trimpath \
  -tags='disable_debug' -ldflags='-s -w -buildid=' . || exit 1
rm -f libcore-sources.jar

mkdir -p "$PROJECT/app/libs"
cp -f libcore.aar "$PROJECT/app/libs"
echo ">> install $PROJECT/app/libs/libcore.aar"
