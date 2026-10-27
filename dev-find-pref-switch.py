#!/usr/bin/env python3
"""Find a preference row by title and return the switch tap coords.

Usage: python3 find-pref-switch.py ui.xml "覆盖目标地址"
Output: "x y checked" or "NOT_FOUND"
"""
import re
import sys

xml_path, title = sys.argv[1], sys.argv[2]
xml = open(xml_path, encoding="utf-8", errors="ignore").read()

m = re.search(r'text="' + re.escape(title) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if not m:
    print("NOT_FOUND")
    sys.exit(0)

cy = (int(m.group(2)) + int(m.group(4))) // 2

best = None
for s in re.finditer(r"<node[^>]*android\.widget\.(?:Switch|CheckBox)[^>]*>", xml):
    node = s.group(0)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if not b:
        continue
    scy = (int(b.group(2)) + int(b.group(4))) // 2
    if abs(scy - cy) <= 90:
        checked = re.search(r'checked="(\w+)"', node)
        cx = (int(b.group(1)) + int(b.group(3))) // 2
        if best is None or abs(scy - cy) < abs(best[0] - cy):
            best = (scy, cx, checked.group(1) if checked else "?")

if best:
    print(f"{best[1]} {best[0]} {best[2]}")
else:
    print("NO_SWITCH")
