#!/usr/bin/env python3
import socket
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: d4b-dns-proxy.py <upstream-ip>")

upstream = sys.argv[1]
listen = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
listen.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
listen.bind(("127.0.0.1", 53))

while True:
    packet, client = listen.recvfrom(4096)
    upstream_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    upstream_socket.settimeout(3.0)
    try:
        upstream_socket.sendto(packet, (upstream, 53))
        response, _ = upstream_socket.recvfrom(4096)
        listen.sendto(response, client)
    except Exception:
        # Validation bridge is best effort. Android transport surfaces failure.
        pass
    finally:
        upstream_socket.close()
