#!/bin/bash
# Build the self-hosted rules dat release artifacts:
#   geosite.dat   official v2fly lists + ADS-POOL category (ad blocking)
#   dlc.dat.xz    xz-compressed geosite.dat (name expected by the app updater)
#   geoip.dat     latest official v2fly geoip (copied through)
#   version.txt   version tag, sha256sum files
#
# Usage: bash dev-ads-build.sh [out_dir]
#   env: WORK (default /tmp/ads), BRAVE (default /tmp/brave-lists)
#        MIRROR (default https://ghfast.top/ ; set empty for direct GitHub)
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-/tmp/ads/out}"
WORK="${WORK:-/tmp/ads}"
BRAVE="${BRAVE:-/tmp/brave-lists}"
DLC="${DLC:-/tmp/dlc}"
MIRROR="${MIRROR-https://ghfast.top/}"
export PATH="$HOME/.local/share/mise/shims:$HOME/.local/share/mise/installs/go/1.26.3/bin:$PATH"

mkdir -p "$OUT" "$WORK"
cd "$WORK"

echo ">> fetching upstream lists"
[ -s antiad-domains.txt ] || curl -fsSL -m 120 -o antiad-domains.txt \
  "${MIRROR}https://raw.githubusercontent.com/privacy-protection-tools/anti-AD/master/anti-ad-domains.txt"
[ -s easylistchina.txt ] || curl -fsSL -m 120 -o easylistchina.txt \
  "${MIRROR}https://raw.githubusercontent.com/easylist/easylistchina/master/easylistchina.txt"
if [ ! -d "$BRAVE/brave-lists" ]; then
  rm -rf "$BRAVE"
  git clone -q --depth 1 "${MIRROR}https://github.com/brave/adblock-lists.git" "$BRAVE"
fi
if [ ! -d "$DLC/data" ]; then
  rm -rf "$DLC"
  git clone -q --depth 1 "${MIRROR}https://github.com/v2fly/domain-list-community.git" "$DLC"
fi
if [ ! -d "$WORK/dlc-cache" ]; then
  mkdir -p "$WORK/dlc-cache"
  cp "$DLC"/data/category-ads-all "$WORK/dlc-cache/" 2>/dev/null || true
fi

echo ">> merging into a host-block pool"
python3 "$ROOT/dev-ads-merge.py" "$WORK" "$BRAVE"

echo ">> writing data/ads-pool"
python3 - "$WORK/final-pool.txt" "$DLC/data/ads-pool" <<'PY'
import re, sys
pool = open(sys.argv[1]).read().split()
lines = [d + " @ads" for d in pool if not re.search(r"[^a-z0-9.\-]", d)]
open(sys.argv[2], "w").write("\n".join(lines) + "\n")
print("   ads-pool entries:", len(lines))
PY

echo ">> compiling geosite.dat (official lists + ads-pool)"
( cd "$DLC" && go run . -outputdir "$OUT" >/dev/null )
cp "$OUT/dlc.dat" "$OUT/geosite.dat"
rm -f "$OUT/dlc.dat"

echo ">> fetching official geoip.dat"
GEOIP_TAG="${GEOIP_TAG:-$(curl -fsSI -m 60 "${MIRROR}https://github.com/v2fly/geoip/releases/latest" \
  | tr -d '\r' | awk 'tolower($1)=="location:"{print $2}' | xargs basename)}"
curl -fsSL -m 300 -o "$OUT/geoip.dat" \
  "${MIRROR}https://github.com/v2fly/geoip/releases/download/${GEOIP_TAG}/geoip.dat"

echo ">> packaging"
xz -9 -k -f "$OUT/geosite.dat" && mv "$OUT/geosite.dat.xz" "$OUT/dlc.dat.xz"
VERSION="${VERSION:-$(date -u +%Y%m%d%H%M)}"
echo "$VERSION" > "$OUT/version.txt"
( cd "$OUT" && sha256sum geosite.dat dlc.dat.xz geoip.dat > sha256sum.txt )
ls -la "$OUT"

echo ">> done: version $VERSION (geoip $GEOIP_TAG)"
