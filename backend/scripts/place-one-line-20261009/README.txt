S15P21E201-2029 장소 한 줄 소개 (2026-10-09)

범위: 추천 후보·일정에 한 번이라도 나온 CURATED 장소(숙소 제외) 2,695곳 → 넣은 것 2,180곳
  (관광공사 개요를 줄인 것 289 · 저장된 사실만으로 쓴 것 1,891). 「음식점」「○○구의 카페」처럼 뜻 없는 줄은 뺐다.
순서 (place-enrichment-20261009 의 places.tsv·facts.tsv 가 있는 폴더에서):
  1) DATA_GO_KR_KEY=… python fetch_overview.py      # 관광공사 국문 개요(detailCommon2)
  2) GMS_KEY_FILE=<GMS 키 파일> python gen_one_line.py   # SSAFY GMS(gpt-4.1-mini), 12곳씩, 중단해도 이어 받음
  3) python make_one_line_sql.py                      # 검사(한국어 45자 이하·외국어에 한글 없음) → place_one_line_20261009.sql
  4) 서버에서 psql 로 place_one_line_20261009.sql 실행 (빈 칸만 채운다)
되돌리기: UPDATE gabolle.place SET one_line = NULL; (이 칸은 이 적재만 쓴다)
