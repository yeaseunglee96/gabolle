"""글자 위치 찾기 모델을 NNCF 로 보정해 INT8 로 만든다 — 도커 빌드 안에서 한 번 돈다.

    python quantize/quantize.py /srv/models

보정(calibration) = 실제 입력 몇 개를 모델에 흘려 값의 범위를 재고, 그 범위에 맞춰 8비트로 줄이는 것.
보정 없이 줄이면(동적 INT8) 이 서버에서 오히려 느려지고 정확도가 무너졌다(이름 27/30 → 11/30).

🔴 보정용 메뉴판(quantize/calib)은 시험용 메뉴판과 **음식이 하나도 겹치지 않게** 따로 그렸다.
   시험지로 보정하면 시험 문제를 미리 보여 준 것이 되어 정확도가 부풀려진다.
🔴 한 줄 읽기 모델은 줄이지 않는다. 합성 메뉴판으로 보정한 INT8 은 실사진에서 가격 인식이
   20/22 → 8/22 로 무너졌다 — 보정 자료가 실사진의 글자 모양·조명을 대표하지 못했다.

만든 것마다 지문(sha256)을 manifest.json 에 남긴다. 「이 숫자는 무엇으로 만들었나」를 재현하려면
입력과 판을 알아야 한다.
"""
import glob
import hashlib
import importlib.metadata as md
import json
import os
import sys

import nncf
import numpy as np
import openvino as ov
from rapidocr import EngineType, LangDet, LangRec, ModelType, OCRVersion, RapidOCR
from rapidocr.inference_engine.openvino.main import OpenVINOInferSession

HERE = os.path.dirname(os.path.abspath(__file__))
MODELS = os.path.join(os.path.dirname(__import__("rapidocr").__file__), "models")
DET = os.path.join(MODELS, "ch_PP-OCRv5_det_mobile.onnx")
REC = os.path.join(MODELS, "korean_PP-OCRv5_rec_mobile.onnx")


def sha(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()[:16]


def main(out_dir):
    samples = []
    original = OpenVINOInferSession.__call__

    def capture(self, x):
        if x.shape[2] != 48:  # 한 줄 읽기 입력은 높이가 48 로 고정이다. 나머지가 위치 찾기 입력
            samples.append(np.array(x))
        return original(self, x)

    OpenVINOInferSession.__call__ = capture
    ocr = RapidOCR(params={
        "Global.use_cls": False, "Global.log_level": "warning",
        "Det.engine_type": EngineType.OPENVINO, "Det.lang_type": LangDet.CH,
        "Det.model_type": ModelType.MOBILE, "Det.ocr_version": OCRVersion.PPOCRV5,
        "Rec.engine_type": EngineType.OPENVINO, "Rec.lang_type": LangRec.KOREAN,
        "Rec.model_type": ModelType.MOBILE, "Rec.ocr_version": OCRVersion.PPOCRV5,
        "Cls.engine_type": EngineType.OPENVINO,
    })
    calib = sorted(glob.glob(os.path.join(HERE, "calib", "*.jpg")))
    for image in calib:
        ocr(image)
    OpenVINOInferSession.__call__ = original
    if len(samples) != len(calib):
        sys.exit(f"보정 입력을 {len(calib)}개 기대했는데 {len(samples)}개 모였다 — RapidOCR 판이 바뀌었나")

    os.makedirs(out_dir, exist_ok=True)
    model = ov.Core().read_model(DET)
    quantized = nncf.quantize(model, nncf.Dataset(samples), subset_size=len(samples),
                              preset=nncf.QuantizationPreset.MIXED)
    dst = os.path.join(out_dir, "det_int8.xml")
    ov.save_model(quantized, dst)

    manifest = {
        "det_int8": {"xml": sha(dst), "bin": sha(dst.replace(".xml", ".bin")),
                     "bytes": os.path.getsize(dst.replace(".xml", ".bin"))},
        "det_fp32_source": {"file": os.path.basename(DET), "sha256_16": sha(DET), "bytes": os.path.getsize(DET)},
        "rec_fp32": {"file": os.path.basename(REC), "sha256_16": sha(REC), "bytes": os.path.getsize(REC)},
        "calibration": [{"file": os.path.basename(p), "sha256_16": sha(p)} for p in calib],
        "versions": {p: md.version(p) for p in ("rapidocr", "openvino", "nncf")},
    }
    with open(os.path.join(out_dir, "manifest.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "/srv/models")
