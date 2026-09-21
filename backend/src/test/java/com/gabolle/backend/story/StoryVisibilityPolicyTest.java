package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.story.application.StoryVisibilityPolicy;
import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryVisibility;
import com.gabolle.backend.story.domain.UserFollow;
import com.gabolle.backend.story.repository.StoryCoauthorRepository;
import com.gabolle.backend.story.repository.UserFollowRepository;

/** {@link StoryVisibilityPolicy} 단위 테스트. DB 없이 돈다. */
class StoryVisibilityPolicyTest {

	private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");

	private final UserFollowRepository userFollowRepository = mock(UserFollowRepository.class);

	// 스텁의 기본값이 false 이므로 아래 경우들은 모두 "공동 작성자가 아니다" 로 판정된다.
	private final StoryCoauthorRepository coauthorRepository = mock(StoryCoauthorRepository.class);

	private final StoryVisibilityPolicy policy = new StoryVisibilityPolicy(this.userFollowRepository,
			this.coauthorRepository);

	@Test
	void authorAlwaysSeesOwnStoryEvenBeforePublishAndPrivate() {
		UUID author = UUID.randomUUID();
		Story story = story(author, StoryVisibility.PRIVATE, NOW.plus(1, ChronoUnit.DAYS));

		assertThat(this.policy.canView(story, author, NOW)).isTrue();
	}

	@Test
	void strangerCannotSeeBeforePublishTimeEvenIfPublic() {
		UUID author = UUID.randomUUID();
		UUID viewer = UUID.randomUUID();
		Story story = story(author, StoryVisibility.PUBLIC, NOW.plus(1, ChronoUnit.DAYS));

		assertThat(this.policy.canView(story, viewer, NOW)).isFalse();
	}

	@Test
	void strangerSeesPublicStoryAfterPublishTime() {
		UUID author = UUID.randomUUID();
		UUID viewer = UUID.randomUUID();
		Story story = story(author, StoryVisibility.PUBLIC, NOW.minus(1, ChronoUnit.DAYS));

		assertThat(this.policy.canView(story, viewer, NOW)).isTrue();
	}

	@Test
	void followerSeesFollowersOnlyStory() {
		UUID author = UUID.randomUUID();
		UUID viewer = UUID.randomUUID();
		Story story = story(author, StoryVisibility.FOLLOWERS, NOW.minus(1, ChronoUnit.DAYS));
		when(this.userFollowRepository.existsByKey(new UserFollow.Key(viewer, author))).thenReturn(true);

		assertThat(this.policy.canView(story, viewer, NOW)).isTrue();
	}

	@Test
	void nonFollowerCannotSeeFollowersOnlyStory() {
		UUID author = UUID.randomUUID();
		UUID viewer = UUID.randomUUID();
		Story story = story(author, StoryVisibility.FOLLOWERS, NOW.minus(1, ChronoUnit.DAYS));
		when(this.userFollowRepository.existsByKey(new UserFollow.Key(viewer, author))).thenReturn(false);

		assertThat(this.policy.canView(story, viewer, NOW)).isFalse();
	}

	@Test
	void strangerCannotSeePrivateStory() {
		UUID author = UUID.randomUUID();
		UUID viewer = UUID.randomUUID();
		Story story = story(author, StoryVisibility.PRIVATE, NOW.minus(1, ChronoUnit.DAYS));

		assertThat(this.policy.canView(story, viewer, NOW)).isFalse();
	}

	// 아래 넷은 viewer 가 null 인 경우, 즉 로그인하지 않은 사람이다.

	@Test
	@DisplayName("로그인하지 않은 사람도 공개된 PUBLIC 기록은 본다")
	void anonymousSeesPublishedPublicStory() {
		Story story = story(UUID.randomUUID(), StoryVisibility.PUBLIC, NOW.minus(1, ChronoUnit.DAYS));

		assertThat(this.policy.canView(story, null, NOW)).isTrue();
	}

	@Test
	@DisplayName("🔴 로그인하지 않은 사람에게 팔로워 전용 기록은 안 보인다 — 팔로우 저장소를 부르지도 않는다")
	void anonymousCannotSeeFollowersOnlyStory() {
		Story story = story(UUID.randomUUID(), StoryVisibility.FOLLOWERS, NOW.minus(1, ChronoUnit.DAYS));

		assertThat(this.policy.canView(story, null, NOW)).isFalse();
		// 복합 키에 null 을 넣는 조회는 동작이 보장되지 않는다. 그 길로 아예 안 간다.
		verifyNoInteractions(this.userFollowRepository);
	}

	@Test
	@DisplayName("🔴 로그인하지 않은 사람에게 나만 보기 기록은 안 보인다")
	void anonymousCannotSeePrivateStory() {
		Story story = story(UUID.randomUUID(), StoryVisibility.PRIVATE, NOW.minus(1, ChronoUnit.DAYS));

		assertThat(this.policy.canView(story, null, NOW)).isFalse();
	}

	@Test
	@DisplayName("🔴 공개 시각이 안 된 PUBLIC 기록은 로그인하지 않은 사람에게 안 보인다")
	void anonymousCannotSeePublicStoryBeforePublishTime() {
		Story story = story(UUID.randomUUID(), StoryVisibility.PUBLIC, NOW.plus(1, ChronoUnit.DAYS));

		assertThat(this.policy.canView(story, null, NOW)).isFalse();
	}

	private static Story story(UUID author, StoryVisibility visibility, Instant publishAt) {
		return new Story(UUID.randomUUID(), author, null, null, "본문", null, visibility, publishAt,
				NOW.minus(2, ChronoUnit.DAYS));
	}
}
