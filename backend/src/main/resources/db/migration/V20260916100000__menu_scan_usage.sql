-- S15P21E201-1038 — 메뉴판 읽기 한도를 서버 메모리에서 이 표로 옮긴다.
--
-- 그전에는 MenuScanRateLimiter 가 사용자별 호출 시각을 JVM 안 맵에 들고 있었다.
-- 그 방식의 문제는 둘이다.
--
--   (1) 재시작하면 집계가 0 이 된다. 배포가 잦은 기간에는 하루 한도가 사실상 없다
--   (2) 서버를 여러 대로 늘리면 각자 따로 센다. 대수만큼 한도가 늘어난다
--
-- 이것이 지키는 것이 돈이 나가는 GMS 키라서, "대충 맞으면 된다" 로 둘 수 없다.
--
-- 한 번 부를 때마다 한 행이다. 「오늘 몇 번」 을 숫자 하나로 들고 더하는 모양이
--    아니다. 그 모양이면 분 한도(최근 60초)를 셀 수 없고, 날이 바뀌는 시각을 서버가
--    따로 판단해야 한다. 행으로 두면 두 한도가 같은 자료에서 창만 바꿔 나온다.

CREATE TABLE menu_scan_usage (
    menu_scan_usage_id UUID        PRIMARY KEY,
    user_id            UUID        NOT NULL,
    -- 부른 시각. 분 한도와 일 한도가 이 한 칸을 서로 다른 창으로 읽는다.
    scanned_at         TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_menu_scan_usage_user
        FOREIGN KEY (user_id) REFERENCES app_user (user_id) ON DELETE CASCADE
);

-- 세는 질의는 언제나 「이 사람의 최근 N 초」 다. 그 모양 그대로 색인을 둔다.
CREATE INDEX ix_menu_scan_usage_user_time ON menu_scan_usage (user_id, scanned_at DESC);

-- 정리가 사용자별 색인만으로는 부족하다. 하루 지난 행을 전부 지우는 쓸기는
--    사용자를 가리지 않고 시각만 보므로 시각 단독 색인이 따로 필요하다.
CREATE INDEX ix_menu_scan_usage_time ON menu_scan_usage (scanned_at);

COMMENT ON TABLE menu_scan_usage IS
    '메뉴판 읽기 호출 기록. 분·일 한도를 이 표에서 센다 — 재시작과 서버 증설을 넘겨도 같은 수가 나온다 (S15P21E201-1038).';
