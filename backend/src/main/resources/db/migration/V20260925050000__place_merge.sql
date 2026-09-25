-- S15P21E201-1619 — 중복 장소 합치기. 지우지 않고 「합쳐짐」으로 표시한다(사용자 결정).
--
-- 🔴 왜
--    같은 가게가 두 줄 이상이었다 — 오픈스트리트맵 적재가 상가·관광공사에 이미 있던 장소를 또 넣었다. 운영 일정에
--    같은 곳이 두 번 들어갔다(송정해수욕장 0일차에 둘, 흰여울문화마을 1·6일차). 2026-09-25 운영 6,931곳을 읽어
--    이름(빈칸·부호·괄호 속을 뺀 것)이 같고 150m 안이며 체인이 아닌 짝을 「확실」로 골랐다 — 109묶음, 113줄이
--    합쳐진다. 애매한 107쌍은 사람이 본다(MR 에 붙인 CSV). 지점이 다른 것·같은 건물의 다른 가게는 안 합친다.
--    여기에 사람이 확인한 한 쌍을 더했다 — 송정해수욕장(관광공사 「바다」 + 오픈스트리트맵 「걷기 길」, 370m 라 150m
--    규칙에서 빠졌다). 사용자가 부하 측정 일정에서 직접 본 짝이다(2026-09-25). 합쳐서 110묶음, 114줄.
--
-- 🔴 왜 지우지 않나
--    · 되돌리기 쉽다 — 지운 것이 없어 place_unmerge 한 번이면 전부 돌아온다
--    · 다시 적재해도 되살아나지 않는다 — 오픈스트리트맵·상가 적재기는 같은 번호가 있으면 안 넣는다. 지우면 다시 생긴다
--    · 옛 일정이 안 깨진다 — 일정 판(itinerary_item·leg·excluded)은 고치지 않는 기록이라 합쳐진 줄을 그대로
--      가리키게 둔다(사용자 결정). 추천 후보·노출 기록과 이벤트 내용도 기록이라 안 건드린다
--
-- 합쳐진 줄은 curation_status = 'MERGED' 가 된다. 장소를 「찾아 주는」 조회(추천 후보·검색·근처·둘러보기·숙소
-- 목록)는 모두 CURATED 만 보므로(PlaceRepository) 저절로 빠지고, 번호로 여는 조회(상세·옛 일정)는 그대로 열린다.
--
-- 지금 쓰이는 참조는 남는 줄로 옮긴다. 같은 것이 남는 줄에 이미 있으면(같은 사람이 둘 다 저장 등) 옮기지 않고
-- 합쳐지는 쪽 행을 빼되 그 행 전체를 place_merge_log 에 남긴다. 표식은 남는 줄에 없는 종류만 옮긴다(있는 것은
-- 합쳐진 줄에 그대로 둔다 — 덮지 않는다). 사진·갈래·주소·영문 이름은 남는 줄이 비어 있을 때만 채운다.

-- ── 1. 표시할 칸 ───────────────────────────────────────────────────────────
ALTER TABLE place DROP CONSTRAINT ck_place_curation_status;
ALTER TABLE place
    ADD CONSTRAINT ck_place_curation_status
        CHECK (curation_status IN ('CURATED', 'USER_SUBMITTED', 'MERGED'));

ALTER TABLE place ADD COLUMN merged_into UUID;
ALTER TABLE place
    ADD CONSTRAINT fk_place_merged_into FOREIGN KEY (merged_into) REFERENCES place (place_id);
ALTER TABLE place
    ADD CONSTRAINT ck_place_merged_into_not_self CHECK (merged_into IS NULL OR merged_into <> place_id);
-- 합쳐짐 표시와 가리키는 곳은 늘 짝이다 — 하나만 있으면 어느 쪽을 믿어야 할지 모른다.
ALTER TABLE place
    ADD CONSTRAINT ck_place_merged_pair CHECK ((curation_status = 'MERGED') = (merged_into IS NOT NULL));

COMMENT ON COLUMN place.merged_into IS
    'S15P21E201-1619 — 이 줄이 합쳐진 남는 줄. 합쳐진 줄은 curation_status = MERGED 라 찾아 주는 조회에서 빠진다. 되돌리기는 place_unmerge(이 줄).';

-- ── 2. 기록표 ─────────────────────────────────────────────────────────────
CREATE TABLE place_merge_log (
    place_merge_log_id BIGSERIAL   PRIMARY KEY,
    merged_place_id    UUID        NOT NULL,
    kept_place_id      UUID        NOT NULL,
    table_name         VARCHAR(64) NOT NULL,
    -- REPOINT: 행을 남는 줄로 옮겼다 · DROP_DUPLICATE: 남는 줄에 같은 것이 있어 뺐다(row_data 로 되살린다)
    -- FILL: 남는 줄의 빈 칸을 채웠다 · MARK: 합쳐짐으로 표시했다(row_data 에 원래 상태)
    action             VARCHAR(20) NOT NULL,
    column_name        VARCHAR(64),
    row_data           JSONB       NOT NULL,
    -- 옮긴 뒤의 기본 키 — 되돌릴 때 이것으로 행을 찾는다(그 사이 다른 칸이 바뀌어도 찾는다)
    key_after          JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    undone_at          TIMESTAMPTZ,

    CONSTRAINT ck_place_merge_log_action CHECK (action IN ('REPOINT', 'DROP_DUPLICATE', 'FILL', 'MARK'))
);
CREATE INDEX ix_place_merge_log_merged ON place_merge_log (merged_place_id);

COMMENT ON TABLE place_merge_log IS
    'S15P21E201-1619 — 장소 합치기가 바꾼 모든 것. place_unmerge 가 이것을 거꾸로 따라가 되돌린다.';

-- ── 3. 한 표의 참조를 옮긴다 ──────────────────────────────────────────────
-- p_same: 남는 줄에 「같은 것」이 있는지 볼 칸(유일성 제약에서 장소 칸을 뺀 나머지). 비어 있으면 늘 옮긴다.
-- p_drop_same: 같은 것이 있을 때 합쳐지는 쪽 행을 뺄지(참) 그대로 둘지(거짓 — 표식처럼 합쳐진 줄에 남겨도 되는 것).
CREATE FUNCTION place_merge_move(p_dup UUID, p_keep UUID, p_table TEXT, p_col TEXT, p_same TEXT[], p_drop_same BOOLEAN)
    RETURNS INTEGER
    LANGUAGE plpgsql
AS $$
DECLARE
    rec      JSONB;
    same_sql TEXT;
    pk_cols  TEXT[];
    key_after JSONB;
    has_same BOOLEAN;
    moved    INTEGER := 0;
    c        TEXT;
BEGIN
    SELECT array_agg(a.attname::TEXT ORDER BY a.attnum)
      INTO pk_cols
      FROM pg_index i
      JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
     WHERE i.indrelid = p_table::regclass AND i.indisprimary;

    -- 🔴 빈 칸(NULL)도 같은 값으로 본다. 키 없는 표식(경사·조용함 — feature_key 가 NULL)은 유일성이 (장소, 종류)라,
    --    NULL 을 다르다고 보면 옮기다 uq_place_feature_unkeyed 에 걸린다. 운영 사본에서 실제로 걸렸다(2026-09-25).
    --    to_jsonb(NULL) 은 SQL NULL 이고 ($2 -> 칸) 은 JSON null 이라 그냥 견주면 참이 안 된다 — JSON null 로 맞춘다.
    same_sql := 'TRUE';
    IF p_same IS NOT NULL THEN
        FOREACH c IN ARRAY p_same LOOP
            same_sql := same_sql || format(' AND COALESCE(to_jsonb(t.%I), ''null''::jsonb) = ($2 -> %L)', c, c);
        END LOOP;
    END IF;

    FOR rec IN EXECUTE format('SELECT to_jsonb(t) FROM %I t WHERE t.%I = $1', p_table, p_col) USING p_dup LOOP
        has_same := FALSE;
        IF p_same IS NOT NULL AND cardinality(p_same) > 0 THEN
            EXECUTE format('SELECT EXISTS (SELECT 1 FROM %I t WHERE t.%I = $1 AND %s)', p_table, p_col, same_sql)
               INTO has_same USING p_keep, rec;
        END IF;

        IF has_same THEN
            IF p_drop_same THEN
                INSERT INTO place_merge_log (merged_place_id, kept_place_id, table_name, action, column_name, row_data)
                VALUES (p_dup, p_keep, p_table, 'DROP_DUPLICATE', p_col, rec);
                EXECUTE format('DELETE FROM %I t WHERE to_jsonb(t) = $1', p_table) USING rec;
            END IF;
        ELSE
            key_after := '{}'::JSONB;
            FOREACH c IN ARRAY pk_cols LOOP
                key_after := key_after || jsonb_build_object(c, CASE WHEN c = p_col THEN to_jsonb(p_keep) ELSE rec -> c END);
            END LOOP;
            INSERT INTO place_merge_log (merged_place_id, kept_place_id, table_name, action, column_name, row_data, key_after)
            VALUES (p_dup, p_keep, p_table, 'REPOINT', p_col, rec, key_after);
            EXECUTE format('UPDATE %I t SET %I = $1 WHERE to_jsonb(t) = $2', p_table, p_col) USING p_keep, rec;
        END IF;
        moved := moved + 1;
    END LOOP;
    RETURN moved;
END;
$$;

-- ── 4. 한 줄을 다른 줄로 합친다 ────────────────────────────────────────────
CREATE FUNCTION place_merge(p_dup UUID, p_keep UUID)
    RETURNS VOID
    LANGUAGE plpgsql
AS $$
DECLARE
    dup  place%ROWTYPE;
    keep place%ROWTYPE;
BEGIN
    IF p_dup = p_keep THEN
        RAISE EXCEPTION '같은 줄로는 합치지 않는다: %', p_dup;
    END IF;
    SELECT * INTO dup FROM place WHERE place_id = p_dup;
    SELECT * INTO keep FROM place WHERE place_id = p_keep;
    -- 없는 줄이면 아무것도 안 한다 — 이 목록은 운영 번호라 다른 DB 에서는 0건이어야 한다.
    IF dup.place_id IS NULL OR keep.place_id IS NULL THEN
        RETURN;
    END IF;
    IF dup.merged_into IS NOT NULL THEN
        IF dup.merged_into = p_keep THEN
            RETURN;   -- 이미 합쳤다
        END IF;
        RAISE EXCEPTION '이미 다른 줄(%)로 합쳐진 줄이다: %', dup.merged_into, p_dup;
    END IF;
    IF keep.merged_into IS NOT NULL THEN
        RAISE EXCEPTION '남는 줄이 이미 합쳐진 줄이다 — 그 줄(%)로 합쳐야 한다: %', keep.merged_into, p_keep;
    END IF;

    -- 지금 쓰이는 참조 — 남는 줄로 옮긴다
    PERFORM place_merge_move(p_dup, p_keep, 'trip', 'accommodation_place_id', NULL, FALSE);
    PERFORM place_merge_move(p_dup, p_keep, 'story', 'place_id', NULL, FALSE);
    PERFORM place_merge_move(p_dup, p_keep, 'place_visit_verification', 'place_id', NULL, FALSE);
    PERFORM place_merge_move(p_dup, p_keep, 'trip_seed_place', 'place_id', ARRAY['trip_id'], TRUE);
    PERFORM place_merge_move(p_dup, p_keep, 'saved_place', 'place_id', ARRAY['user_id'], TRUE);
    PERFORM place_merge_move(p_dup, p_keep, 'collection_item', 'place_id', ARRAY['collection_id'], TRUE);
    PERFORM place_merge_move(p_dup, p_keep, 'user_place_taste_state', 'place_id', ARRAY['user_id'], TRUE);
    PERFORM place_merge_move(p_dup, p_keep, 'recommendation_place_action', 'place_id', ARRAY['trip_id'], TRUE);
    PERFORM place_merge_move(p_dup, p_keep, 'editorial_pick_place', 'place_id', ARRAY['pick_id'], TRUE);
    PERFORM place_merge_move(p_dup, p_keep, 'place_review', 'place_id', ARRAY['user_id'], TRUE);
    PERFORM place_merge_move(p_dup, p_keep, 'place_event_period', 'place_id', ARRAY['start_date', 'end_date'], TRUE);
    -- 표식은 남는 줄에 없는 종류만 옮긴다. 있는 것은 합쳐진 줄에 그대로 둔다 — 남는 줄의 값을 덮지 않는다.
    PERFORM place_merge_move(p_dup, p_keep, 'place_feature', 'place_id', ARRAY['feature_type', 'feature_key'], FALSE);

    -- 남는 줄의 빈 칸 채우기 — 사진은 한 묶음으로(사진 없이 출처만 있는 줄을 만들지 않는다)
    IF keep.photo_url IS NULL AND dup.photo_url IS NOT NULL THEN
        INSERT INTO place_merge_log (merged_place_id, kept_place_id, table_name, action, column_name, row_data)
        VALUES (p_dup, p_keep, 'place', 'FILL', 'photo',
                jsonb_build_object('photo_url', dup.photo_url, 'photo_source', dup.photo_source,
                                   'photo_subject', dup.photo_subject, 'photo_license', dup.photo_license,
                                   'photo_license_url', dup.photo_license_url, 'photo_file_page', dup.photo_file_page));
        UPDATE place
           SET photo_url = dup.photo_url, photo_source = dup.photo_source, photo_subject = dup.photo_subject,
               photo_license = dup.photo_license, photo_license_url = dup.photo_license_url,
               photo_file_page = dup.photo_file_page
         WHERE place_id = p_keep;
    END IF;
    IF (keep.category IS NULL OR btrim(keep.category) = '') AND dup.category IS NOT NULL AND btrim(dup.category) <> '' THEN
        INSERT INTO place_merge_log (merged_place_id, kept_place_id, table_name, action, column_name, row_data)
        VALUES (p_dup, p_keep, 'place', 'FILL', 'category', jsonb_build_object('category', dup.category));
        UPDATE place SET category = dup.category WHERE place_id = p_keep;
    END IF;
    IF keep.name_en IS NULL AND dup.name_en IS NOT NULL THEN
        INSERT INTO place_merge_log (merged_place_id, kept_place_id, table_name, action, column_name, row_data)
        VALUES (p_dup, p_keep, 'place', 'FILL', 'name_en', jsonb_build_object('name_en', dup.name_en));
        UPDATE place SET name_en = dup.name_en WHERE place_id = p_keep;
    END IF;
    IF keep.address IS NULL AND dup.address IS NOT NULL THEN
        INSERT INTO place_merge_log (merged_place_id, kept_place_id, table_name, action, column_name, row_data)
        VALUES (p_dup, p_keep, 'place', 'FILL', 'address', jsonb_build_object('address', dup.address));
        UPDATE place SET address = dup.address WHERE place_id = p_keep;
    END IF;
    IF keep.address_en IS NULL AND dup.address_en IS NOT NULL THEN
        INSERT INTO place_merge_log (merged_place_id, kept_place_id, table_name, action, column_name, row_data)
        VALUES (p_dup, p_keep, 'place', 'FILL', 'address_en', jsonb_build_object('address_en', dup.address_en));
        UPDATE place SET address_en = dup.address_en WHERE place_id = p_keep;
    END IF;

    INSERT INTO place_merge_log (merged_place_id, kept_place_id, table_name, action, row_data)
    VALUES (p_dup, p_keep, 'place', 'MARK', jsonb_build_object('curation_status', dup.curation_status));
    UPDATE place SET curation_status = 'MERGED', merged_into = p_keep WHERE place_id = p_dup;
END;
$$;

-- ── 5. 되돌린다 — 기록표를 거꾸로 따라간다 ─────────────────────────────────
CREATE FUNCTION place_unmerge(p_dup UUID)
    RETURNS VOID
    LANGUAGE plpgsql
AS $$
DECLARE
    entry RECORD;
    col   TEXT;
BEGIN
    FOR entry IN
        SELECT * FROM place_merge_log
         WHERE merged_place_id = p_dup AND undone_at IS NULL
         ORDER BY place_merge_log_id DESC
    LOOP
        IF entry.action = 'MARK' THEN
            UPDATE place SET curation_status = entry.row_data ->> 'curation_status', merged_into = NULL
             WHERE place_id = p_dup;
        ELSIF entry.action = 'FILL' THEN
            -- 채우기 전에는 비어 있었다(비어 있을 때만 채웠다). 한 번에 비운다 — 사진 칸을 하나씩 비우면
            -- 「사진 없이 사진 분류만 있는 줄」(ck_place_photo_subject_needs_photo)이 잠깐 생겨 막힌다.
            SELECT string_agg(format('%I = NULL', k), ', ') INTO col FROM jsonb_object_keys(entry.row_data) AS k;
            EXECUTE format('UPDATE place SET %s WHERE place_id = $1', col) USING entry.kept_place_id;
        ELSIF entry.action = 'REPOINT' THEN
            EXECUTE format('UPDATE %I t SET %I = $1 WHERE to_jsonb(t) @> $2', entry.table_name, entry.column_name)
              USING p_dup, entry.key_after;
        ELSIF entry.action = 'DROP_DUPLICATE' THEN
            EXECUTE format('INSERT INTO %I SELECT * FROM jsonb_populate_record(NULL::%I, $1)',
                           entry.table_name, entry.table_name)
              USING entry.row_data;
        END IF;
        UPDATE place_merge_log SET undone_at = now() WHERE place_merge_log_id = entry.place_merge_log_id;
    END LOOP;
END;
$$;

COMMENT ON FUNCTION place_merge(UUID, UUID) IS
    'S15P21E201-1619 — 첫째 줄을 둘째 줄로 합친다(지우지 않고 MERGED 로 표시). 바꾼 것은 모두 place_merge_log 에 남는다.';
COMMENT ON FUNCTION place_unmerge(UUID) IS
    'S15P21E201-1619 — place_merge 를 되돌린다. 기록표를 거꾸로 따라가며 옮긴 행·채운 칸·표시를 원래대로.';

-- ── 6. 2026-09-25 운영 「확실」 목록 — (합쳐질 줄, 남는 줄) ───────────────────────
-- 남는 줄: 관광공사 > 카카오 큐레이션 > 상가 > 오픈스트리트맵, 같으면 사진·표식이 많은 쪽.
-- 없는 번호는 place_merge 가 건너뛴다(다른 DB 에서는 0건).
SELECT place_merge(pair.dup, pair.keep)
  FROM (VALUES
    (UUID '089a8a66-04ea-39c4-8de7-834d661359be', UUID '40fec0fc-3e79-3d0d-8690-7f3f186316b6'),  -- 송정3대국밥 (OSM → SBIZ)
    (UUID '1a8d29e4-9fda-31bb-a8eb-2e381aba1081', UUID '4571a21b-e835-33f6-a895-d286af0b0040'),  -- 바오하우스 (OSM → SBIZ)
    (UUID '3a139f66-08df-36d7-89c2-0b535d078691', UUID 'b82021a8-a038-3f06-812c-cc02740003f4'),  -- 초원복국 (OSM → SBIZ)
    (UUID 'a6833f86-d6e3-3445-8d40-c9729ca36c51', UUID 'd406654a-47cd-3c20-9afb-6cb5e73ba9fa'),  -- 본전돼지국밥 (OSM → SBIZ)
    (UUID '8d80ce14-bb79-312a-9391-beaf697de6e1', UUID 'fe2f8382-a076-3173-8605-777a4c500702'),  -- 수변최고돼지국밥 (OSM → SBIZ)
    (UUID '328804fb-136d-3bb0-b845-630a07ab0a2e', UUID 'ce2e4e67-4f0e-30ed-97c9-6ff7a242f04c'),  -- 엄용백 돼지국밥 (OSM → SBIZ)
    (UUID '36512b16-571c-3635-855e-eb51ca08719d', UUID '67bef0e2-7384-3257-941a-e567ac57d903'),  -- 의령식당 (OSM → SBIZ)
    (UUID '9e3ebd8d-70fb-3944-ba3e-ee0145d08f6c', UUID '19ec72a8-ca5a-39c2-ade5-692fa46ff35f'),  -- 할매재첩국 (OSM → SBIZ)
    (UUID 'b05a50b0-510d-31e0-8dbe-0830168e0d91', UUID 'c7354006-1458-36e4-aa65-debd57a04580'),  -- 서면밀면 (OSM → SBIZ)
    (UUID '9f28e53f-e924-3c90-ad82-e4081328c0e2', UUID '44bf8a37-5cd0-3240-a936-76d598eee84f'),  -- 초량밀면 (OSM → SBIZ)
    (UUID 'd57cf355-b677-3d0f-b8e9-73032b55e4bd', UUID '066bc2c5-a18a-38f7-abb4-b814fae36277'),  -- 동래할매파전 (OSM → SBIZ)
    (UUID '6023cf71-b29d-3ff1-b617-bd7caa77fc0e', UUID 'd8ceb699-fdf0-3895-abfb-22ae80a43b81'),  -- 내호냉면 (OSM → SBIZ)
    (UUID '703f582b-6ee0-332e-b6eb-346ea32960c9', UUID '547f9bd1-17d8-3c28-acbc-19e3844124a9'),  -- 동보성 (OSM → SBIZ)
    (UUID 'b6f62dfe-38a2-3d94-b2c8-1c55090a8b43', UUID '8a3fb389-92fe-3a85-9387-4c4f7a67365e'),  -- 까치횟집 (OSM → SBIZ)
    (UUID 'db87c627-67e8-3a0f-8b7b-8798e3a66376', UUID 'd2bb0681-ec54-3048-a188-2d0bb7645af5'),  -- 삼삼횟집 (OSM → SBIZ)
    (UUID 'fbfd400f-d40a-382d-892b-60811b453303', UUID 'd2bb0681-ec54-3048-a188-2d0bb7645af5'),  -- 삼삼횟집 (OSM → SBIZ)
    (UUID '96dc823e-bc2b-38a3-9b71-1751795612dd', UUID '0b6ae117-9966-3e1b-9b73-5d2d9435610c'),  -- 개미기사식당 (OSM → SBIZ)
    (UUID '24893066-a2d0-3e39-bd0f-70174a6425e8', UUID '2a38b719-bb45-3b1c-aed7-fd9417b8ce91'),  -- 달봉이횟집 (OSM → SBIZ)
    (UUID '0b635e47-6406-339e-8b45-af5173774bca', UUID '5bc92078-deff-3291-bb2f-f273f3bbb2c1'),  -- 동백섬횟집 (OSM → SBIZ)
    (UUID '3bef8a54-d950-33dd-85b5-7200ef54ceba', UUID 'bc53ef10-23c6-3b42-92c9-9b798c24ba47'),  -- 경북횟집 (OSM → SBIZ)
    (UUID 'd0d29f0f-d55d-3cf9-9c9a-d566b1c48602', UUID 'bc53ef10-23c6-3b42-92c9-9b798c24ba47'),  -- 경북횟집 (OSM → SBIZ)
    (UUID 'e8f88e4f-29a0-31a6-82aa-cde58e2d4751', UUID 'e442b6fc-c1ac-34f3-972e-e6f49c3b133d'),  -- 가마솥생복집 (OSM → SBIZ)
    (UUID '671ceaa4-7745-3a98-ade4-16c90bce6efa', UUID '15786fbd-f2b3-31ef-b21a-1aac76b700a4'),  -- 시골한방돼지국밥 (OSM → SBIZ)
    (UUID 'f1f84d3c-9730-36da-869c-34aefa8db53d', UUID '92687fa5-f20f-3c5b-9b29-5297e0878b19'),  -- 거북선횟집 (OSM → SBIZ)
    (UUID '2c62e48c-810a-341b-931a-6e60fafeafba', UUID '1522f965-a786-33ab-9a5f-4b4dad30c28b'),  -- 고스락 (OSM → SBIZ)
    (UUID 'e29eaf91-9ed2-3978-9cd1-70295d1fbddb', UUID '9ef2eb76-6a3b-35e6-a275-c54cf700025b'),  -- 칠성횟집 (OSM → SBIZ)
    (UUID 'a23077a8-9766-33d0-95ae-1f60e491224b', UUID 'bed61ce2-e5ab-3dc2-aab6-fdc751459403'),  -- 초원복국 (OSM → SBIZ)
    (UUID '24ffee02-9f93-31f3-a2b9-1f568c62fe95', UUID '603ff067-e69e-371d-b4ef-9e083fc77421'),  -- 새미골해물아구찜 (OSM → SBIZ)
    (UUID '1f8d437d-8a3f-3a50-8491-f68b7611b546', UUID '4ffd73af-4c8a-35d0-b179-e5bd26513f16'),  -- 동백횟집 (OSM → SBIZ)
    (UUID '43ef9121-c620-3342-9454-dbbbb1182c68', UUID '7ebb8dea-0a9b-3672-a121-abd981d5bca3'),  -- 서가원국수 (OSM → SBIZ)
    (UUID 'f15982f4-ddb3-3977-8230-8db38743d379', UUID '3fe9e3af-e018-3cc3-a93a-90d14517e11e'),  -- 로옹 (OSM → SBIZ)
    (UUID '4742cd78-e994-3d30-b25c-f28b53464659', UUID '57c38836-0563-3277-914d-372379465af6'),  -- 흥해참치 (OSM → SBIZ)
    (UUID 'c2ced07a-e84d-311d-ad42-a4d1a77a66f6', UUID '641cd350-5e39-3af6-88a7-c4e21019b914'),  -- 영변횟집 (OSM → SBIZ)
    (UUID 'f4d09ba5-7a88-3d2c-a3f9-24209851e68c', UUID 'e5a47caf-4129-30f5-9be3-de5d98535bf8'),  -- 헷츠 (OSM → SBIZ)
    (UUID 'b1c042a0-4f35-301c-b66b-e4d527a8a904', UUID '9d93f1d5-d4f3-39f1-a827-e5c22e1fe225'),  -- 그랜드 애플 (OSM → SBIZ)
    (UUID 'c32a9498-ae96-379d-a605-64220d4f26dd', UUID '2d6f74ea-4868-35b4-8ce1-5734c840f6e5'),  -- 일송횟집 (OSM → SBIZ)
    (UUID '0433fe0a-86e5-308e-b60b-330e73cd7d1a', UUID 'd3dab409-a5f7-3135-acec-b353e4eac7db'),  -- 토흐 (OSM → SBIZ)
    (UUID '0e79ae2f-c19a-33aa-92bf-4fa18eebbf30', UUID '824d3254-1abd-35f3-9393-441d99d8d1b2'),  -- 영주횟집 (OSM → SBIZ)
    (UUID '39e4be5a-129a-373f-8727-093dd65770b0', UUID '16f639e5-0f93-38ba-aa53-865fb1062cdf'),  -- 영남관 (OSM → SBIZ)
    (UUID 'f8bdd98d-b2f4-3552-88b2-972ddd73917c', UUID 'bf5c4b8e-f8bc-317b-8be1-45f71d3834f7'),  -- 만점식당 (OSM → SBIZ)
    (UUID 'f9482b73-c82d-34d0-9922-4463e8697c60', UUID '62f832f8-cefe-32b8-981f-a1f92e3462b7'),  -- 비치모텔 (OSM → TOURAPI)
    (UUID 'f1d78053-c80f-32c8-a3fb-42319a1ddbf8', UUID 'c546fe66-1396-3d0c-a5fa-7bd583e9a908'),  -- 개화 (OSM → SBIZ)
    (UUID '2b57733d-eb78-3640-af76-e74bb13cfa66', UUID 'b353ab5f-eed3-320a-b64c-2019a3861692'),  -- 동현횟집 (OSM → SBIZ)
    (UUID 'a4d841fe-41aa-386a-a703-3111964a1756', UUID '11e58260-09d1-3a76-a4e3-54bf63ae5543'),  -- 형제집 (OSM → SBIZ)
    (UUID 'fb14e3c4-49fc-388c-bc2e-b6819396332a', UUID '5bc01892-bf86-3b3b-86a1-383b05f6a332'),  -- 본동횟집 (OSM → SBIZ)
    (UUID 'c7b3db70-8980-348c-8f5d-b1edab00844a', UUID '3a4d55a1-988c-300e-a12b-e7910fdae921'),  -- 달인막창 (SBIZ → SBIZ)
    (UUID 'dc266582-027f-3801-bf0f-538f2509071a', UUID 'f3fd6b61-ed51-3e9b-b6c9-69337bb13d53'),  -- 수궁복집 (OSM → SBIZ)
    (UUID '523e13a3-c951-32f0-8e0a-cb8a713cac8c', UUID 'c3a23df1-6251-36fb-824a-6d86e64c2d74'),  -- 발리우드 (OSM → SBIZ)
    (UUID '64ae4919-901f-3939-afaf-8c6aa5efe098', UUID '9ed81960-5d62-3314-8fb8-fb921b7f399e'),  -- 아메리칸빌리지 (OSM → SBIZ)
    (UUID 'b30d95c9-3471-3d0a-9531-b1ea75caafa4', UUID '6172080b-af54-35cc-8572-b8f26018646f'),  -- 감골횟집 (OSM → SBIZ)
    (UUID '72fbf0a5-e767-3312-b652-ee0337caa278', UUID '6b39d59a-7010-3daf-9d34-0876648fbe43'),  -- 광안다이닝 (SBIZ → SBIZ)
    (UUID 'fe5a251f-56d0-3a44-852a-c29973b150b3', UUID '3075869c-a248-346c-99ba-3bc286bf480b'),  -- 산호횟집 (OSM → SBIZ)
    (UUID '1ca1f1de-ad4e-3dc2-9a3b-2978d8e86279', UUID '5f7f64c7-163a-3d49-a078-37115aa3ccc4'),  -- 영미횟집 (OSM → SBIZ)
    (UUID '878a78f8-551f-301b-9c51-5b906aa4a040', UUID '217fb780-1b63-3610-89ad-0a65ed4a870f'),  -- W모텔 (OSM → OSM)
    (UUID 'e42d3b0f-a6a1-3bf6-90d7-9ace57c26904', UUID 'e5ff6764-30c2-3bcb-92a1-413b390e8deb'),  -- 청도횟집 (OSM → SBIZ)
    (UUID '9558490e-6d4d-3999-983c-29981dcb4c85', UUID 'c4fd903b-a181-3478-a629-feaa3daee193'),  -- 미식육 (SBIZ → SBIZ)
    (UUID '46ca01b2-0044-3362-b2ad-b98fd822af0f', UUID '9688858f-09c5-30a9-a855-6c4246a9ca23'),  -- 주차장식당 (OSM → SBIZ)
    (UUID '5d8b240b-85bb-3316-8ac3-59aeddb228d8', UUID '2ade2503-ed5a-321d-832f-ff86b7ec200e'),  -- 아미티스 (SBIZ → SBIZ)
    (UUID 'cfa9a0cb-4707-33c2-9805-8b4c6270fba5', UUID '1f31b6f7-a8d6-34bd-bf4f-186de0584a3f'),  -- 명물횟집 (OSM → SBIZ)
    (UUID 'fdbec688-c59d-383e-a6d4-a48e8a5d3668', UUID 'ab4e8ad7-066a-3160-a950-cf719d350f12'),  -- 수림횟집 (OSM → SBIZ)
    (UUID '35e273d1-3adf-3416-8ecc-98512e0cb57b', UUID 'ff552ffb-0a3f-37bf-8cce-c217d9f1af91'),  -- 나가하마만게츠 광안리점 (OSM → SBIZ)
    (UUID 'e9380118-2528-3866-a9af-cbb452d4d855', UUID '2646060b-048a-3dc2-9e3a-33eede972bf8'),  -- 루비모텔 (OSM → OSM)
    (UUID '5e4488e7-32c9-3674-9520-4d074d56146d', UUID 'aef92ab6-e545-3133-84aa-3ddcff9edd91'),  -- 스무고개 (OSM → SBIZ)
    (UUID 'd774a80b-4cd0-3bbd-9fda-7d4262e16095', UUID '129ae151-8f5c-3c32-a914-68065391ed30'),  -- 해변횟집 (OSM → SBIZ)
    (UUID '1267b1af-0d7c-3fa2-acce-8a72525eb29d', UUID '6b459650-a13a-3796-ad93-ff083e9182f3'),  -- 우리콩순두부 (OSM → SBIZ)
    (UUID '819ff1b1-cfd0-32f3-a72b-c8ecdd7dd82a', UUID '9230c582-3a22-386e-9bd8-3c900ee74b4a'),  -- 남일횟집 (OSM → SBIZ)
    (UUID '5a6e0e53-a245-330d-9645-c583ae57dfa2', UUID '8241de23-6675-3d5b-b3be-bca3d3e5f61c'),  -- 백수농원 (OSM → SBIZ)
    (UUID '9504088b-6e2f-3f50-a5db-06ddb052c3c0', UUID '01efe00f-0a5a-34de-bea2-83dc4a234b7d'),  -- 뉴부산횟집 (OSM → SBIZ)
    (UUID 'b867eb03-88bc-33e6-8582-c6312ca039fc', UUID 'a04e929e-049e-3bf9-84a7-bcf34f3a4774'),  -- 호야 (OSM → SBIZ)
    (UUID '491c6bb5-2ea2-3e2b-959e-68c8c9d32c5e', UUID 'd19deca0-fd50-3c67-bac4-94c65f14103a'),  -- 수영동우담 (OSM → SBIZ)
    (UUID '6c015dd9-f690-3fd0-a0de-da1813aed38e', UUID '32d9c98c-d097-3e5d-b02b-96be819b0449'),  -- 버킹검모텔 (OSM → OSM)
    (UUID '3c2590a0-d137-3ebd-a740-0b4d4ed0976c', UUID '0ab88d52-8aa4-3add-ae03-ebc00facfa6d'),  -- 이바구캠프 (OSM → TOURAPI)
    (UUID '43859d39-2abf-3380-a28e-25b888e57891', UUID 'f0b183a5-7cc7-3790-9bf1-fd868a5c6027'),  -- 파라다이스호텔부산 (KAKAO_LOCAL → TOURAPI)
    (UUID 'd2804fee-6b93-367c-bc3d-897578d8f218', UUID 'f0b183a5-7cc7-3790-9bf1-fd868a5c6027'),  -- 파라다이스 호텔 부산 (OSM → TOURAPI)
    (UUID 'cfe31676-8472-38bc-81ec-0ba11bdba503', UUID 'c90dfd00-cc3c-3c05-bb57-7a7924ce02b0'),  -- 펠릭스 바이 STX (OSM → TOURAPI)
    (UUID 'b694ce7e-f664-3adf-8a59-0b38a1a83a6b', UUID '200cb33f-393d-36b0-b82e-4d469d4ddf7c'),  -- 로망스모텔 (OSM → OSM)
    (UUID '6e2ab49e-5a29-331e-9076-be0dc7dcc0ac', UUID '384918ef-cb3a-33f7-9c06-bc6d14617342'),  -- 커피미미 (OSM → SBIZ)
    (UUID 'a2da180c-9965-3c40-a863-8d338e6ca0fc', UUID '77fc053a-6815-3492-af2c-d7562f63ba74'),  -- 데자뷰모텔 (OSM → OSM)
    (UUID 'f21748b1-2a8a-3e23-8228-2195be5b3173', UUID 'a40ed47a-b93c-371a-b3e1-82b239ae3202'),  -- 오베론모텔 (OSM → OSM)
    (UUID 'be7b9e7d-a3ce-3d63-a46e-1974d1dcebd4', UUID '5326010e-dfb9-36b5-8eff-99b4fd090028'),  -- 엔모텔 (OSM → OSM)
    (UUID 'f70a5a7c-c887-3db3-bad9-0ba8c925ad59', UUID 'bbcfafbd-653f-3a45-b0d0-b146d7774acc'),  -- 고래당 (OSM → SBIZ)
    (UUID '10899c09-72d4-34f3-932f-270f843d8003', UUID '58c4aa95-087c-46e4-a557-002684712001'),  -- 흰여울문화마을 (OSM → TOURAPI)
    (UUID 'e4bad11d-9245-369c-a17b-269e2fc400f6', UUID '784fb3ab-16af-3ce2-b556-880fc61efdc3'),  -- 부산대학교박물관 (OSM → TOURAPI)
    (UUID 'b67df509-70b4-3b43-a75c-57a4eea3173d', UUID '2f4a279c-de03-300d-9a26-8611253fac1d'),  -- 은하사 (OSM → OSM)
    (UUID 'c4995d33-a30a-37a1-8e2e-3c2b64f8f2bf', UUID '20a94350-d50c-397e-9452-15cae2345eb0'),  -- 수목원 (OSM → OSM)
    (UUID 'af21ae7f-982b-3380-affd-7ac96310205e', UUID '66f46aa8-fdb1-3d0d-ae3d-00f897cac9bb'),  -- (주)동부산관광호텔 (Dongbusan Tourist Hotel) (OSM → OSM)
    (UUID 'b9264493-c7b0-3b94-a549-1455fa920970', UUID '0fa53483-1cc9-3919-9ee1-7d9715bedbbe'),  -- 등대횟집 (OSM → OSM)
    (UUID 'cb8e4805-c918-30d3-b6f3-3b54a5959e34', UUID '83aff238-6d80-3261-98b5-91a26820c93a'),  -- 수림횟집 (OSM → OSM)
    (UUID '81dbbd2b-8b99-3403-b999-296cfdb45e03', UUID '742365d8-831c-354a-870c-c4db70ec0e35'),  -- 수횟집 (OSM → OSM)
    (UUID 'cb33d319-9b5d-300b-8f7d-1246c189dcbd', UUID '3f485742-bd2d-3cb8-a6d7-9873328ef61d'),  -- 금수사 (OSM → TOURAPI)
    (UUID 'b1dde5f7-edd3-351e-a27b-8fb7baa489a1', UUID 'bfd6f8a7-6732-32bb-a43d-42cff8ef1f20'),  -- 보광사 (OSM → TOURAPI)
    (UUID '4ccef563-90b0-3006-b1c5-640d3a6a11db', UUID '45457cb9-8790-3dcc-9fe6-577e719056ef'),  -- 월명사 (OSM → TOURAPI)
    (UUID '136e3b75-66b9-3818-9d94-16d8c69a43a0', UUID '5592538a-fff2-3776-b13b-2560c0183f50'),  -- 박태준기념관 (OSM → TOURAPI)
    (UUID '59b04ae0-639c-308a-9103-dabdcdda8773', UUID '0d0043c0-5e21-3e3f-be4e-f6c8df41a2e4'),  -- 관음사 (OSM → TOURAPI)
    (UUID '68819fff-f7ca-3a1b-bb07-fc5a17a986dc', UUID '1f2c7af1-1c4c-39b9-bf91-e92adb07f90f'),  -- 별장횟집 (OSM → OSM)
    (UUID '5a12a8a0-4970-32ff-8813-46a435166e88', UUID '0908c1c6-8b2f-303d-857f-b4917873291e'),  -- 해진횟집 (OSM → OSM)
    (UUID 'cc3e8bd8-e8af-3118-8e7b-803079458876', UUID '98b15e5d-9389-3e09-825e-d2340e21aa16'),  -- 의령소바 (OSM → OSM)
    (UUID 'b8fdb5f2-95ac-3ccf-b87c-032892ba9975', UUID '7c3bbfc0-ecbc-3dd7-8d6e-97eb4905c40a'),  -- Benikea Hotel Haeundae (OSM → OSM)
    (UUID '4599341c-71bb-3bc4-b183-71bd233976f7', UUID '29b78259-c60f-31ba-9634-2ebb9fb119d1'),  -- 갈맷길 (OSM → OSM)
    (UUID 'cb307185-cbf6-32ca-9ae8-c100c2bda237', UUID '08cd2eb5-cfe1-3b7b-9b37-e30fddbc003e'),  -- 새양산교회 (OSM → OSM)
    (UUID '9ada619a-ab6f-334d-bab9-592e521151f4', UUID '144ad25d-2b49-3b62-a9fe-3c8c92edec5b'),  -- 바다횟집 (OSM → OSM)
    (UUID 'ba8c2fef-6223-3baf-b0ee-c9d12b25b92f', UUID '6f1a9aaf-1535-3e03-bb3f-b06878d63a90'),  -- 스타벅스 (OSM → OSM)
    (UUID 'd7e53f44-5e3e-3610-833b-01c6294f4d60', UUID '9c087edb-e3cc-322a-86da-f54d5f9e2d35'),  -- 갈맷길 (녹산) (OSM → OSM)
    (UUID 'fca3647e-0250-3921-8ec2-9a62e0a42930', UUID '9c087edb-e3cc-322a-86da-f54d5f9e2d35'),  -- 갈맷길 (녹산) (OSM → OSM)
    (UUID '630c1014-9c01-3e12-b4c8-c059cbf03379', UUID '42f023e6-c54d-3cfc-b763-c6aa26514cf0'),  -- 금곡역 4번 출구 자전거도로 진입 (OSM → OSM)
    (UUID 'c6debb7c-080f-30de-bf40-8c51a80cf756', UUID '83d765b0-6c4e-3002-bd41-f01f949e1637'),  -- 사계절가야밀면 (OSM → OSM)
    (UUID '5fc853f5-8f12-3696-a31a-3b794e2d5304', UUID '3c2c565b-0f27-3e6d-b76f-f646edfd67ec'),  -- 파라다이스 (OSM → OSM)
    (UUID 'eb16373b-a148-3bf9-9660-e7957665ce88', UUID '86fd551a-afd0-3506-abf1-a4488bd68028'),  -- 가야포차선지국밥 (OSM → OSM)
    (UUID '71a2bcfe-769f-3e1d-9bd9-9728882a1aad', UUID '455b9af8-9df4-3411-b22a-ad7f127c03b8'),  -- 이재모 Pizza (OSM → OSM)
    (UUID '49278eb3-8f7e-3843-9604-35e8711ca012', UUID '181e5461-49d2-3f77-ac98-80eaaf4b445a'),  -- Haeparang Cafe (OSM → OSM)
    (UUID 'fff7a938-fd2e-3ea2-84f1-8045e79a8239', UUID 'afec24e6-0521-3e99-9c66-8513e3f0854c'),  -- 이재모피자 (OSM → OSM)
    (UUID 'f7f38cae-966f-3ee6-be3a-8a3754059262', UUID '8ba72a11-ad44-31be-91c8-78385ce61400'),  -- 부산미도어묵 (OSM → OSM)
    (UUID 'a6857b87-021b-3152-b80e-4ff227b6f6ef', UUID 'a5f7debc-fd87-307d-a6ec-9aee255aa506'),  -- 호텔 아벤트리 부산 (OSM → OSM)
    -- 사람이 확인한 짝 — 150m 규칙 밖(370m)
    (UUID 'b52aa08a-4462-3459-bd04-bc24143b48de', UUID '148d72d0-4748-43b0-a650-000126080001')   -- 송정해수욕장 (OSM 걷기 길 → TOURAPI 바다)
  ) AS pair(dup, keep);
