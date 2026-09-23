# menu-ocr — 메뉴판 사진을 서버 안에서 읽는 경량 OCR (S15P21E201-1538)
#
# 세 단계다.
#   base      실행에 필요한 것만: rapidocr(PP-OCR 을 돌리는 도구) + openvino(인텔 CPU 추론기)
#   quantize  base + nncf(인텔 양자화 도구). 모델을 받고, 보정 메뉴판으로 위치 찾기 INT8 을 만든다
#   runtime   base 에 모델만 옮겨 싣는다 — nncf 와 그 딸림(scipy 등)은 실행 이미지에 안 들어간다
#
# 🔴 판을 박는다. RapidOCR 은 판마다 모델 기본값이 바뀐다(3.x 에서 PP-OCRv6 가 기본이 됐다).
#    올리려면 운영 서버에서 다시 재고 tests/ 의 기준 숫자를 확인한 뒤에 올린다.
FROM python:3.11-slim AS base
RUN apt-get update && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir rapidocr==3.9.2 openvino==2026.4.0
WORKDIR /srv

FROM base AS quantize
RUN pip install --no-cache-dir nncf==3.4.0
COPY quantize /srv/quantize
RUN python /srv/quantize/quantize.py /srv/models

FROM base AS runtime
COPY --from=quantize /usr/local/lib/python3.11/site-packages/rapidocr/models /usr/local/lib/python3.11/site-packages/rapidocr/models
COPY --from=quantize /srv/models /srv/models
COPY app /srv/app
COPY data /srv/data
COPY tests /srv/tests
# 시험이 실패하면 이미지가 안 만들어진다 → Jenkins 는 직전 판을 그대로 둔다
RUN cd /srv && python -m unittest discover -s tests -v
ENV OCR_THREADS=2 PORT=8000 PYTHONUNBUFFERED=1
EXPOSE 8000
CMD ["python", "/srv/app/server.py"]
