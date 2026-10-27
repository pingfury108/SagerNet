#!/usr/bin/env python3
# Minimal protobuf parser for v2ray router.GeoSiteList:
#   GeoSiteList { repeated GeoSite entry = 1; }
#   GeoSite { string country_code = 1; repeated Domain domain = 2; }
import sys


def read_varint(buf, i):
    v = 0
    s = 0
    while True:
        b = buf[i]
        v |= (b & 0x7F) << s
        i += 1
        if not b & 0x80:
            return v, i
        s += 7


def fields(buf):
    i = 0
    while i < len(buf):
        tag, i = read_varint(buf, i)
        fno, wt = tag >> 3, tag & 7
        if wt == 0:
            v, i = read_varint(buf, i)
            yield fno, wt, v
        elif wt == 2:
            ln, i = read_varint(buf, i)
            yield fno, wt, buf[i:i + ln]
            i += ln
        elif wt == 5:
            yield fno, wt, buf[i:i + 4]; i += 4
        elif wt == 1:
            yield fno, wt, buf[i:i + 8]; i += 8
        else:
            raise ValueError(f"bad wire type {wt}")


data = open(sys.argv[1], "rb").read()
codes = []
domains_of = {}
for fno, wt, val in fields(data):
    if fno == 1 and wt == 2:  # GeoSite entry
        cc = ""
        n = 0
        for f2, w2, v2 in fields(val):
            if f2 == 1 and w2 == 2:
                cc = v2.decode()
            elif f2 == 2 and w2 == 2:
                n += 1
        codes.append((cc, n))

print(f"total categories: {len(codes)}")
for cc, n in codes:
    if cc in ("ads-pool", "cn", "category-ads-all", "category-ads", "google"):
        print(f"  {cc!r}: {n} domains")
print("sample:", [c for c, _ in codes[:10]])
print("has ads-pool:", any(c == "ads-pool" for c, _ in codes))
