#!/usr/bin/env python3
# Analyze adblock filter lists: classify rules by feasibility of
# converting them into DNS/routing-layer (v2ray domain) block rules.
import re, sys, collections

stats = collections.Counter()
examples = {}
for f in sys.argv[1:]:
    try:
        fh = open(f, encoding="utf-8", errors="ignore")
    except OSError:
        continue
    for l in fh:
        l = l.strip()
        if not l or l.startswith("!"):
            continue
        if "##" in l or "#@#" in l:
            kind = "cosmetic/scriptlet (browser-only)" if ("+js(" in l or l.count("#") >= 2) else "cosmetic (DOM/CSS)"
        elif l.startswith("@@"):
            kind = "whitelist (@@)"
        elif l.startswith("||"):
            opts = l.split("$", 1)[1] if "$" in l else ""
            if not opts:
                kind = "network: ||domain^ plain block  [convertible]"
            elif re.search(r"\b(third-party|domain=|script|xhr|xmlhttprequest|image|subdocument|popup|ping|websocket|media)\b", opts):
                kind = "network: with 3rd-party/resource opts [partially convertible]"
            elif re.search(r"\b(badfilter|ghide|redirect|doc=)\b", opts):
                kind = "special (badfilter/redirect/ghide) [not convertible]"
            else:
                kind = "network: other opts"
        else:
            kind = "network: plain pattern [partial]"
        stats[kind] += 1
        if kind not in examples:
            examples[kind] = l[:70]

for k, v in stats.most_common():
    print(f"{v:6d}  {k}")
    print(f"          e.g. {examples[k]}")
