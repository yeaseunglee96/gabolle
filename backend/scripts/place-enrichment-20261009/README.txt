S15P21E201-2030 장소 외국어 이름·영업시간 채우기 (2026-10-09)

순서 (이 폴더에서):
  1) places.tsv 를 운영 DB 에서 뽑는다 — place_id, name_ko, name_en, name_ja, name_zh_hans, name_zh_hant, lat, lng,
     category, source_type, source_id, 추천 후보 수, 일정 항목 수, 영업시간 행 수, address, 사진 유무 (탭 구분, CURATED·숙소 제외)
  2) DATA_GO_KR_KEY=<공공데이터포털 키> python fetch_tourapi.py   # 관광공사 다국어 목록 (raw/)
     python fetch_osm_ids.py                                        # OSM 공개 API 로 우리 OSM 장소의 태그
     python fetch_wikidata.py                                       # 위키데이터 부산 상자
  3) python match_tourapi.py && python build_changes.py           # changes-names.csv · changes-hours.csv · dropped-osm-en.txt
  4) python make_migration.py                                       # V20261009210000__place_names_hours_enrichment.sql

changes-names.csv  바뀌는 이름 줄 (출처·근거·추천/일정 등장 수 — 많이 나오는 장소부터)
changes-hours.csv  넣는 영업시간 (OSM opening_hours 원문과 읽은 값)
dropped-osm-en.txt 로마자 검사에서 떨어뜨린 OSM 영어 이름 (뜻풀이·다른 이름일 수 있어 넣지 않음)
