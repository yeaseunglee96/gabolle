-- S15P21E201-1935 — 여행 돈. 쓴 돈 한 줄씩(trip_expense)과 여행마다 예산 하나(trip_budget).
--
-- 금액은 원(KRW) 정수로만 적는다 — 외화로 쓴 돈도 화면이 원으로 바꿔 적는다. 통화를 섞어 두면 합계를 낼 수 없다.
-- 여행 행이 지워지면 둘 다 함께 간다(CASCADE).
-- created_by·paid_by 에는 app_user 외래키를 걸지 않는다 — trip_member·trip_rating 과 같은 이유(익명 여행의 구성원).
-- 탈퇴 때는 AccountDeletionService 가 이 사람이 «적은» 줄을 지운다(남이 적은 줄의 낸 사람 칸은 남는다).
-- trip_budget.updated_by 는 일부러 남긴다 — 예산은 여행의 것이지 마지막으로 고친 사람의 것이 아니다. 화면에 보이지도 않는다.

CREATE TABLE trip_expense (
    expense_id  UUID         NOT NULL,
    trip_id     UUID         NOT NULL,
    created_by  UUID         NOT NULL,
    paid_by     UUID         NOT NULL,
    amount_krw  INTEGER      NOT NULL,
    category    VARCHAR(20)  NOT NULL,
    place_name  VARCHAR(120),
    note        VARCHAR(200),
    split_even  BOOLEAN      NOT NULL,
    spent_at    TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_trip_expense PRIMARY KEY (expense_id),
    CONSTRAINT ck_trip_expense_amount CHECK (amount_krw BETWEEN 1 AND 100000000),
    CONSTRAINT ck_trip_expense_category CHECK (category IN ('FOOD', 'CAFE', 'TRANSPORT', 'ADMISSION', 'SHOPPING', 'LODGING', 'OTHER')),
    CONSTRAINT fk_trip_expense_trip FOREIGN KEY (trip_id) REFERENCES trip (trip_id) ON DELETE CASCADE
);

CREATE INDEX ix_trip_expense_trip_spent ON trip_expense (trip_id, spent_at);
CREATE INDEX ix_trip_expense_created_by ON trip_expense (created_by);

CREATE TABLE trip_budget (
    trip_id    UUID        NOT NULL,
    amount_krw INTEGER     NOT NULL,
    updated_by UUID        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_trip_budget PRIMARY KEY (trip_id),
    CONSTRAINT ck_trip_budget_amount CHECK (amount_krw BETWEEN 1 AND 1000000000),
    CONSTRAINT fk_trip_budget_trip FOREIGN KEY (trip_id) REFERENCES trip (trip_id) ON DELETE CASCADE
);
