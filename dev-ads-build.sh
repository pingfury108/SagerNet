#!/bin/bash
# Build an ad-block geosite.dat:  upstream lists -> merged domain pool ->
# domain-list-community data/ads-pool -> full geosite.dat with ADS-POOL category.
#
# Usage: bash dev-ads-build.sh [output_dir]
#   output_dir defaults to /tmp/ads/out; result is <output_dir>/geosite.dat
#
# Rules of the pipeline (see dev-ads-merge.py):
#   - anti-AD (curated for DNS/host blocking, best CN SDK coverage)
#   - EasyList China + brave/adblock-lists, host-level rules only
#     (path-scoped or $option-scoped rules are skipped: extracting the bare
#      host would over-block whole sites, e.g. ||baidu.com/adver?)
#   - @@ whitelist entries are excluded from the pool
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-/tmp/ads/out}"
WORK=/tmp/ads
DLC=/tmp/dlc
MIRROR="${MIRROR:-https://ghfast.top/}"
export PATH="$HOME/.local/share/mise/shims:$HOME/.local/share/mise/installs/go/1.26.3/bin:$PATH"

mkdir -p "$WORK" "$OUT"
cd "$WORK"

echo ">> fetch upstream lists"
[ -f antiad-domains.txt ] || curl -sSL -m 90 -o antiad-domains.txt \
  "${MIRROR}https://raw.githubusercontent.com/privacy-protection-tools/anti-AD/master/anti-ad-domains.txt"
[ -f easylistchina.txt ] || curl -sSL -m 90 -o easylistchina.txt \
  "${MIRROR}https://raw.githubusercontent.com/easylist/easylistchina/master/easylistchina.txt"
if [ ! -d /tmp/brave-lists ]; then
  git clone -q --depth 1 "${MIRROR}https://github.com/brave/adblock-lists.git" /tmp/brave-lists
fi
# v2fly domain lists: keep a cache for dev-ads-merge.py include expansion
if [ ! -d "$WORK/dlc-cache" ]; then
  mkdir -p "$WORK/dlc-cache"
  git clone -q --depth 1 "${MIRROR}https://github.com/v2fly/domain-list-community.git" "$DLC"
  cp "$DLC"/data/category-ads-all "$WORK/dlc-cache/" 2>/dev/null || true
fi

echo ">> merge lists -> domain pool"
python3 "$ROOT/dev-ads-merge.py"
POOL="$WORK/final-pool.txt"
wc -l < "$POOL" | xargs echo "   pool entries:"

echo ">> write DLC data/ads-pool"
[ -d "$DLC" ] || git clone -q --depth 1 "${MIRROR}https://github.com/v2fly/domain-list-community.git" "$DLC"
python3 - "$POOL" "$DLC/data/ads-pool" <<'PY'
import re, sys
pool = open(sys.argv[1]).read().split()
lines = [d + " @ads" for d in pool if not re.search(r"[^a-z0-9.\-]", d)]
open(sys.argv[2], "w").write("\n".join(lines) + "\n")
print("   ads-pool entries:", len(lines))
PY

echo ">> build geosite.dat"
cd "$DLC"
go run . -outputdir "$OUT" >/dev/null
cp "$OUT/dlc.dat" "$OUT/geosite.dat"
ls -la "$OUT/geosite.dat"
echo ">> done: $OUT/geosite.dat"
echo "   push: adb push $OUT/geosite.dat /storage/emulated/0/Android/data/<pkg>/files/geosite.dat"
