-- ══════════════════════════════════════════════════════════════════════════════
-- 개인정보 자동 정리 배치가 조회·복사 낱개도 치운다 — S15P21E201-1216
-- ══════════════════════════════════════════════════════════════════════════════
--
-- story_view · story_link_copy 는 「90일만 보관한다」고 표 주석·도메인 주석·
-- 마이그레이션 주석 세 곳에 적혀 있었는데, 실제로 치우는 코드가 한 줄도 없었다
-- (S15P21E201-1201 이 표를 만들면서 배치는 다음으로 미뤘다). 두 표는 그동안
-- 끝없이 자라고 있었다.
--
-- 이 마이그레이션이 하는 일은 하나다 — 실행 기록 표에 **두 카테고리의 건수 칸**을
-- 더한다. 지우는 것은 응용 코드(PrivacyCleanupService)가 한다.
--
-- ── 🔴 새 스케줄러를 만들지 않았다 ──────────────────────────────────────────
--
-- 개인정보 자동 정리 배치(S15P21E201-357 · -166)에 붙였다. 그쪽에는 실행 기록 행과
-- 실패 알림(MatterMost 웹훅)이 이미 있다. 배치를 하나 더 만들면 그 둘을 또 만들어야
-- 하고, 둘 중 하나만 실패했을 때 어느 쪽 기록을 봐야 하는지가 애매해진다.
--
-- ── 🔴 누적 칸(story.view_count · story.link_copy_count)은 안 건드린다 ──────
--
-- 낱개를 지우면서 누적 칸을 같이 내리면 **어제까지의 조회가 사라진다.** 낱개는
-- 「사람 × 글 × 하루 한 번」을 지키려고 두는 것이고, 누적은 누적이다.
-- V20260918010000 이 건 CHECK (view_count >= 0) 이 정확히 이 실수를 막으려고 있다 —
-- 같이 내리면 조용히 음수가 되기 때문이다.
--
-- ── 마이그레이션 번호 ────────────────────────────────────────────────────────
--
-- 붙이기 직전에 원격을 받아 모든 브랜치의 마이그레이션 파일을 훑었고, 가장 높은
-- 것이 V20260918010000 이라 그보다 높은 V20260918020000 으로 잡았다. 번호가 이미
-- 적용된 것보다 낮으면 Flyway 가 거부한다.

ALTER TABLE privacy_cleanup_run
    ADD COLUMN story_views_deleted INTEGER NOT NULL DEFAULT 0;
ALTER TABLE privacy_cleanup_run
    ADD COLUMN story_link_copies_deleted INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN privacy_cleanup_run.story_views_deleted IS
    '이 실행이 지운 조회 낱개(story_view) 건수. 누적 칸 story.view_count 는 안 내린다 (S15P21E201-1216)';
COMMENT ON COLUMN privacy_cleanup_run.story_link_copies_deleted IS
    '이 실행이 지운 링크 복사 낱개(story_link_copy) 건수. 누적 칸 story.link_copy_count 는 안 내린다 (S15P21E201-1216)';

-- ── 되돌리기 ─────────────────────────────────────────────────────────────────
--
-- ALTER TABLE privacy_cleanup_run DROP COLUMN story_link_copies_deleted;
-- ALTER TABLE privacy_cleanup_run DROP COLUMN story_views_deleted;
--
-- 되돌려도 지워진 낱개는 안 돌아온다. 이 칸들은 「몇 건 지웠나」의 기록일 뿐이다.
