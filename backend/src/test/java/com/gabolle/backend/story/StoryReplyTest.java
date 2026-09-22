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
 * 댓글 규칙 가운데 DB 없이 판정되는 것만 모은다. 같은 폴더의 통합 시험은 PostgreSQL 이 없으면
 * 실패가 아니라 건너뜀이 되므로, 도커 없이도 도는 자리가 따로 필요하다.
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

		// 부모가 FOLLOWERS 여도 댓글 행은 PUBLIC 이다. NOT NULL 인 칸을 채운 것일 뿐이고,
		// 실제로 보이는가는 부모가 정한다.
		assertThat(reply.getVisibility()).isEqualTo(StoryVisibility.PUBLIC);
		// 예약 댓글은 없다 — 공개 시각이 만든 시각과 같다.
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

		// DB 에도 같은 제약(ck_story_reply_count)이 있지만, 여기서 먼저 막아야 어느 자리인지가 남는다.
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
		Story post = post();
		Story depth1 = Story.reply(UUID.randomUUID(), UUID.randomUUID(), post.getStoryId(), "댓글", this.now);
		Story depth2 = Story.reply(UUID.randomUUID(), UUID.randomUUID(), depth1.getStoryId(), "답글", this.now);
		Story depth3 = Story.reply(UUID.randomUUID(), UUID.randomUUID(), depth2.getStoryId(), "답답글", this.now);

		post.addReply();
		depth1.addReply();
		depth2.addReply();

		// 세기는 바로 아래만 센다. 손자까지 세면 하나 지울 때마다 조상 전부를 거슬러 내려야 한다.
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
		// 자식은 지워진 댓글을 계속 가리킨다. 화면이 그 자리를 삭제된 댓글로 그린다.
		assertThat(child.isDeleted()).isFalse();
		assertThat(child.getParentStoryId()).isEqualTo(reply.getStoryId());
		// 자식이 사라진 것이 아니므로 지워진 댓글의 세기도 그대로다.
		assertThat(reply.getReplyCount()).isEqualTo(1);
		assertThat(parent.getReplyCount()).isZero();
	}
}
