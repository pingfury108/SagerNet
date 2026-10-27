#!/usr/bin/env python3
# Expand v2fly domain-list-community include tree, compare with anti-AD /
# EasyList China extracted domains, and report the incremental coverage.
import re, subprocess, sys, os

MIRROR = "https://ghfast.top/"
RAW = MIRROR + "https://raw.githubusercontent.com/v2fly/domain-list-community/master/data/"
CACHE = "/tmp/ads/dlc-cache"
os.makedirs(CACHE, exist_ok=True)


def fetch(name):
    fn = os.path.join(CACHE, name)
    if os.path.exists(fn):
        return open(fn, encoding="utf-8", errors="ignore").read()
    url = RAW + name
    r = subprocess.run(["curl", "-sSL", "-m", "40", "-o", fn, url], capture_output=True)
    if r.returncode != 0 or not os.path.exists(fn):
        return ""
    txt = open(fn, encoding="utf-8", errors="ignore").read()
    if txt.startswith("<") or "404" in txt[:20]:  # not found page
        return ""
    return txt


def expand(name, seen=None, out=None):
    seen = seen if seen is not None else set()
    out = out if out is not None else set()
    if name in seen:
        return out
    seen.add(name)
    txt = fetch(name)
    if not txt:
        print(f"  [warn] {name} not found", file=sys.stderr)
        return out
    for line in txt.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        if line.startswith("include:"):
            expand(line[8:].split("@")[0].strip(), seen, out)
        else:
            dom = line.split("@")[0].strip()
            if dom:
                out.add(dom)
    return out


print("expanding category-ads-all ...")
cat = expand("category-ads-all")
print(f"category-ads-all expanded domains: {len(cat)}")
kinds = collections = {}
import collections
kinds = collections.Counter("full:" if d.startswith("full:") else
                            "keyword:" if d.startswith("keyword:") else
                            "regexp:" if d.startswith("regexp:") else
                            "plain(domain)" for d in cat)
print("kinds:", dict(kinds))

# anti-AD domains
anti = set()
for line in open("/tmp/ads/antiad-domains.txt", encoding="utf-8", errors="ignore"):
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    anti.add(line.lower())
print(f"anti-AD domains: {len(anti)}")

# EasyList China ||domain^
elc = set()
for line in open("/tmp/ads/easylistchina.txt", encoding="utf-8", errors="ignore"):
    line = line.strip()
    if not line.startswith("||") or "$" in line:
        continue
    dom = line[2:].split("/")[0].split("^")[0].strip()
    if dom and "." in dom:
        elc.add(dom.lower())
print(f"EasyList China extractable domains: {len(elc)}")

# normalize category set: plain domains only (drop full:/keyword:/regexp: prefixes for compare)
cat_plain = set()
cat_special = set()
for d in cat:
    if d.startswith(("full:", "keyword:", "regexp:")):
        cat_special.add(d)
    else:
        cat_plain.add(d.lower())

new_from_anti = anti - cat_plain - elc
new_from_elc = elc - cat_plain - anti
union_new = anti | elc
increment = union_new - cat_plain
overlap = union_new & cat_plain
lost_if_replaced = cat_plain - union_new

print(f"\n=== comparison (plain domains) ===")
print(f"category-ads-all plain:        {len(cat_plain)}  (+special rules: {len(cat_special)})")
print(f"anti-AD ∪ ELC:                 {len(union_new)}")
print(f"overlap (already covered):     {len(overlap)}")
print(f"INCREMENT (new block targets): {len(increment)}")
print(f"category-only (lost if replaced): {len(lost_if_replaced)}")

sample = sorted(increment)[:15]
print("\nincrement sample:", sample)

# save increment for potential import
with open("/tmp/ads/increment-domains.txt", "w") as f:
    f.write("\n".join(sorted(increment)))
print("\nsaved: /tmp/ads/increment-domains.txt")
