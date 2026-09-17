package com.gabolle.backend.story;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryVisibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 댓글의 규칙 — S15P21E201-1183. <b>DB 없이</b> 도는 시험이다.
 *
 * <p>🔴 이 파일이 따로 있는 이유. 같은 폴더의 다른 시험들은 전부 진짜 PostgreSQL 을 요구해서
 * 도커가 없는 곳에서는 <b>실패가 아니라 건너뜀</b>이 된다. 댓글의 규칙 중 상당수는 DB 없이
 * 판정할 수 있는 것이라(값이 무엇으로 정해지는가, 세기가 어떻게 움직이는가) 그것만 여기 모은다.
 * 피드에서 걸러지는가·목록이 무엇을 주는가는 DB 가 있어야 하므로 통합 시험이 맡는다.
 */
class StoryReplyTest {

	private final Instant now = Instant.parse("2026-09-17T10:00:00Z");

	private Story post() {
		return new Story(UUID.randomUUID(), UUID.randomUUID(), null, null, "원글", null,
				StoryVisibility.FOLLOWERS, this.now, this.now);
	}

	@Test
	@DisplayName("🔴 댓글의 공개범위·공개시각은 사용자가 못 고른다 — 서버가 정한다")
	void replyDecidesItsOwnVisibilityAndPublishAt() {
		Story parent = post();

		Story reply = Story.reply(UUID.randomUUID(), UUID.randomUUID(), parent.getStoryId(), "댓글", this.now);

		// 🔴 부모가 FOLLOWERS 여도 댓글 행은 PUBLIC 이다. 그 칸은 NOT NULL 이라 자리를 채운
		//    것이고, 실제로 보이는가는 부모가 정한다. 이 값을 「댓글도 공개범위를 고를 수
		//    있다」로 읽으면 안 된다.
		assertThat(reply.getVisibility()).isEqualTo(StoryVisibility.PUBLIC);
		// 예약 댓글은 말이 안 된다 — 만든 시각과 같다.
		assertThat(reply.getPublishAt()).isEqualTo(this.now);
		assertThat(reply.isPublishedAt(this.now)).isTrue();
	}

	@Test
	@DisplayName("🔴 댓글은 여행·장소·지역을 안 가진다 — 원글의 칸이다")
	void replyCarriesNoPostOnlyFields() {
		Story reply = Story.reply(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "댓글", this.now);

		assertThat(reply.getTripId()).isNull();
		assertThat(reply.getPlaceId()).isNull();
		assertThat(reply.getRegion()).isNull();
	}

	@Test
	@DisplayName("부모가 없으면 댓글이 아니라 원글이다 — 만들 때 거부한다")
	void replyRequiresAParent() {
		assertThatThrownBy(() -> Story.reply(UUID.randomUUID(), UUID.randomUUID(), null, "댓글", this.now))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("parentStoryId");
	}

	@Test
	@DisplayName("원글과 댓글은 parentStoryId 하나로 갈린다")
	void isReplyLooksOnlyAtTheParentColumn() {
		assertThat(post().isReply()).isFalse();
		assertThat(Story.reply(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "댓글", this.now).isReply())
				.isTrue();
	}

	@Test
	@DisplayName("🔴 세기는 0 아래로 안 내려간다 — 「답글 -1개」가 뜨는 것보다 안 내려가는 편이 낫다")
	void replyCountNeverGoesNegative() {
		Story parent = post();
		assertThat(parent.getReplyCount()).isZero();

		// 안 달린 상태에서 지우는 경로가 생겨도 음수가 안 된다. DB 에도 같은 제약이 있지만
		// (ck_story_reply_count) 여기서 먼저 막아야 어느 자리에서 그랬는지가 남는다.
		parent.removeReply();
		assertThat(parent.getReplyCount()).isZero();

		parent.addReply();
		parent.addReply();
		parent.removeReply();
		assertThat(parent.getReplyCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 댓글의 댓글 — 깊이 제한이 없고, 세기는 각자 「바로 아래」만 센다")
	void nestedRepliesEachCountOnlyTheirDirectChildren() {
		// 원글 ← 댓글 ← 답글 ← 답답글. 깊이를 막는 것이 아무 데도 없다.
		Story post = post();
		Story depth1 = Story.reply(UUID.randomUUID(), UUID.randomUUID(), post.getStoryId(), "댓글", this.now);
		Story depth2 = Story.reply(UUID.randomUUID(), UUID.randomUUID(), depth1.getStoryId(), "답글", this.now);
		Story depth3 = Story.reply(UUID.randomUUID(), UUID.randomUUID(), depth2.getStoryId(), "답답글", this.now);

		post.addReply();
		depth1.addReply();
		depth2.addReply();

		// 🔴 원글의 세기가 3 이 아니라 1 이다. 손자·증손자는 안 센다.
		//
		//    손자까지 세면 depth3 을 지울 때 depth2 → depth1 → post 를 거슬러 올라가며
		//    전부 내려야 한다. 깊이가 깊어질수록 느리고, 중간에 하나만 어긋나면 되찾을
		//    방법이 없다. 「바로 아래만」이면 고치는 자리가 언제나 한 칸이다.
		assertThat(post.getReplyCount()).isEqualTo(1);
		assertThat(depth1.getReplyCount()).isEqualTo(1);
		assertThat(depth2.getReplyCount()).isEqualTo(1);
		assertThat(depth3.getReplyCount()).isZero();

		assertThat(depth3.getParentStoryId()).isEqualTo(depth2.getStoryId());
	}

	@Test
	@DisplayName("🔴 댓글을 지워도 그 댓글의 자식은 부모를 계속 가리킨다 — 남의 글을 지우지 않는다")
	void deletingAReplyDoesNotDetachItsChildren() {
		Story parent = post();
		Story reply = Story.reply(UUID.randomUUID(), UUID.randomUUID(), parent.getStoryId(), "댓글", this.now);
		Story child = Story.reply(UUID.randomUUID(), UUID.randomUUID(), reply.getStoryId(), "답글", this.now);
		parent.addReply();
		reply.addReply();

		reply.markDeleted(this.now);
		parent.removeReply();

		assertThat(reply.isDeleted()).isTrue();
		// 자식은 그대로다. 지워진 댓글을 계속 가리키고, 화면이 그 자리를 「삭제된 댓글」로 그린다.
		assertThat(child.isDeleted()).isFalse();
		assertThat(child.getParentStoryId()).isEqualTo(reply.getStoryId());
		// 지워진 댓글의 세기도 그대로다 — 자식이 사라진 것이 아니기 때문이다.
		assertThat(reply.getReplyCount()).isEqualTo(1);
		assertThat(parent.getReplyCount()).isZero();
	}
}
