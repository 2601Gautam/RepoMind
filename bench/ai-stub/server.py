import json
import os
import random
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


SEED = int(os.getenv("STUB_SEED", "20261113"))
RNG = random.Random(SEED)


def delay(name):
    time.sleep(int(os.getenv(name, "0")) / 1000)


def should_fail():
    return RNG.randrange(100) < int(os.getenv("FAILURE_PERCENT", "0"))


class Handler(BaseHTTPRequestHandler):
    def _json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length)
        if should_fail():
            self._json(500, {"error": {"message": "seeded stub failure"}})
            return
        if RNG.randrange(100) < int(os.getenv("RATE_LIMIT_PERCENT", "0")):
            self._json(429, {"error": {"message": "seeded rate limit"}})
            return
        if self.path.endswith("/embeddings"):
            delay("EMBEDDING_LATENCY_MS")
            request = json.loads(raw or b"{}")
            values = request.get("input", [])
            if isinstance(values, str):
                values = [values]
            data = [
                {"object": "embedding", "index": index, "embedding": [0.0] * 1024}
                for index, _ in enumerate(values)
            ]
            self._json(200, {"object": "list", "data": data, "model": "bench-stub"})
            return
        delay("CHAT_LATENCY_MS")
        self._json(
            200,
            {
                "id": "bench-stub",
                "choices": [{"index": 0, "message": {"role": "assistant", "content": "stub response"}}],
            },
        )

    def log_message(self, *_):
        return


ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
