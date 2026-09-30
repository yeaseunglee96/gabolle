# 가까운 도움 자료 (병원·의원·약국·경찰) — S15P21E201-1893

긴급 도움의 「가까운 병원·약국·경찰 지도」가 부르는 `GET /api/v1/help-places/nearby` 의 자료다.
서버는 `src/main/resources/help/busan-help-places.json` 을 기동할 때 한 번 읽어 메모리에 둔다(`HelpPlaceCatalog`).

**DB 표가 아니다.** 4,500곳 남짓이라 전부 재도 가볍고, 병원·약국은 여행 후보가 아니라 장소 표와 섞지 않는다.
자료를 바꾸려면 아래로 파일만 다시 만들어 커밋한다 — 마이그레이션이 없다.

## 출처와 이용 조건

| 갈래 | 출처 | 이용 조건 |
|---|---|---|
| 병원·의원·보건소, 약국 | 건강보험심사평가원 「전국 병의원 및 약국 현황」(보건의료빅데이터개방시스템 opendata.hira.or.kr, 공공데이터포털 15051059) | 공공누리 제1유형 — 출처표시 |
| 경찰 | © OpenStreetMap 기여자, `bigData/data/raw/pbf/poi.ndjson`(origin/main) | ODbL 1.0 |

응답의 `source` 한 줄을 앱이 화면 아래에 그대로 적는다. 앱의 데이터 출처 화면(legalContent 7번 절)에도 적혀 있다.

## 다시 만들기 (분기마다 — 심평원 갱신 주기)

1. opendata.hira.or.kr 에서 「전국 병의원 및 약국 현황 YYYY.M.zip」을 받는다(무료, 로그인 없음)
2. zip 을 풀고 아래 셋을 각각 xlsx 로 풀어(`xlsx` 는 zip 이다) JSON 으로 바꾼다
   - `1.병원정보서비스(…).xlsx` · `2.약국정보서비스(…).xlsx` · `4.의료기관별상세정보서비스_02_세부정보(…).xlsx`
   ```bash
   node xlsx2json.cjs <풀어 둔 폴더> hospital.json
   ```
3. 만든다
   ```bash
   node build-help-places.cjs hospital.json pharmacy.json detail.json poi.ndjson ../../src/main/resources/help/busan-help-places.json
   ```
4. `build-help-places.cjs` 안의 `basedOn`·`source` 의 기준일을 새 자료에 맞춘다
5. `HelpPlaceServiceTest` 를 돌린다

## 거르는 것

- 요양병원·정신병원, 치과, 한의원·한방병원, 조산원 — 종별로
- 피부·성형·미용·정신건강의학과 의원 — 이름으로(의원 종별 안에 섞여 있다)
- 응급실 표시는 상급종합·종합병원·병원만 믿는다 — 세부정보의 응급실 칸이 의원에도 「Y」인 곳이 있었다
- 진료시간은 세부정보에 있는 곳만(부산 병의원 약 30%). 공휴일은 자유 글이라 셀 수 없어 쓰지 않는다
