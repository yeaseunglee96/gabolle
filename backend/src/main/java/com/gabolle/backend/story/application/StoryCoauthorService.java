package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.common.security.OpaqueTokens;
import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryCoauthor;
import com.gabolle.backend.story.domain.StoryInvite;
import com.gabolle.backend.story.presentation.dto.AcceptStoryInviteResponse;
import com.gabolle.backend.story.presentation.dto.StoryCoauthorsResponse;
import com.gabolle.backend.story.presentation.dto.StoryInviteResponse;
import com.gabolle.backend.story.repository.StoryCoauthorRepository;
import com.gabolle.backend.story.repository.StoryInviteRepository;
import com.gabolle.backend.trip.domain.TripInvite;
import com.gabolle.backend.trip.domain.TripMembershipRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 기록 공동 작성 — 초대 발급·수락·참여자 관리·여행 동행자 편입.
 *
 * <p>"볼 수 있는가/만든 사람인가" 판정은 여기서 다시 재지 않고
 * {@link StoryService#requireVisible}·{@link StoryVisibilityPolicy#isParticipant} 를 빌려 쓴다.
 */
@Service
@Profile({ "db", "dev" })
@Transactional
public class StoryCoauthorService {

	private final StoryService storyService;

	private final StoryVisibilityPolicy visibilityPolicy;

	private final StoryCoauthorRepository coauthorRepository;

	private final StoryInviteRepository inviteRepository;

	private final TripMembershipRepository tripMembershipRepository;

	private final AppUserRepository appUserRepository;

	private final Clock clock;

	public StoryCoauthorService(StoryService storyService, StoryVisibilityPolicy visibilityPolicy,
			StoryCoauthorRepository coauthorRepository, StoryInviteRepository inviteRepository,
			TripMembershipRepository tripMembershipRepository, AppUserRepository appUserRepository, Clock clock) {
		this.storyService = storyService;
		this.visibilityPolicy = visibilityPolicy;
		this.coauthorRepository = coauthorRepository;
		this.inviteRepository = inviteRepository;
		this.tripMembershipRepository = tripMembershipRepository;
		this.appUserRepository = appUserRepository;
		this.clock = clock;
	}

	/**
	 * 초대 링크를 발급한다. 만든 사람만 부를 수 있다.
	 *
	 * @throws StoryService.StoryNotFoundException 요청자가 그 기록을 볼 수 없다 — 404. 존재를 감춘다.
	 * @throws StoryService.StoryForbiddenException 볼 수는 있지만 만든 사람이 아니다 — 403.
	 */
	public StoryInviteResponse issueInvite(UUID storyId, UUID requester) {
		Instant now = this.clock.instant();
		Story story = this.storyService.requireVisible(storyId, requester, now);
		if (!story.isAuthor(requester)) {
			throw new StoryService.StoryForbiddenException(storyId);
		}

		// 만료까지의 시간은 여행 초대(TripInvite.TTL)와 같게 맞춘다 — 값을 새로 짓지 않는다.
		StoryInvite invite = new StoryInvite(UUID.randomUUID(), storyId, OpaqueTokens.generate(), requester, now,
				now.plus(TripInvite.TTL));
		this.inviteRepository.save(invite);

		return new StoryInviteResponse(invite.getStoryInviteId().toString(), invite.getStoryId().toString(),
				invite.getToken(), invite.getExpiresAt().toString(),
				"/api/v1/story-invites/" + invite.getToken() + "/accept");
	}

	/**
	 * 표(token)로 함께 쓰는 사람이 된다.
	 *
	 * <p>이미 참여 중이면 실패가 아니라 {@code alreadyJoined=true} 성공이다. 동시에 두 번 눌린 경쟁은
	 * {@code saveAndFlush} 가 그 자리에서 기본키 충돌을 터뜨려 걸러내고, 진 쪽도 성공으로 답한다.
	 *
	 * @throws StoryInviteNotFoundException 그런 표가 없다 — 404.
	 * @throws StoryInviteExpiredException 발급 후 7일이 지났다 — 410. 참여자 행은 만들지 않는다.
	 * @throws StoryService.StoryNotFoundException 기록이 지워졌거나 요청자가 볼 수 없다 — 404.
	 */
	public AcceptStoryInviteResponse accept(String token, UUID requester) {
		StoryInvite invite = this.inviteRepository.findByToken(token).orElseThrow(StoryInviteNotFoundException::new);

		Instant now = this.clock.instant();
		if (now.isAfter(invite.getExpiresAt())) {
			throw new StoryInviteExpiredException();
		}

		// 표를 가진 것 자체가 열쇠다. 열람 권한을 먼저 요구하면 나만 보기 기록에 초대받은 사람이
		// 수락하기도 전에 404 를 받는다.
		Story story = this.storyService.requireActiveIgnoringVisibility(invite.getStoryId());

		if (this.visibilityPolicy.isParticipant(story, requester)) {
			return toAcceptResponse(story, requester, true);
		}

		StoryCoauthor coauthor = new StoryCoauthor(story.getStoryId(), requester, invite.getCreatedBy(), now);
		try {
			this.coauthorRepository.saveAndFlush(coauthor);
			return new AcceptStoryInviteResponse(story.getStoryId().toString(), false, now.toString());
		}
		catch (DataIntegrityViolationException e) {
			return toAcceptResponse(story, requester, true);
		}
	}

	private AcceptStoryInviteResponse toAcceptResponse(Story story, UUID requester, boolean alreadyJoined) {
		// 만든 사람은 story_coauthor 행이 없어서 합류 시각이라는 것 자체가 없다 — joinedAt 은 null 이다.
		if (story.isAuthor(requester)) {
			return new AcceptStoryInviteResponse(story.getStoryId().toString(), alreadyJoined, null);
		}
		Instant joinedAt = findJoinedAt(story.getStoryId(), requester);
		return new AcceptStoryInviteResponse(story.getStoryId().toString(), alreadyJoined, joinedAt.toString());
	}

	private Instant findJoinedAt(UUID storyId, UUID userId) {
		return this.coauthorRepository.findByKeyStoryId(storyId).stream()
				.filter(c -> c.getUserId().equals(userId))
				.map(StoryCoauthor::getJoinedAt)
				.findFirst()
				// 방금 넣었거나 이미 있었어야 할 행이라 비어 있으면 그 자체가 버그다.
				.orElseThrow(() -> new IllegalStateException(
						"참여자 행을 찾을 수 없다: storyId=" + storyId + ", userId=" + userId));
	}

	/**
	 * 참여자 목록 — 만든 사람과 공동 작성자를 함께 담는다. 그 기록을 볼 수 있는 사람이면 누구나 부를 수 있다.
	 *
	 * @throws StoryService.StoryNotFoundException 요청자가 그 기록을 볼 수 없다 — 404.
	 */
	@Transactional(readOnly = true)
	public StoryCoauthorsResponse list(UUID storyId, UUID requester) {
		Instant now = this.clock.instant();
		Story story = this.storyService.requireVisible(storyId, requester, now);
		List<StoryCoauthor> coauthors = this.coauthorRepository.findByKeyStoryId(storyId);

		List<UUID> userIds = new ArrayList<>();
		userIds.add(story.getAuthorUserId());
		for (StoryCoauthor coauthor : coauthors) {
			userIds.add(coauthor.getUserId());
		}
		Map<UUID, String> displayNames = this.appUserRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(AppUser::getUserId, AppUser::getDisplayName));

		List<StoryCoauthorsResponse.Coauthor> members = new ArrayList<>();
		// 만든 사람의 joinedAt 자리에는 기록이 만들어진 시각을 넣는다.
		members.add(new StoryCoauthorsResponse.Coauthor(story.getAuthorUserId().toString(),
				displayNames.get(story.getAuthorUserId()), true, story.getCreatedAt().toString()));
		for (StoryCoauthor coauthor : coauthors) {
			members.add(new StoryCoauthorsResponse.Coauthor(coauthor.getUserId().toString(),
					displayNames.get(coauthor.getUserId()), false, coauthor.getJoinedAt().toString()));
		}

		return new StoryCoauthorsResponse(members);
	}

	/**
	 * 여행 동행자를 골라 공동 작성자로 넣는다. 만든 사람만 부를 수 있다.
	 *
	 * <p>넣으려는 사람마다 그 여행의 동행자인지 서버가 검사한다 — 이 검사를 빼면 사용자 검색
	 * 기능이 없는데도 번호(UUID)만 알면 남을 아무 기록에나 끌어들일 수 있다. 검사는 삽입 전에
	 * 전부 먼저 끝낸다 — 목록 중 하나라도 동행자가 아니면 요청 전체를 거부한다(일부만 넣고 나머지를
	 * 자르는 것은 "누가 왜 안 들어갔는지" 를 호출한 쪽이 알 수 없게 만든다).
	 *
	 * @throws StoryService.StoryNotFoundException 요청자가 그 기록을 볼 수 없다 — 404.
	 * @throws StoryService.StoryForbiddenException 볼 수는 있지만 만든 사람이 아니다 — 403.
	 * @throws StoryHasNoTripException 그 기록에 여행이 붙어 있지 않다 — 400.
	 * @throws NotTripMemberException 목록의 누군가가 그 여행의 동행자가 아니다 — 400.
	 */
	public void addTripMembers(UUID storyId, UUID requester, List<UUID> userIds) {
		Instant now = this.clock.instant();
		Story story = this.storyService.requireVisible(storyId, requester, now);
		if (!story.isAuthor(requester)) {
			throw new StoryService.StoryForbiddenException(storyId);
		}
		if (story.getTripId() == null) {
			throw new StoryHasNoTripException(storyId);
		}
		String tripId = story.getTripId().toString();

		List<UUID> toAdd = new ArrayList<>();
		for (UUID userId : userIds) {
			if (userId.equals(requester)) {
				continue;
			}
			if (this.coauthorRepository.existsByKey(new StoryCoauthor.Key(storyId, userId))) {
				continue;
			}
			if (this.tripMembershipRepository.findMember(tripId, userId.toString()).isEmpty()) {
				throw new NotTripMemberException(userId);
			}
			toAdd.add(userId);
		}

		for (UUID userId : toAdd) {
			this.coauthorRepository.save(new StoryCoauthor(storyId, userId, requester, now));
		}
	}

	/**
	 * 참여자를 뺀다. 만든 사람이 남을 빼거나, 공동 작성자가 자기 자신을 뺀다(나가기).
	 *
	 * <p>만든 사람은 자기를 뺄 수 없다 — 빼면 이 기록을 지울 수 있는 사람이 아무도 없어진다.
	 *
	 * @throws StoryService.StoryNotFoundException 요청자가 그 기록을 볼 수 없다 — 404.
	 * @throws StoryService.StoryForbiddenException 만든 사람이 남을 빼려는 것도, 자기 자신을 빼려는
	 * 것도 아니다(공동 작성자가 남을 빼려는 경우 포함) — 403. 만든 사람이 자기를 빼려는 경우도 403.
	 */
	public void remove(UUID storyId, UUID requester, UUID target) {
		Instant now = this.clock.instant();
		Story story = this.storyService.requireVisible(storyId, requester, now);

		boolean requesterIsAuthor = story.isAuthor(requester);
		boolean selfRemoval = requester.equals(target);
		if (!requesterIsAuthor && !selfRemoval) {
			throw new StoryService.StoryForbiddenException(storyId);
		}
		if (story.isAuthor(target)) {
			throw new StoryService.StoryForbiddenException(storyId);
		}

		// 참여자가 아닌 사람을 빼려 하면(행이 없으면) 조용히 넘어간다 — 이미 없는 상태가 원하는 상태다.
		this.coauthorRepository.deleteByKey(new StoryCoauthor.Key(storyId, target));
	}

	// ---- 예외 ----

	/** 표(token)가 존재하지 않는다. */
	public static class StoryInviteNotFoundException extends RuntimeException {
		public StoryInviteNotFoundException() {
			super("초대를 찾을 수 없습니다.");
		}
	}

	/** 표는 있지만 발급 후 7일이 지났다 — 행은 그대로 둔다({@code StoryInvite} 문서 참고). */
	public static class StoryInviteExpiredException extends RuntimeException {
		public StoryInviteExpiredException() {
			super("초대가 만료됐어요.");
		}
	}

	/** 그 기록에 여행이 붙어 있지 않다 — "여행 동행자" 라는 개념 자체가 성립하지 않는다. */
	public static class StoryHasNoTripException extends RuntimeException {
		public StoryHasNoTripException(UUID storyId) {
			super("여행이 연결되지 않은 기록에는 여행 동행자를 넣을 수 없습니다.");
		}
	}

	/** 넣으려는 사람이 그 기록에 연결된 여행의 동행자가 아니다. */
	public static class NotTripMemberException extends RuntimeException {
		public NotTripMemberException(UUID userId) {
			super("그 여행의 동행자가 아닙니다: " + userId);
		}
	}
}
