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

# app/src/main/assets/v2ray is gitignored, so the bundled rules dat lives in
# rules/ and is copied in here (keeps the APK self-contained: install = working)
if [ -f rules/geosite.dat.xz ]; then
  mkdir -p app/src/main/assets/v2ray
  cp -f rules/geosite.dat.xz app/src/main/assets/v2ray/geosite.dat.xz
  cp -f rules/geosite.version.txt app/src/main/assets/v2ray/geosite.version.txt
  echo ">> bundled rules dat: $(cat rules/geosite.version.txt)"
fi

./gradlew "$@" app:assembleOssDebug
