"""menu-ocr — 백엔드만 부르는 내부 HTTP 서버. 밖으로 포트를 열지 않는다.

  GET  /health                 모델을 올리고 한 번 읽어 본 뒤에만 200
  POST /v1/read?lang=en        몸통은 JPEG 바이트. 결과는 app/ocr.py 의 Reader.read 모양

라이브러리를 새로 들이지 않으려고 표준 http.server 를 쓴다. 한 번에 한 장만 읽는다 — 추론기는
동시에 부를 수 없고, 코어 2개에 두 장을 겹쳐 돌리면 둘 다 느려질 뿐이다. 기다리다 백엔드의 시간
제한(5초)에 걸리면 백엔드가 GMS 로 대신 읽는다.
"""
import json
import os
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

from ocr import Reader

MAX_BYTES = 8 * 1024 * 1024
FIXTURE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tests", "fixtures", "synthetic-photo.jpg")

reader = None
lock = threading.Lock()
ready = threading.Event()


def warm_up():
    """모델을 올리고 합성 메뉴판을 한 번 읽는다. 첫 추론의 준비 비용을 사용자에게 넘기지 않는다."""
    global reader
    try:
        reader = Reader()
        with open(FIXTURE, "rb") as f:
            reader.read(f.read(), "en")
    except Exception:  # 올리다 죽으면 /health 가 계속 503 이다 — 왜 죽었는지는 여기 남긴다
        import traceback
        traceback.print_exc()
        return
    ready.set()
    print(f"menu-ocr ready ({reader.det_model})", flush=True)


class Handler(BaseHTTPRequestHandler):
    def _send(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if urlparse(self.path).path != "/health":
            return self._send(404, {"error": "없는 주소"})
        if not ready.is_set():
            return self._send(503, {"status": "loading"})
        return self._send(200, {"status": "ok", "model": reader.det_model})

    def do_POST(self):
        url = urlparse(self.path)
        if url.path != "/v1/read":
            return self._send(404, {"error": "없는 주소"})
        if not ready.is_set():
            return self._send(503, {"error": "모델을 올리는 중"})
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > MAX_BYTES:
            return self._send(400, {"error": "사진 크기가 맞지 않는다"})
        image = self.rfile.read(length)
        language = (parse_qs(url.query).get("lang") or [None])[0]
        try:
            with lock:
                result = reader.read(image, language)
        except ValueError as e:
            return self._send(400, {"error": str(e)})
        return self._send(200, result)

    def log_message(self, fmt, *args):  # 요청마다 한 줄 — 사진 내용은 남기지 않는다
        print("menu-ocr", self.address_string(), fmt % args, flush=True)


def main():
    threading.Thread(target=warm_up, daemon=True).start()
    port = int(os.environ.get("PORT", "8000"))
    print(f"menu-ocr listening on :{port}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()


if __name__ == "__main__":
    main()
