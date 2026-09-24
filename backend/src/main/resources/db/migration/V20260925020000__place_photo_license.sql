-- S15P21E201-1606 — 사진의 라이선스를 칸으로 남긴다.
--
-- 🔴 왜 photo_source 에 이어 적지 않나
--    위키미디어 커먼즈 사진(CC BY · CC BY-SA 등)은 작성자와 함께 **라이선스 이름과 그 링크**를
--    보여야 쓸 수 있다. photo_source 는 화면에 그대로 나가는 출처 문구(100자)라 링크를 걸 수 없고,
--    한 문장에 섞으면 화면이 어디가 링크인지 읽을 방법이 없다.
--
-- 🔴 NULL 은 「라이선스 문구가 따로 필요 없다」가 아니라 **이 칸으로 받은 것이 없다**다.
--    지금 있는 사진(관광공사 공공누리)은 전부 NULL 이고, 그쪽 이용 조건은 지금처럼
--    photo_source 문구가 진다.

ALTER TABLE place
    ADD COLUMN photo_license VARCHAR(100),
    ADD COLUMN photo_license_url VARCHAR(500),
    ADD COLUMN photo_file_page VARCHAR(500);

-- 사진이 없는데 라이선스만 있는 행은 뜻이 없다 (photo_subject 와 같은 규칙).
ALTER TABLE place
    ADD CONSTRAINT ck_place_photo_license_needs_photo
        CHECK (photo_license IS NULL OR photo_url IS NOT NULL);

COMMENT ON COLUMN place.photo_license IS
    '사진의 라이선스 이름(예: CC BY-SA 4.0, Public domain). 원천이 준 문구 그대로. NULL 은 이 칸으로 받은 것이 없다.';
COMMENT ON COLUMN place.photo_license_url IS
    '라이선스 본문 주소. Public domain 처럼 원천이 주소를 안 주면 NULL.';
COMMENT ON COLUMN place.photo_file_page IS
    '원본 파일 페이지(예: 커먼즈의 File: 페이지). 작성자·라이선스를 사람이 되짚는 곳.';
