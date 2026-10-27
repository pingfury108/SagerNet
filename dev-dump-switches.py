#!/usr/bin/env python3
# Dump preference rows: title text + switch checked state, in screen order.
import re, sys

xml = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
nodes = re.findall(r"<node[^>]*>", xml)
last_texts = []
for n in nodes:
    tm = re.search(r'text="([^"]{1,40})"', n)
    if 'android.widget.Switch' in n:
        cm = re.search(r'checked="(\w+)"', n)
        print(f"SWITCH checked={cm.group(1) if cm else '?'}  <- {last_texts[-2:]}")
    elif tm and tm.group(1).strip():
        last_texts.append(tm.group(1))
