-- S15P21E201-2048 — 앱 안 업데이트 안내. GET /api/v1/app/version 이 이 표를 매 요청 읽는다(캐시 없음).
--
-- 값을 바꾸는 법 (코드 배포 없이, 즉시 반영):
--   새 판이 스토어에 «심사 통과·공개된 뒤»에 latest_version 을 올린다. 먼저 올리면 사용자가 스토어에서
--   아직 없는 판을 찾게 된다.
--     UPDATE app_release SET latest_version = '1.0.4', updated_at = now() WHERE platform = 'ios';
--   강제 업데이트(그보다 낮은 앱은 업데이트 단추만 보인다)는 min_supported_version 을 올린다.
--   서버가 옛 앱과 호환을 끊을 때만 쓴다:
--     UPDATE app_release SET min_supported_version = '1.0.3', updated_at = now() WHERE platform = 'android';
--   버전은 숫자.숫자.숫자 꼴이다(CHECK 로 막는다).
CREATE TABLE app_release (
    platform              VARCHAR(16)  PRIMARY KEY CHECK (platform IN ('ios', 'android')),
    latest_version        VARCHAR(32)  NOT NULL CHECK (latest_version ~ '^[0-9]+\.[0-9]+\.[0-9]+$'),
    min_supported_version VARCHAR(32)  NOT NULL CHECK (min_supported_version ~ '^[0-9]+\.[0-9]+\.[0-9]+$'),
    store_url             VARCHAR(512) NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO app_release (platform, latest_version, min_supported_version, store_url) VALUES
    ('ios',     '1.0.3', '1.0.0', 'https://apps.apple.com/app/id6811252919'),
    ('android', '1.0.3', '1.0.0', 'https://play.google.com/store/apps/details?id=com.gabolle.app');
