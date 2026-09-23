"""실제 모델로 합성 메뉴판 한 장을 끝까지 읽는다 — 도커 이미지 안에서만 돈다.

모델·런타임이 없는 곳(개발 PC)에서는 건너뛴다. 이미지가 만들어질 때 이 시험이 실패하면 빌드가 멈추고,
Jenkins 는 직전 판의 menu-ocr 을 그대로 둔다.
"""
import json
import os
import re
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "app"))

try:
    from ocr import Reader
except ImportError:  # rapidocr·openvino 가 없는 곳
    Reader = None


@unittest.skipIf(Reader is None, "모델 런타임이 없다 — 도커 이미지 안에서 돈다")
class SmokeTest(unittest.TestCase):

    def test_reads_the_synthetic_menu_end_to_end(self):
        reader = Reader()
        self.assertIn("INT8", reader.det_model, "위치 찾기 INT8 모델을 못 찾아 FP32 로 물러섰다")
        with open(os.path.join(HERE, "fixtures", "synthetic-photo.jpg"), "rb") as f:
            image = f.read()
        reader.read(image, "en")  # 예열
        result = reader.read(image, "en")
        with open(os.path.join(HERE, "fixtures", "synthetic-truth.json"), encoding="utf-8") as f:
            truth = json.load(f)["dishes"]
        food = {re.sub(r"\(.*?\)", "", "".join(l["name"].split())): l["price"].split(" / ")
                for l in result["lines"] if l["name"]}
        pairs = [n for n in truth if food.get(n) == truth[n]]
        print(f"smoke: 짝 {len(pairs)}/30, {result['timingsMs']}", flush=True)
        self.assertGreaterEqual(len(pairs), 28)


if __name__ == "__main__":
    unittest.main()
