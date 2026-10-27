#!/usr/bin/env python3
# Reconstruct the v2ray config dumped by ProxyInstance logs and print
# routing rule order + inbound sniffing settings.
import json, sys, re

lines = [l.rstrip("\n") for l in sys.stdin]
raw = []
for l in lines:
    i = l.find("ProxyInstance:")
    if i > -1:
        raw.append(l[i + len("ProxyInstance:"):])

text = "\n".join(raw)
# the dump may contain multiple configs; take the last complete JSON object
start = text.find("{")
if start < 0:
    sys.exit("no json found")
dec = json.JSONDecoder()
best = None
idx = start
while idx < len(text):
    try:
        obj, end = dec.raw_decode(text[idx:])
        best = obj
        idx += end
        nxt = text.find("{", idx)
        if nxt < 0:
            break
        idx = nxt
    except json.JSONDecodeError:
        idx += 1
        if idx > start + 50_000_000:
            break

if not best:
    sys.exit("parse failed")

print("=== inbounds ===")
for ib in best.get("inbounds", []):
    sn = ib.get("sniffing", {})
    print(f"  tag={ib.get('tag'):<24} proto={ib.get('protocol'):<14} sniff={sn}")

print("\n=== routing ===")
r = best.get("routing", {})
print("domainStrategy:", r.get("domainStrategy"))
for i, rule in enumerate(r.get("rules", [])):
    dom = rule.get("domain")
    ip = rule.get("ip")
    desc = []
    if dom:
        desc.append(f"domain={dom if len(str(dom)) < 90 else str(dom)[:90] + '...'}")
    if ip:
        desc.append(f"ip={ip if len(str(ip)) < 60 else str(ip)[:60] + '...'}")
    for k in ("inboundTag", "port", "network", "protocol", "source"):
        if rule.get(k):
            desc.append(f"{k}={rule[k]}")
    print(f"  [{i}] -> {rule.get('outboundTag'):<16} {' '.join(desc)}")

print("\n=== outbounds ===")
for ob in best.get("outbounds", []):
    print(f"  tag={ob.get('tag')} proto={ob.get('protocol')}")
