-- 체류 작업이 운영 DB 에서 읽는 것 (S15P21E201-1692). 창구 뷰 하나만 읽는다 — 뷰가 이미 익명으로 줄여 내준다.
-- :window_from 은 실행기가 날짜 글자로 바꿔 넣는다(checks.json 의 window_days 만큼 거슬러 간 날).
SELECT category, source, stay_minutes
  FROM calibration_stay_source
 WHERE visit_week >= DATE ':window_from'
