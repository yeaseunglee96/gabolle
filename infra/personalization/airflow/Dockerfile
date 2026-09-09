FROM apache/airflow:3.0.3

# LightGBM 네이티브 라이브러리가 OpenMP 런타임(libgomp)을 필요로 하는데
# 베이스 이미지엔 없다 — 없으면 "libgomp.so.1: cannot open shared object file"
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*
USER airflow

COPY requirements.txt /requirements.txt
RUN pip install --no-cache-dir -r /requirements.txt
