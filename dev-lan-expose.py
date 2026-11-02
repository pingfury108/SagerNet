#!/usr/bin/env python3
"""Expose a loopback-only TCP service on the LAN.

The local proxy (pid "yz") listens on 127.0.0.1:1080 only, so phones cannot
reach it at 192.168.32.177:1080. macOS allows binding 0.0.0.0:1080 next to
an existing 127.0.0.1:1080, so we take LAN connections on 1080 and splice
them to the loopback listener.

Usage: python3 lan-expose.py [listen_port] [target_port]
"""
import asyncio
import socket
import sys

LISTEN_PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 1080
TARGET_PORT = int(sys.argv[2]) if len(sys.argv) > 2 else 1080


async def splice(reader, writer, tag):
    try:
        while True:
            data = await reader.read(65536)
            if not data:
                break
            writer.write(data)
            await writer.drain()
    except (ConnectionError, asyncio.IncompleteReadError):
        pass
    finally:
        try:
            writer.close()
        except Exception:
            pass


async def handle(client_reader, client_writer):
    peer = client_writer.get_extra_info("peername")
    try:
        up_reader, up_writer = await asyncio.open_connection("127.0.0.1", TARGET_PORT)
    except OSError as e:
        print(f"connect failed for {peer}: {e}", flush=True)
        client_writer.close()
        return
    print(f"proxying {peer}", flush=True)
    await asyncio.gather(
        splice(client_reader, up_writer, "c->s"),
        splice(up_reader, client_writer, "s->c"),
    )


async def main():
    server = await asyncio.start_server(handle, "0.0.0.0", LISTEN_PORT,
                                        reuse_address=True)
    print(f"listening on 0.0.0.0:{LISTEN_PORT} -> 127.0.0.1:{TARGET_PORT}", flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
