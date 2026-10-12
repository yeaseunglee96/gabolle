S15P21E201-2029 장소 한 줄 소개 사실 점검 (2026-10-12)

원본: gabolle-archive/place-enrichment-20261009 (changes-one-line.csv · facts.tsv · raw/overview.json). 스크립트는 그 폴더의 qa/ 에서 돈다.

순서
  0) 운영 DB 백업: gabolle.place_one_line_backup_20261012 (place_id, one_line) 표 + qa/one_line-backup-20261012.tsv (2,180줄)
  1) GMS_KEY_FILE=<키 파일> python check_one_line.py 500
       - 규칙 검사(2,180곳 전부): 외국어 줄의 한글, 한우·소고기→pork 같은 고기 종류 오역 → rule-flags.json
       - AI 판정(추천 횟수 상위 500곳, 언어 6개): 저장된 사실(분류·업종·메뉴·방문 이유·관광공사 개요·구)과 대조해
         언어마다 ok / fix(사실만으로 고친 문장) / drop(사실이 모자라 뺀다) → verdicts.json
         (앞 90곳은 Gemini 3.8 flash, 무료 한도가 끝나 나머지는 GMS gpt-4.1)
  2) python make_fix_sql.py → place_one_line_fix_20261012.sql · fix-list.csv(바뀐 줄마다 이유·전·후)
       - ko 를 고치면 다른 언어도 고친 것만 남기고, 안 고친 언어는 뺀다(뜻이 어긋나지 않게)
       - ko 를 빼면 그 장소는 NULL(화면이 아무것도 안 그린다)
       - 표현만 다듬은 판정(평가어 빼기·간결화)은 틀린 것이 아니라 버린다
       - 고친 문장이 형식 검사(ko 45자·외국어에 한글 없음)에 걸리면 그 언어를 뺀다
  3) 운영 DB 에 SQL 을 넣는다. 원래 값이 그대로인 행만 바뀐다(AND one_line = 원래 값).

되돌리기
  UPDATE gabolle.place p SET one_line = b.one_line FROM gabolle.place_one_line_backup_20261012 b WHERE p.place_id = b.place_id;
