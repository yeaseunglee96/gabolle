-- S15P21E201-366 — 기상청 단기예보 캐시 표.
--
-- 같은 격자(nx,ny)+같은 발표 회차(base_date+base_time)면 기상청 응답이 고정된다. 그래서
-- 다시 받지 않고 답을 들고 있는다. 보관은 캐시 열쇠에 발표 회차가 이미 들어 있어 값이
-- 절대 안 바뀌므로, translation_cache 와 같은 이유로 배치로 지우지 않는다 — 조회 시점에
-- expires_at 이 지나면 캐시 미스로 취급하는 것으로 충분하다.
--
-- cache_key 는 "{nx}_{ny}_{baseDate}{baseTime}" 모양의 평문이다. 번역 캐시의 source_hash 와
-- 달리 원문을 감출 이유가 없다 — 격자 번호와 발표 시각은 개인정보가 아니다.

CREATE TABLE weather_forecast_cache (
    cache_id UUID PRIMARY KEY,
    cache_key VARCHAR(64) NOT NULL,
    forecast_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_weather_forecast_cache_cache_key UNIQUE (cache_key)
);

CREATE INDEX idx_weather_forecast_cache_expires_at ON weather_forecast_cache (expires_at);
