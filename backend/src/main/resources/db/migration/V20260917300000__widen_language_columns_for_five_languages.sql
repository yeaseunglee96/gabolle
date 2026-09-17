-- 프론트가 5개 언어(ko/en/ja/zh-Hans/zh-Hant)를 지원한다 — frontend/src/i18n/languages.ts.
-- LanguageNormalizer 가 "ZH-HANS"·"ZH-HANT" 처럼 두 글자를 넘는 값을 내게 되어 VARCHAR(2) 로는
-- 담을 수 없다 — 넓히지 않으면 INSERT/UPDATE 가 문자열 길이 위반으로 그 자리에서 실패한다.
ALTER TABLE app_user ALTER COLUMN language TYPE VARCHAR(10);
ALTER TABLE oauth_signup_ticket ALTER COLUMN language TYPE VARCHAR(10);
