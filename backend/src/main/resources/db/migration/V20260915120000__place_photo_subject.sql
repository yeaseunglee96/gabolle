-- S15P21E201-1006 — 이 사진이 「무엇을 찍은 것인가」를 칸으로 남긴다.
--
-- 🔴 왜 photo_source 에 문구로 안 적나
--    photo_source 는 **출처 표기 문구**다(그 칸 주석: "저작권 표기 없이 남의 사진을 쓰지
--    않기 위해 URL 과 짝으로 둔다"). 출처(누가 준 사진인가)와 피사체(무엇을 찍은 사진인가)는
--    **다른 질문**이라, 한 칸에 담으면 둘 중 하나는 반드시 거짓이 된다.
--
--    그리고 자유 문장은 **코드가 못 읽는다.** "행사장 사진은 목록에서 빼자"·"배지를 달자"·
--    "축제를 실제로 찍은 사진이 있는 것을 위로 올리자" 중 무엇을 하려 해도 한국어 문장을
--    파싱해야 한다. 이 저장소가 evidenceStatus(VERIFIED·ESTIMATED·UNKNOWN·NOT_COLLECTED)를
--    값으로 갈라 둔 것과 같은 이유다 — 뜻이 갈리면 값을 가른다.
--
-- 🔴 왜 이 칸이 필요해졌나 (2026-09-16 실측)
--    관광사진갤러리에서 부산 축제 사진 35건을 붙였는데, **축제를 실제로 찍은 사진은 1건**
--    이고 나머지 34건은 **그 축제가 열리는 곳**을 찍은 사진이다(예: 부산브릿지마라톤 →
--    벡스코, 해운대 빛축제 → 해운대해수욕장). 구분 없이 띄우면 사용자는 "이 축제가 이렇게
--    생겼구나" 로 읽는다.
--
--    수집기가 붙인 matchedBy(name/landmark)로는 못 가른다 — 그건 "왜 붙었나" 이지 "무엇을
--    찍었나" 가 아니다. 실제로 matchedBy=name 셋 중 둘이 행사장 사진이었다. 판정은 **사진
--    자체의 제목**과 축제 제목이 같은가로 한다.
--
-- 🔴 값이 SELF·VENUE 인 이유 (EVENT 가 아니다)
--    이 칸은 place 에 산다. 식당·해수욕장도 같은 칸을 쓰므로 EVENT 는 뜻이 안 통한다.
--    SELF 는 "이 장소 자체를 찍은 사진" 이라 어디에나 맞는다.
--
--    NULL 은 **모른다**. 지금 있는 사진이 전부 여기다 — 그것을 SELF 로 채우지 않는다.
--    "안 알아본 것" 을 "확인했더니 맞더라" 로 뒤집는 것이 이 저장소가 알레르기 표시에서
--    겪은 바로 그 사고다.

ALTER TABLE place
    ADD COLUMN photo_subject VARCHAR(20);

ALTER TABLE place
    ADD CONSTRAINT ck_place_photo_subject
        CHECK (photo_subject IS NULL OR photo_subject IN ('SELF', 'VENUE'));

-- 🔴 사진이 없는데 피사체만 있는 행은 뜻이 없다. 반대(사진은 있는데 피사체를 모른다)는
--    정상이라 막지 않는다 — 지금 있는 사진이 전부 그 상태다.
ALTER TABLE place
    ADD CONSTRAINT ck_place_photo_subject_needs_photo
        CHECK (photo_subject IS NULL OR photo_url IS NOT NULL);

COMMENT ON COLUMN place.photo_subject IS
    '그 사진이 무엇을 찍은 것인가. SELF=이 장소 자체, VENUE=이 축제가 열리는 곳. NULL 은 모른다(안 알아본 것을 SELF 로 채우지 않는다).';
