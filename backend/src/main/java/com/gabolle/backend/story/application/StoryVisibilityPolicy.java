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
 * 기록(Story) 한 건을 특정 사람이 볼 수 있는지 판정한다 — S15P21E201-254 뽑아내기.
 *
 * <h2>왜 이 판정을 한 곳에 모아야 하나</h2>
 * {@code StoryService}(기록 상세·수정·삭제)와 {@code StoryReportService}(신고 접수) 둘 다 같은 질문을
 * 던진다 — "이 사람이 이 기록을 볼 수 있는가". 볼 수 없으면 둘 다 <b>404</b> 로 답해 존재 자체를 감춰야
 * 한다. 신고 쪽에서 이 판정이 없으면, 사적인 기록의 UUID 를 우연히 알게 된 사람이 신고를 넣어 보고
 * 응답이 200 인지 404 인지만으로 그 기록이 실제로 존재하는지 알아낼 수 있다 — 신고 기능이 열람 권한을
 * 우회하는 샛길이 되는 것이다. 그래서 두 서비스는 같은 규칙을, 같은 코드로 써야 한다.
 *
 * <p>🔴 예전에는 이 로직이 {@code StoryService} 안에 있었고 {@code StoryReportService} 가 그대로
 * 복사해 갖고 있었다(두 클래스가 각자 사용 가능한 자리에 있지 않아서였다). 두 사본이 갈라질 위험이
 * 실제로 있었기 때문에 이 클래스로 뽑아 왔다.
 *
 * <h2>왜 {@code moderation} 이 아니라 {@code story} 패키지인가</h2>
 * {@code moderation} 은 이미 {@code story.repository.StoryRepository} 와
 * {@code story.repository.UserFollowRepository} 를 직접 참조하고 있다 — 즉 이 저장소의 기존 의존
 * 방향은 {@code moderation → story} 다. 이 클래스를 {@code story} 에 두면 {@code moderation} 이
 * 지금처럼 {@code story} 를 참조하는 방향 그대로 가져다 쓸 수 있고, {@code story} 가 {@code moderation}
 * 을 거꾸로 참조하게 되는 일이 생기지 않는다.
 *
 * <h2>규칙</h2>
 * <ul>
 *   <li>작성자는 언제나 자기 기록을 본다 — 공개 전이든, 나만 보기든</li>
 *   <li>작성자가 아니면 <b>공개 시각이 지난</b> 기록만 대상이 되고, 그중에서도 PUBLIC 이거나
 *       (FOLLOWERS 이고 그 사람을 팔로우할 때)만 본다. PRIVATE 은 작성자 외엔 아무도 못 본다</li>
 * </ul>
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
	 * 이 사람이 이 기록을 함께 쓰는가 — 만든 사람이거나 초대받아 들어온 사람 — S15P21E201-770.
	 *
	 * <p>열람과 수정이 <b>같은 명단</b>을 본다. 여기서 갈라 두면 "고칠 수는 있는데 볼 수는 없는"
	 * 사람이 생길 수 있고, 그건 어느 쪽이 버그인지 아무도 모르는 상태다.
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
	 * @param viewer 로그인한 사람. <b>{@code null} 이면 로그인하지 않은 사람</b>이다 —
	 *     익명 출입증만 들고 온 경우 (S15P21E201-995)
	 */
	public boolean canView(Story story, UUID viewer, Instant now) {
		// 🔴 S15P21E201-995 — 로그인하지 않은 사람은 공개된 PUBLIC 만 본다. 여기서 먼저 끊는다.
		//
		//    아래 길로 흘려보내도 "대체로" 같은 답이 나온다 — isParticipant 는 null 에 false 를
		//    주고 PRIVATE 는 어차피 false 다. 그런데 FOLLOWERS 가지는 다르다. 거기서
		//    userFollowRepository.existsByKey(new UserFollow.Key(null, …)) 를 부르게 되는데,
		//    복합 키의 한 칸이 null 인 조회는 **동작이 보장되지 않는다** — 예외가 날 수도,
		//    조용히 false 가 될 수도 있다.
		//
		//    보안 판정을 그런 우연에 맡기지 않는다. 새는 쪽이 조용한 종류의 사고라, 규칙을
		//    코드에 드러내 둔다. S15P21E201-974 가 피드 질의를 따로 둔 것과 같은 이유다.
		if (viewer == null) {
			return story.isPublishedAt(now) && story.getVisibility() == StoryVisibility.PUBLIC;
		}
		// 참여자는 공개 시각 전이든 나만 보기든 언제나 본다. 자기가 함께 쓰는 글을 못 보면
		// 고칠 수도 없다 — 수정 경로가 먼저 상세 조회를 지나기 때문이다.
		if (isParticipant(story, viewer)) {
			return true;
		}
		// 🔴 여기에 검토 상태를 넣지 않는다 — S15P21E201-137 에서 한 번 넣었다가 되돌렸다.
		//
		// 이 판정은 "공개 범위 규칙상 이 사람이 볼 수 있는가" 만 답한다. 신고 접수 경로가
		// 이것을 그대로 쓰는데(StoryReportService.requireReportable), 그쪽은 검토 중인 기록도
		// 받아야 한다. 두 번째 신고자가 404 를 받으면 신고 수가 1로 멈추고, 그 숫자가 운영자가
		// 무엇을 먼저 볼지 정하는 근거라 판단이 흐려진다. 실제로 그 검사 둘이 빨개져서 알았다.
		//
		// 감춰진 기록을 남에게 안 보이게 하는 것은 한 단계 위, StoryService.requireVisible 이
		// 한다. 거기는 참여자를 이미 가려낸 뒤라 작성자의 삭제 경로를 막지 않는다.
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
