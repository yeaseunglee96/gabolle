package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gabolle.backend.story.application.StoryVisibilityPolicy;
import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryVisibility;
import com.gabolle.backend.story.domain.UserFollow;
import com.gabolle.backend.story.repository.UserFollowRepository;

/**
 * {@link StoryVisibilityPolicy} 단위 테스트 — DB 없이 돈다.
 *
 * <p>🔴 이 판정은 예전에 {@code StoryService}와 {@code StoryReportService}가 각자 자기 파일 안에
 * 글자 그대로 같은 코드로 갖고 있던 것이다(S15P21E201-254 뽑아내기). 뽑아내는 과정에서 조건 하나라도
 * 놓쳤다면 여기 여섯 경우 중 하나가 먼저 깨진다 — 그래서 이 테스트가 "뽑아내기 전과 같은 입력에
 * 같은 답을 낸다" 는 증거다. 기존 {@code StoryCrudIntegrationTest}·모더레이션 통합 테스트가 API
 * 계층에서 같은 것을 다시 확인한다.
 */
class StoryVisibilityPolicyTest {

	private static final Instant NOW = Instant.parse("2026-09-07T00:00:00Z");

	private final UserFollowRepository userFollowRepository = mock(UserFollowRepository.class);

	private final StoryVisibilityPolicy policy = new StoryVisibilityPolicy(this.userFollowRepository);

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

	private static Story story(UUID author, StoryVisibility visibility, Instant publishAt) {
		return new Story(UUID.randomUUID(), author, null, null, "본문", null, visibility, publishAt,
				NOW.minus(2, ChronoUnit.DAYS));
	}
}
