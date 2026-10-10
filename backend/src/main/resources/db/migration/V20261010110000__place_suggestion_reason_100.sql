-- S15P21E201-2031 — 추천 이유를 50자 이상 100자 이하로 받는다(사용자 지시 2026-10-10). 칸을 80 에서 100 으로 넓힌다.
alter table place_suggestion alter column reason type varchar(100);
