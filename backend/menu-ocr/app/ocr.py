"""PP-OCRv5 한국어 모델을 OpenVINO 로 돌려 메뉴판 한 장을 읽는다.

설정 셋은 운영 서버(t3.xlarge, 실제 코어 2개)에서 재서 정했다 (S15P21E201-1538).

  글자 위치 찾기  NNCF 로 보정한 INT8 — 0.60초 → 0.34초, 정확도 같음
  한 줄 읽기      FP32 그대로 — INT8 로 줄이면 실사진에서 가격 인식이 20/22 → 8/22 로 무너졌다
  방향 분류기     끈다 — 켜면 멀쩡한 한국어 줄을 뒤집어 이름 정확 일치가 27/30 → 14/30 으로 떨어졌다
"""
import os
import time

import cv2
import numpy as np
from rapidocr import EngineType, LangDet, LangRec, ModelType, OCRVersion, RapidOCR

from pipeline import Corrector, Dictionary, build_lines, to_boxes

DET_INT8 = os.environ.get("OCR_DET_MODEL", "/srv/models/det_int8.xml")
THREADS = int(os.environ.get("OCR_THREADS", "2"))
# 긴 변이 이보다 짧은 사진은 2배로 키워 넣는다. 837x619 실사진에서 가격 인식이 17/22 → 20/22
UPSCALE_BELOW = int(os.environ.get("OCR_UPSCALE_BELOW", "1200"))


def engine_params(det_model_path=None):
    params = {
        "Global.use_cls": False,
        # 0.3 아래는 버리고, 0.3~0.5 는 돌려받아 «못 읽은 줄»로 센다 (pipeline.MIN_SCORE)
        "Global.text_score": 0.3,
        "Global.log_level": "warning",
        "Det.engine_type": EngineType.OPENVINO, "Det.lang_type": LangDet.CH,
        "Det.model_type": ModelType.MOBILE, "Det.ocr_version": OCRVersion.PPOCRV5,
        "Rec.engine_type": EngineType.OPENVINO, "Rec.lang_type": LangRec.KOREAN,
        "Rec.model_type": ModelType.MOBILE, "Rec.ocr_version": OCRVersion.PPOCRV5,
        "Cls.engine_type": EngineType.OPENVINO,
        "EngineConfig.openvino.inference_num_threads": THREADS,
    }
    if det_model_path:
        params["Det.model_path"] = det_model_path
    return params


def decode(image_bytes):
    img = cv2.imdecode(np.frombuffer(image_bytes, np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("사진을 읽을 수 없다")
    scale = 2 if max(img.shape[:2]) < UPSCALE_BELOW else 1
    if scale != 1:
        img = cv2.resize(img, None, fx=scale, fy=scale, interpolation=cv2.INTER_LANCZOS4)
    return img, scale


class Reader:
    def __init__(self, det_model_path=DET_INT8):
        path = det_model_path if det_model_path and os.path.exists(det_model_path) else None
        self.det_model = "PP-OCRv5 mobile det INT8(NNCF)" if path else "PP-OCRv5 mobile det FP32"
        self.engine = RapidOCR(params=engine_params(path))
        self.corrector = Corrector.load()
        self.dictionary = Dictionary.load()

    def read(self, image_bytes, language=None):
        t0 = time.perf_counter()
        img, scale = decode(image_bytes)
        t1 = time.perf_counter()
        out = self.engine(img)
        t2 = time.perf_counter()
        boxes = to_boxes(out.boxes if out.boxes is not None else [], out.txts or [], out.scores or [])
        lines, unread = build_lines(boxes, language, self.corrector, self.dictionary)
        t3 = time.perf_counter()
        return {
            "lines": lines,
            "unreadLineCount": unread,
            "upscale": scale,
            "timingsMs": {"decode": round((t1 - t0) * 1000), "ocr": round((t2 - t1) * 1000),
                          "group": round((t3 - t2) * 1000), "total": round((t3 - t0) * 1000)},
            "model": {"det": self.det_model, "rec": "korean PP-OCRv5 mobile rec FP32", "runtime": "OpenVINO"},
        }
