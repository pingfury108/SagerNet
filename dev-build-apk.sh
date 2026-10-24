#!/bin/bash
# Assemble OSS debug APK on a remote dev box (pcw)
# Usage: bash dev-build-apk.sh [gradle args...]  (repo root)
set -e
cd "$(dirname "$0")"

export ANDROID_HOME="$HOME/Android/Sdk"
export JAVA_HOME="$HOME/.local/share/mise/installs/java/temurin-17"
export PATH="$JAVA_HOME/bin:$PATH"
export BUILD_PLUGIN=none

if [ ! -f local.properties ]; then
  cat > local.properties <<EOF
sdk.dir=$ANDROID_HOME
ndk.dir=$ANDROID_HOME/ndk/25.0.8775105
EOF
fi

./gradlew "$@" app:assembleOssDebug
