-- ══════════════════════════════════════════════════════════════════════════════
-- 댓글 — 같은 표에 부모 칸을 단다 (S15P21E201-1183)
-- ══════════════════════════════════════════════════════════════════════════════
--
-- 🔴 댓글을 별도 표로 만들지 않는다. 사장님이 정하신 것이고 피드 v2 시안도 같다 —
--    「댓글 = 같은 StoryDto 형태 + parentId. 별도 Comment 타입 만들지 말 것」.
--    화면이 같은 부품을 쓰면 응답도 같은 모양이어야 하고, 응답이 같은 모양이면
--    표도 같은 표가 맞다.
--
-- 🔴 같은 표가 싼 이유는 재서 정했다. StoryRepository 의 메서드 일곱이
--    NOT_DELETED_AND_PUBLISHED 상수 하나를 공유해서, 댓글이 피드에 새는 것을 막는 데
--    한 줄이면 된다. 저장소 전체에서 story 를 직접 읽는 곳도 둘뿐이다
--    (StoryRepository · AccountDeletionService).
--
--    그리고 신고·숨김·소프트삭제·사진·탈퇴처리가 그대로 따라온다 — 댓글에 전부 필요한
--    것들이고, 다른 표로 갔으면 그 다섯을 다시 만들어야 했다.
--
-- ── 🔴 댓글의 공개범위는 「부모를 따른다」 ──────────────────────────────────────
--
-- story.visibility 는 NOT NULL 인데 댓글에 공개범위는 뜻이 없다. 그래서 댓글은
-- 언제나 'PUBLIC' 으로 저장하고, 실제로 보이는가는 부모의 공개범위가 정한다.
--
-- 🔴 이 칸을 보고 「댓글도 공개범위를 고를 수 있구나」로 읽지 마라. 그 값은 자리를
--    채우려고 넣은 것이지 사용자가 고른 것이 아니다. 댓글에 공개범위를 주려면
--    부모와 어긋날 때 무엇이 이기는지부터 정해야 한다 — 그 답이 없는 채로 칸만
--    열면, 비공개 글에 공개 댓글이 달리는 상태가 생긴다.
--
-- publish_at 도 같은 이유로 created_at 과 같은 값을 넣는다. 예약 댓글은 말이 안 된다.
--
-- ── 🔴 세기는 「바로 아래만」 센다 ────────────────────────────────────────────
--
-- 댓글에 댓글이 달리므로 reply_count 의 뜻이 두 가지일 수 있다. 직접 달린 것만 세는
-- 쪽으로 정했다.
--
--   바로 아래만 : 지울 때 부모 하나만 내리면 끝. 깊이와 무관하게 언제나 한 칸
--   아래 전부   : 조상을 전부 거슬러 올라가며 내려야 한다. 깊어질수록 느리고,
--                 중간에 하나만 어긋나면 되찾을 방법이 없다
--
-- 깊이 자체는 제한하지 않는다. DB 는 깊이를 모르고, 몇 단까지 보여줄지는 화면이 정한다.

ALTER TABLE story ADD COLUMN parent_story_id UUID;

-- 🔴 ON DELETE 규칙을 일부러 안 단다 (NO ACTION).
--
--    CASCADE 였다면 부모를 지울 때 자식 댓글이 같이 사라지는데, 그건 남의 글을 지우는
--    것이다. SET NULL 이었다면 자식이 부모를 잃고 원글인 척 피드에 올라온다 —
--    아래 ix_story_parent 로 거르는 조건이 parent_story_id IS NULL 이라 정확히 그렇게 된다.
--
--    실제로는 둘 다 안 일어난다. 이 저장소는 글을 하드 삭제하지 않고 deleted_at 만
--    찍기 때문이다(StoryService.markDeleted). 그래서 부모 행은 계속 남아 있고 자식은
--    계속 그것을 가리킨다 — 화면이 그 자리를 「삭제된 댓글」로 그리면 된다.
--
--    규칙을 안 다는 것이 곧 「하드 삭제를 하려거든 먼저 여기를 보라」는 표시다.
ALTER TABLE story ADD CONSTRAINT fk_story_parent
    FOREIGN KEY (parent_story_id) REFERENCES story (story_id);

ALTER TABLE story ADD COLUMN reply_count INTEGER NOT NULL DEFAULT 0;

-- 🔴 음수를 DB 가 막는다. 세기는 지울 때 내리는데, 같은 댓글을 두 번 지우려는 경로가
--    생기면 조용히 -1 이 된다. 화면에 「답글 -1개」가 뜨는 것보다 여기서 터지는 편이 낫다.
ALTER TABLE story ADD CONSTRAINT ck_story_reply_count CHECK (reply_count >= 0);

-- 댓글 목록은 「이 글의 자식을 오래된 순으로」 읽는다. 원글만 읽는 피드 질의도
-- parent_story_id 로 거르므로 같은 색인을 쓴다.
CREATE INDEX ix_story_parent ON story (parent_story_id, created_at);

COMMENT ON COLUMN story.parent_story_id IS
    '댓글이면 부모 글. 원글이면 NULL. 댓글의 댓글도 같은 칸으로 이어지고 깊이 제한은 없다 (S15P21E201-1183)';
COMMENT ON COLUMN story.reply_count IS
    '직접 달린 댓글 수. 손자 이하는 세지 않는다 — 지울 때 조상을 거슬러 올라가지 않으려고 (S15P21E201-1183)';
