-- S15P21E201-1908 — 여행 별점. 여행 하나에 구성원마다 한 줄(1~5). 다시 매기면 덮어쓰고, 지우면 줄이 없어진다.
--
-- 여행 행이 지워지면 별점도 함께 간다(CASCADE).
-- user_id 에는 app_user 외래키를 걸지 않는다 — trip_member 와 같다(V20260910040000 이 그쪽 외래키를 뺐다).
-- 로그인 전 익명 사용자의 여행도 구성원이 app_user 에 없는 ID 라 외래키가 있으면 별점이 500 으로 터진다.
-- 탈퇴 때는 AccountDeletionService 가 이 사람의 줄을 직접 지운다.

CREATE TABLE trip_rating (
    trip_id    UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    score      SMALLINT    NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_trip_rating PRIMARY KEY (trip_id, user_id),
    CONSTRAINT ck_trip_rating_score CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT fk_trip_rating_trip FOREIGN KEY (trip_id) REFERENCES trip (trip_id) ON DELETE CASCADE
);
