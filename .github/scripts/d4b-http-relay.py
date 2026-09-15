#!/usr/bin/env python3
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import urllib.error
import urllib.request

TARGET = "https://ignmxvommfnkllcpliuw.supabase.co/functions/v1/linkpas-diagnostics"

class Relay(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0 or length > 32768:
            self.send_error(400)
            return
        body = self.rfile.read(length)
        headers = {
            "Content-Type": self.headers.get("Content-Type", "application/json"),
            "apikey": self.headers.get("apikey", ""),
            "Cache-Control": "no-store",
        }
        req = urllib.request.Request(TARGET, data=body, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=10) as res:
                payload = res.read(65536)
                self.send_response(res.status)
                self.send_header("Content-Type", res.headers.get("Content-Type", "application/json"))
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)
        except urllib.error.HTTPError as exc:
            payload = exc.read(65536)
            self.send_response(exc.code)
            self.send_header("Content-Type", exc.headers.get("Content-Type", "application/json"))
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
        except Exception:
            self.send_error(502)

    def log_message(self, fmt, *args):
        # Do not log request headers or diagnostic payloads.
        return

ThreadingHTTPServer(("127.0.0.1", 8765), Relay).serve_forever()
