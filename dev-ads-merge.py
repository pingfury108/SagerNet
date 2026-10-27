#!/usr/bin/env python3
# Merge candidate ad-block domain sources and report pool size vs
# current category-ads-all coverage.
import re, sys, os, glob

pool = set()          # final candidate block domains
whitelist = set()     # @@ whitelist domains (excluded from pool)
cat = set()           # category-ads-all expanded plain domains

def norm(d):
    d = d.strip().lower()
    return d[:-1] if d.endswith(".") else d

# --- category-ads-all (expanded cache from dev-ads-diff.py) ---
cache = "/tmp/ads/dlc-cache"
files = {"category-ads-all"}
seen = set()
while files:
    name = files.pop()
    if name in seen:
        continue
    seen.add(name)
    p = os.path.join(cache, name)
    if not os.path.exists(p):
        continue
    for line in open(p, encoding="utf-8", errors="ignore"):
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        if line.startswith("include:"):
            files.add(line[8:].split("@")[0].strip())
        else:
            d = line.split("@")[0].strip()
            if d and not d.startswith(("full:", "keyword:", "regexp:")):
                cat.add(norm(d))

# --- source 1: anti-AD ---
anti = set()
for line in open("/tmp/ads/antiad-domains.txt", encoding="utf-8", errors="ignore"):
    line = line.strip()
    if line and not line.startswith("#"):
        anti.add(norm(line))
pool |= anti

def extract_adblock_domain(line):
    """Return a domain only for pure host-level block rules.

    Path-scoped rules (||baidu.com/adver?) and option-scoped rules
    (||x^$domain=y) cannot be expressed as a host block: extracting the
    bare host would over-block the whole site.
    """
    if not line.startswith("||"):
        return None
    body = line[2:]
    host = body
    if "$" in body:
        host, opts = body.split("$", 1)
        if re.search(r"\b(domain=|badfilter|ghide|redirect|doc=|1p)\b", opts):
            return None
        # resource-type scoped rules are still an approximation; skip them
        return None
    if "/" in host:
        return None
    host = host.rstrip("^").rstrip("|")
    if not host or "." not in host or ":" in host:
        return None
    return norm(host)


# --- source 2: EasyList China (||domain^ / plain ||) ---
elc = set()
for line in open("/tmp/ads/easylistchina.txt", encoding="utf-8", errors="ignore"):
    line = line.strip()
    if line.startswith("@@"):
        continue
    d = extract_adblock_domain(line)
    if d:
        elc.add(d)
pool |= elc

# --- source 3: brave/adblock-lists (convertible ||domain^; keep @@ as whitelist) ---
brave = set()
for f in glob.glob("/tmp/brave-lists/brave-lists/*.txt") + ["/tmp/brave-lists/brave-unbreak.txt"]:
    for line in open(f, encoding="utf-8", errors="ignore"):
        line = line.strip()
        if line.startswith("@@"):
            continue
        d = extract_adblock_domain(line)
        if d:
            brave.add(d)
pool |= brave

# --- whitelist exclusion ---
removed = pool & whitelist
pool -= whitelist

total = pool
overlap_cat = pool & cat
new_vs_cat = pool - cat

print(f"anti-AD:                 {len(anti)}")
print(f"EasyList China:          {len(elc)}")
print(f"Brave convertible:       {len(brave)}")
print(f"whitelist excluded:      {len(removed)}")
print(f"FINAL pool:              {len(total)}")
print(f"  already in category-ads-all: {len(overlap_cat)}")
print(f"  NEW vs category-ads-all:     {len(new_vs_cat)}")
print(f"category-ads-all plain domains: {len(cat)} (pool misses {len(cat - pool)})")

out = "/tmp/ads/final-pool.txt"
open(out, "w").write("\n".join(sorted(pool)))
print("saved:", out, f"({os.path.getsize(out)/1048576:.1f} MB)")
