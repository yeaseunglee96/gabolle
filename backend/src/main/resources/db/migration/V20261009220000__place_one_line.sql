-- S15P21E201-2029 — 장소 한 줄 소개. 코스·일정 카드에서 장소 이름 밑에 「어떤 곳인지」 한 줄을 보여 준다.
--
-- 모양: {"ko": "…", "en": "…", "ja": "…", "zh-Hans": "…", "zh-Hant": "…", "vi": "…"} — 없는 언어는 열쇠째 없다.
-- 값은 이관이 아니라 적재 스크립트(backend/scripts/place-one-line-20261009)가 채운다. 출처 순서:
--   1) 한국관광공사 개요(overview)를 한 줄로 줄인 것  2) 저장된 사실(분류·메뉴·소개 근거)만 주고 생성한 것.
-- 빈 칸은 NULL 이고 화면은 그때 아무것도 그리지 않는다.
ALTER TABLE place ADD COLUMN IF NOT EXISTS one_line JSONB;
