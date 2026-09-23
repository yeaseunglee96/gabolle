# menu-ocr — 메뉴판 사진을 서버 안에서 읽는다

**S15P21E201-1538.** 메뉴판 읽기(`POST /api/v1/menu-scans`)가 외부 비전 API(GPT-4.1-mini) 대신
**운영 서버 CPU 에서 도는 경량 OCR 모델**로 사진을 읽는다. 백엔드가 배포될 때마다 같이 빌드·배포된다
(`backend/Jenkinsfile` 의 `Menu OCR` 단계).

| | |
|---|---|
| 하는 일 | 글자 위치 찾기 → 한 줄 읽기 → 닮은 한 글자 사전 보정 → 이름·가격 짝짓기 → 800선 사전 번역 |
| 모델 | PP-OCRv5 (Apache-2.0). 위치 찾기 `ch_PP-OCRv5_det_mobile`, 한 줄 읽기 `korean_PP-OCRv5_rec_mobile` |
| 실행기 | OpenVINO(**인텔 CPU 전용 추론 엔진**) 2026.4.0 |
| 바깥 호출 | 없다. 800선 사전에 없는 음식 이름의 **글자 번역**만 백엔드가 GMS 로 한다 |
| 실패하면 | 백엔드가 GMS 비전으로 대신 읽고 로그에 `MENU_SCAN_FALLBACK` 을 남긴다 |

## 왜 이 설정인가 — 운영 서버 실측 (2026-09-23)

AWS t3.xlarge(Xeon 8259CL, **실제 코어 2개**, GPU 없음), CPU 2개 고정, 5회 중앙값.
실사진은 식당 메뉴판 한 장(837×619 → 2배 확대)이고 정답은 사람이 확대해서 옮겨 적었다(음식 19·가격 22).

| 설정 | 한 장 | 실사진 이름 | 실사진 가격 |
|---|---|---|---|
| ONNX Runtime FP32 | 6.25초 (측정 중 부하가 섞였다) | 10/19 | 20/22 |
| OpenVINO FP32 | 3.08초 | 10/19 | 20/22 |
| OpenVINO 전부 INT8 (NNCF 보정) | 2.68초 | 8/19 | **8/22** ❌ |
| **OpenVINO 위치 찾기만 INT8** ← 이것 | **1.93초** | 10/19 | 19/22 |

- 🔴 **보정 없는 동적 INT8 은 쓰지 않는다.** 이 기종은 AWS 가 8비트 가속 명령(VNNI)을 숨겨, 오히려 느려지고(4.1초) 정확도가 무너졌다(합성판 이름 27/30 → 11/30)
- 🔴 **한 줄 읽기는 INT8 로 줄이지 않는다.** 합성 메뉴판으로 보정한 INT8 은 실사진에서 가격이 무너졌다
- 🔴 **방향 분류기(RapidOCR 기본값 `use_cls`)를 끈다.** 켜면 멀쩡한 한국어 줄을 뒤집어 합성판 이름이 27/30 → 14/30
- 사전 보정을 더하면 실사진 이름이 **14/19**, 글자 오류율 7.1% → 3.7%
- 짝짓기까지 거친 실사진 결과: **음식 19개 중 12개가 이름·가격 둘 다 정확**, 가격을 틀리게 붙인 것 0

🔴 **실사진이 한 장뿐이다.** 짝짓기·보정 규칙은 그 사진을 보며 다듬었으니, 다른 실사진으로 다시 재기 전까지
위 숫자를 일반적인 정확도로 말하지 않는다.

## 돌리기

```bash
# 모델 없이 도는 시험 (짝짓기·보정·사전)
python -m unittest discover -s tests -v

# 이미지 — 빌드 안에서 INT8 을 만들고 시험(실제 모델로 합성판 읽기 포함)을 돌린다
docker build -t gabolle-menu-ocr:local .
docker run --rm -p 18000:8000 --cpus=2 --memory=1500m gabolle-menu-ocr:local
curl -s --retry 60 --retry-delay 1 --retry-all-errors -f http://127.0.0.1:18000/health
curl -s -X POST --data-binary @메뉴판.jpg -H 'Content-Type: image/jpeg' 'http://127.0.0.1:18000/v1/read?lang=en'
```

## 폴더

| | |
|---|---|
| `app/pipeline.py` | 짝짓기·사전 보정·번역 사전 — 모델 없는 순수 규칙 |
| `app/ocr.py` · `app/server.py` | 모델 올리기·HTTP(표준 라이브러리만) |
| `quantize/quantize.py` · `quantize/calib/` | NNCF 보정 INT8. 보정 메뉴판은 시험 메뉴판과 **음식이 겹치지 않게** 따로 그렸다 |
| `data/hansik800.json` | 한식진흥원 「한식메뉴 외국어표기 길라잡이 800선」(공공데이터포털 15129784) — 영·일·중 간체·번체 |
| `data/dish-vocab.json` | 보정 사전: 800선 요리명 + 부산 음식점 2,356곳 조사의 간판 메뉴 |
| `tests/fixtures/` | 합성 메뉴판 사진·정답, 실사진을 읽은 **상자 좌표와 글자만**(사진은 싣지 않는다) |

## 손잡이

| | |
|---|---|
| 우리 모델 끄기 | Jenkins 에 `GABOLLE_MENU_SCAN_LOCAL_BASE_URL` 을 빈 값으로 → 백엔드가 전부 GMS 비전으로 읽는다 |
| 스레드 | `OCR_THREADS` (기본 2 — 실제 코어 수) |
| 작은 사진 확대 | `OCR_UPSCALE_BELOW` (기본 1200 — 긴 변이 이보다 짧으면 2배) |
