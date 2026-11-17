#!/usr/bin/env python3
import itertools
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

COUNTER = itertools.count(1)


class LocalDemoHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Keep Gradle E2E logs clean
        pass

    def _send_json(self, status_code, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _route(self):
        parsed = urlsplit(self.path)
        query = {k: v[0] for k, v in parse_qs(parsed.query).items()}
        path = parsed.path
        if path.startswith("/stable"):
            return "stable", path[len("/stable"):] or "/", query
        if path.startswith("/target"):
            return "target", path[len("/target"):] or "/", query
        return "unknown", path, query

    def do_GET(self):
        env_name, api_path, query = self._route()
        seq = next(COUNTER)

        if api_path == "/actuator/health":
            return self._send_json(200, {"status": "UP", "env": env_name})

        if api_path == "/v1/catalog/categories":
            return self._send_json(200, {
                "status": "OK",
                "locale": query.get("locale", "ko_KR"),
                "category": query.get("category", "전자제품"),
                "categories": ["노트북", "스마트폰", "태블릿"],
            })

        if api_path == "/v1/catalog/items/SKU-KR-1001":
            return self._send_json(200, {
                "request_id": f"{env_name}-req-{seq}",
                "timestamp": f"2026-11-16T12:00:{seq % 60:02d}Z",
                "meta": {"traceId": f"trace-{env_name}-{seq}"},
                "data": {
                    "cacheAgeMs": seq * 10,
                    "sku": "SKU-KR-1001",
                    "title": "UltraBook Pro 16",
                    "price": 1890000,
                    "currency": "KRW",
                },
            })

        return self._send_json(404, {"error": f"Not found: {api_path}"})

    def do_POST(self):
        env_name, api_path, query = self._route()
        seq = next(COUNTER)
        length = int(self.headers.get("Content-Length", "0"))
        raw_body = self.rfile.read(length).decode("utf-8") if length > 0 else "{}"
        try:
            req_json = json.loads(raw_body)
        except json.JSONDecodeError:
            return self._send_json(400, {"error": "Invalid JSON body"})

        if api_path == "/v1/catalog/items/batch":
            skus = req_json.get("skus", [])
            items = [
                {"sku": sku, "currency": "KRW", "price": 1500000 + idx * 100000, "inStock": True}
                for idx, sku in enumerate(skus)
            ]
            return self._send_json(200, {"items": items, "region": req_json.get("region", "KR")})

        if api_path == "/v1/catalog/filter":
            return self._send_json(200, {
                "request_id": f"{env_name}-req-{seq}",
                "timestamp": f"2026-11-16T12:00:{seq % 60:02d}Z",
                "meta": {"traceId": f"trace-{env_name}-{seq}"},
                "category": req_json.get("category", "노트북"),
                "total": 2,
                "items": [
                    {"sku": "SKU-KR-1001", "price": 1890000},
                    {"sku": "SKU-KR-1002", "price": 1290000},
                ],
            })

        return self._send_json(404, {"error": f"Not found: {api_path}"})


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18080
    server = ThreadingHTTPServer(("127.0.0.1", port), LocalDemoHandler)
    print(f"Local mock server listening on http://127.0.0.1:{port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
