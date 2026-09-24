-- S15P21E201-1602 — 여행마다 「지금 확정된 일정」.
--
-- 여행 하나에 일정이 여럿일 수 있다 — 추천이 만든 기본 일정(A안)에, 코스 2·3안을 고르면 그때마다 일정이
-- 하나씩 더 생긴다. 앱은 어느 것을 열지 몰라 「버전 N」 목록을 띄워 물었다. 사용자가 이 단계를 없애라고 했다.
--
-- chosen_at = 이 일정을 마지막으로 「고른」 시각. 여행마다 가장 최근 것이 확정이다.
-- - 일정이 생기면(추천·코스 고르기·복제 어느 길이든) 그 시각 — 기본값 now()
-- - 이미 있는 안을 다시 고르면(A안으로 되돌아가기 포함) 지금으로 옮긴다(TripCourseService.choose)
-- 「가장 나중에 만든 일정」이 아니다 — A 를 다시 고르면 A 가 확정이어야 한다.
--
-- 옛 행은 만든 시각으로 채운다. 전에는 B·C안이 고를 때만 만들어졌으므로 만든 시각이 곧 고른 시각이다.
-- 옛날에 A안을 「다시」 고른 기록만 알 수 없다 — 그런 여행은 마지막으로 만든 일정이 확정으로 보인다.

ALTER TABLE itineraries ADD COLUMN chosen_at TIMESTAMPTZ;

UPDATE itineraries SET chosen_at = created_at;

ALTER TABLE itineraries ALTER COLUMN chosen_at SET DEFAULT now();
ALTER TABLE itineraries ALTER COLUMN chosen_at SET NOT NULL;

-- 여행 목록이 여행 여럿의 확정 일정을 한 번에 찾는다(TripCoverAdapter).
CREATE INDEX ix_itineraries_trip_chosen ON itineraries (trip_id, chosen_at DESC);
