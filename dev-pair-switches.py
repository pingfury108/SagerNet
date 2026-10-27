#!/usr/bin/env python3
# Pair Switch nodes with title text nodes by vertical center; print tap coords.
import re, sys

xml = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
nodes = re.findall(r"<node[^>]*>", xml)

texts, switches = [], []
for n in nodes:
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    cy = (y1 + y2) // 2
    tm = re.search(r'text="([^"]+)"', n)
    if "android.widget.Switch" in n or "android.widget.CheckBox" in n:
        cm = re.search(r'checked="(\w+)"', n)
        switches.append((cy, (x1 + x2) // 2, cm.group(1) if cm else "?"))
    elif tm and tm.group(1).strip():
        texts.append((cy, tm.group(1), (x1 + x2) // 2))

for scy, sx, checked in switches:
    best = None
    for tcy, title, tx in texts:
        if abs(tcy - scy) <= 90:
            if best is None or abs(tcy - scy) < abs(best[0] - scy):
                best = (tcy, title)
    if best:
        print(f"{best[1][:34]:<36} checked={checked:<6} tap=({sx},{scy})")
