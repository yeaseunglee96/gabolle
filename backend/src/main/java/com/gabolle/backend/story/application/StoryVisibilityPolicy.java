package com.gabolle.backend.story.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryCoauthor;
import com.gabolle.backend.story.domain.StoryVisibility;
import com.gabolle.backend.story.domain.UserFollow;
import com.gabolle.backend.story.repository.StoryCoauthorRepository;
import com.gabolle.backend.story.repository.UserFollowRepository;

/**
 * 기록 한 건을 특정 사람이 볼 수 있는지 판정한다.
 *
 * <p>{@code StoryService}(상세·수정·삭제)와 {@code StoryReportService}(신고 접수)가 같은 질문을 던지고
 * 둘 다 404 로 답해야 하므로 규칙을 여기 한 곳에만 둔다. 신고 쪽에 이 판정이 없으면 기록의 UUID 를
 * 아는 사람이 신고를 넣어 보고 200 이냐 404 냐로 존재를 알아낼 수 있다.
 *
 * <p>{@code moderation} 이 아니라 {@code story} 패키지에 있다. 이 저장소의 의존 방향이
 * {@code moderation → story} 라, 반대로 두면 {@code story} 가 {@code moderation} 을 참조하게 된다.
 *
 * <p>규칙은 둘이다. 작성자는 공개 전이든 나만 보기든 언제나 본다. 작성자가 아니면 공개 시각이 지난
 * 기록 중 PUBLIC 이거나 (FOLLOWERS 이고 그 사람을 팔로우할 때)만 보고, PRIVATE 은 아무도 못 본다.
 */
@Component
@Profile({ "db", "dev" })
public class StoryVisibilityPolicy {

	private final UserFollowRepository userFollowRepository;

	private final StoryCoauthorRepository coauthorRepository;

	public StoryVisibilityPolicy(UserFollowRepository userFollowRepository,
			StoryCoauthorRepository coauthorRepository) {
		this.userFollowRepository = userFollowRepository;
		this.coauthorRepository = coauthorRepository;
	}

	/**
	 * 이 사람이 이 기록을 함께 쓰는가 — 만든 사람이거나 초대받아 들어온 사람. 열람과 수정이 같은
	 * 명단을 본다. 갈라 두면 "고칠 수는 있는데 볼 수는 없는" 사람이 생긴다.
	 */
	public boolean isParticipant(Story story, UUID user) {
		if (user == null) {
			return false;
		}
		if (story.isAuthor(user)) {
			return true;
		}
		return this.coauthorRepository.existsByKey(new StoryCoauthor.Key(story.getStoryId(), user));
	}

	/**
	 * @param viewer 로그인한 사람. {@code null} 이면 로그인하지 않은 사람이다
	 */
	public boolean canView(Story story, UUID viewer, Instant now) {
		// 로그인하지 않은 사람은 공개된 PUBLIC 만 본다. 여기서 먼저 끊는다 — 아래로 흘려보내면
		// FOLLOWERS 가지에서 복합 키의 한 칸이 null 인 조회를 하게 되고, 그 조회는 동작이
		// 보장되지 않는다. 보안 판정을 그런 우연에 맡기지 않는다.
		if (viewer == null) {
			return story.isPublishedAt(now) && story.getVisibility() == StoryVisibility.PUBLIC;
		}
		// 참여자는 공개 시각 전이든 나만 보기든 언제나 본다. 자기가 함께 쓰는 글을 못 보면
		// 고칠 수도 없다 — 수정 경로가 먼저 상세 조회를 지나기 때문이다.
		if (isParticipant(story, viewer)) {
			return true;
		}
		// 여기에 검토 상태를 넣지 않는다. 이 판정은 "공개 범위 규칙상 볼 수 있는가" 만 답한다.
		// 신고 접수가 이것을 그대로 쓰는데, 그쪽은 검토 중인 기록도 받아야 두 번째 신고자가
		// 세어진다. 감춰진 기록을 남에게 안 보이게 하는 것은 StoryService.requireVisible 이 한다.
		if (!story.isPublishedAt(now)) {
			return false;
		}
		return switch (story.getVisibility()) {
			case PUBLIC -> true;
			case FOLLOWERS -> this.userFollowRepository.existsByKey(new UserFollow.Key(viewer, story.getAuthorUserId()));
			case PRIVATE -> false;
		};
	}
}
