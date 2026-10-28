#!/bin/bash
# Reproduce the "accept: invalid argument" (EINVAL) scenario seen in
# libcore/nat: a TCP listener bound to a tun-like address, while the
# interface/address is torn down (VPN re-establish / network change).
#
# Runs inside a private network namespace so no privileges on the host
# are needed. Needs: unshare, python3, iproute2.
set -u

unshare -rn bash -c '
set -u
ip link set lo up
ip link add dummy0 type dummy
ip addr add 172.19.0.1/30 dev dummy0
ip link set dummy0 up
ip route add default dev dummy0 2>/dev/null || true
ip addr show dummy0 | sed -n "1,4p"

python3 - <<PY
import socket, subprocess, time, errno

def show(tag, fn):
    try:
        r = fn()
        print(f"{tag}: OK {r}")
    except OSError as e:
        print(f"{tag}: errno={errno.errorcode.get(e.errno)} {e}")

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
s.bind(("172.19.0.1", 0))
s.listen(128)
s.setblocking(False)
port = s.getsockname()[1]
print("listening on 172.19.0.1:%d" % port)

show("baseline accept (no conn)", lambda: s.accept())

# 1) address removed while the listener stays open
subprocess.run(["ip", "addr", "del", "172.19.0.1/30", "dev", "dummy0"], check=False)
time.sleep(0.2)
show("after addr del", lambda: s.accept())

# 2) interface down / deleted
subprocess.run(["ip", "link", "set", "dummy0", "down"], check=False)
time.sleep(0.2)
show("after link down", lambda: s.accept())

subprocess.run(["ip", "link", "del", "dummy0"], check=False)
time.sleep(0.2)
show("after link del", lambda: s.accept())

# 3) shutdown() on a listening socket
s2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s2.bind(("127.0.0.1", 0))
s2.listen(8)
s2.setblocking(False)
s2.shutdown(socket.SHUT_RD)
show("after shutdown(SHUT_RD) on listener", lambda: s2.accept())
PY
'
