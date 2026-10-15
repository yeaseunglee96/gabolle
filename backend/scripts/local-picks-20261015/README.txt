S15P21E201-2059 현지인 설문 추천 47곳을 운영 DB 에 단다 (2026-10-15)

무엇
  앱 「로컬 탐색」 카드의 「현지인 추천」 배지 + 「카페」 갈래 채우기. 부산 현지인 설문에서 추천받은 47곳(맛집 35 · 카페 12)은 모두 우리 DB 에 이미 있는
  장소라 장소를 새로 만들지 않고 표식(place_feature)만 단다. 같은 SQL 이 「맛집에 든 카페를 카페로 옮기기」도 한다.

파일
  survey-local-picks-20261015.ndjson   입력 47줄 (placeId · name · category FOOD|CAFE_HEALING · gu · dong · recommenders)
  make_sql.mjs                         입력 → SQL.  node make_sql.mjs   (SQL 은 손으로 고치지 않고 이 스크립트를 고친다)
  local_picks_20261015.sql             운영에서 돌릴 파일. 한 트랜잭션 · 멱등(두 번 돌려도 같다)

순서  (자세한 것은 backend/docs/PLACE-DATA-LOAD.md 13절)
  0) 배포가 먼저다 — V20261015120000__local_survey_and_cafe 마이그레이션이 사전에 새 낱말을 넣는다. 안 들어갔으면 SQL 이 아무것도 안 바꾸고 멈춘다.
  1) scp local_picks_20261015.sql $HOST:/home/ubuntu/load/
  2) ssh $HOST "docker exec -i local-route-personalization-postgres-1 psql -U app_user -d app_db -v ON_ERROR_STOP=1 < /home/ubuntu/load/local_picks_20261015.sql"
  3) 출력의 「확인」 표를 읽는다 — 「확인 1」(달 수 없는 곳)이 비어 있어야 47곳이 다 달린다.

시험
  backend/src/test/java/com/gabolle/backend/place/LocalPicksSqlIntegrationTest — 이 SQL 파일 그대로를 진짜 PostgreSQL 에서 두 번 돌려
  달린 표식 · 옮기기 · 멱등 · 표식 id 가 자바 UUID.nameUUIDFromBytes 와 같은 값인지를 본다.

되돌리기
  DELETE FROM gabolle.place_feature WHERE source_type = 'LOCAL_SURVEY' AND source_version = 'survey-local-picks-20261015';
  -- 카페로 옮긴 곳을 맛집으로 되돌리려면
  UPDATE gabolle.place_feature f SET feature_key = 'LOCAL_RESTAURANT' FROM gabolle.place p
   WHERE p.place_id = f.place_id AND p.category = 'CAFE_HEALING' AND f.feature_type = 'INTEREST_TAG'
     AND f.feature_key = 'LOCAL_CAFE' AND f.source_type = 'BUSAN_CERTIFIED_FOOD';
