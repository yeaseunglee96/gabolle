-- S15P21E201-343 — 번역 중계 캐시 표.
--
-- 같은 문장(+방향)을 다시 번역하지 않게 답을 들고 있는다. 보관은 7일이고, 배치로 지우지
-- 않는다 — 조회 시점에 expires_at 이 지나면 캐시 미스로 취급하는 것으로 충분하다.
--
-- 🔴 원문(source)은 어디에도 평문으로 없다. source_hash 는 TranslationHash.of()
-- (SHA-256 hex, 64자)가 만든 값이고, anonymous_session.token_hash 와 같은 자리·같은 길이
-- 관례를 따른다 — "번역한 문장을 데이터베이스나 로그에 남기지 않는다" 는 요구는 원문에
-- 대한 것이다. 번역 결과(target_text)는 캐시 히트를 위해 평문으로 둔다.

CREATE TABLE translation_cache (
    cache_id UUID PRIMARY KEY,
    source_hash VARCHAR(64) NOT NULL,
    target_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_translation_cache_source_hash UNIQUE (source_hash)
);

CREATE INDEX idx_translation_cache_expires_at ON translation_cache (expires_at);
