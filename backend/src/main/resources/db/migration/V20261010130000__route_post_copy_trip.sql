-- S15P21E201-2043 — 「이 루트로 여행 만들기」로 만든 여행을 지우면 「따라 간 사람」에서 뺀다.
--
-- 복사 반응(kind = 'COPY')에 그때 만든 여행을 적어 둔다. 여행을 지우면(soft delete) 그 여행을 가리키는
-- 복사 반응을 지우고 copy_count 를 하나 줄인다. 한 사람은 여전히 한 번만 센다(기본 키 그대로).
-- 같은 사람이 다시 복사하면 마지막에 만든 여행을 가리킨다. 외래 키는 두지 않는다 — 여행은 지워도 행이 남는다(soft delete).
-- 옛 복사 반응은 NULL 이다. 어느 여행인지 모르므로 지우지 않는다(지어내지 않는다).
ALTER TABLE route_post_reaction ADD COLUMN trip_id UUID;

CREATE INDEX ix_route_post_reaction_trip ON route_post_reaction (trip_id) WHERE trip_id IS NOT NULL;
