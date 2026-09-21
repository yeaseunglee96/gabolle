package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.UserFollow;
import com.gabolle.backend.story.presentation.dto.FollowResponse;
import com.gabolle.backend.story.presentation.dto.RelationListResponse;
import com.gabolle.backend.story.presentation.dto.UserProfileResponse;
import com.gabolle.backend.story.repository.RelationRow;
import com.gabolle.backend.story.repository.StoryRepository;
import com.gabolle.backend.story.repository.UserBlockRepository;
import com.gabolle.backend.story.repository.UserFollowRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 팔로우·해제·프로필.
 *
 * <p>팔로우는 멱등이다. 이미 팔로우한 사람을 다시 팔로우해도 오류가 아니라 "이미 된 상태" 로 답한다.
 * 두 줄이 생기지 않는 것은 {@code user_follow} 의 기본키가 보장하고, 동시에 온 두 요청 중 두 번째
 * INSERT 가 그 키에 막히는 경우도 성공으로 답한다.
 */
@Service
@Profile({ "db", "dev" })
public class FollowService {

	private final UserFollowRepository userFollowRepository;

	private final UserBlockRepository userBlockRepository;

	private final AppUserRepository appUserRepository;

	private final StoryRepository storyRepository;

	private final StoryService storyService;

	private final BlockService blockService;

	private final Clock clock;

	public FollowService(UserFollowRepository userFollowRepository, UserBlockRepository userBlockRepository,
			AppUserRepository appUserRepository, StoryRepository storyRepository, StoryService storyService,
			BlockService blockService, Clock clock) {
		this.userFollowRepository = userFollowRepository;
		this.userBlockRepository = userBlockRepository;
		this.appUserRepository = appUserRepository;
		this.storyRepository = storyRepository;
		this.storyService = storyService;
		this.blockService = blockService;
		this.clock = clock;
	}

	@Transactional
	public FollowResponse follow(UUID me, UUID target) {
		if (me.equals(target)) {
			throw new UserFollow.SelfFollowException(me);
		}
		requireActiveUser(target);
		UserFollow.Key key = new UserFollow.Key(me, target);
		if (!this.userFollowRepository.existsByKey(key)) {
			try {
				this.userFollowRepository.saveAndFlush(new UserFollow(me, target, this.clock.instant()));
			}
			catch (DataIntegrityViolationException raced) {
				// 같은 순간 다른 요청이 먼저 넣었다. 결과는 같다 — 팔로우한 상태.
			}
		}
		return status(me, target, true);
	}

	@Transactional
	public FollowResponse unfollow(UUID me, UUID target) {
		if (me.equals(target)) {
			throw new UserFollow.SelfFollowException(me);
		}
		requireActiveUser(target);
		UserFollow.Key key = new UserFollow.Key(me, target);
		if (this.userFollowRepository.existsById(key)) {
			this.userFollowRepository.deleteById(key);
		}
		return status(me, target, false);
	}

	/**
	 * 프로필 머리.
	 *
	 * <p>차단당한 경우에도 404 를 내지 않는다. 화면이 「차단되어 볼 수 없습니다」를 띄우려면 그 사람이
	 * 있다는 것까지는 와야 한다. 대신 속을 비워 보낸다 — 팔로워·팔로잉·기록 수가 전부 0 이다. 숫자를
	 * 그대로 실어 보내면 화면이 가려도 응답에는 남는다.
	 */
	@Transactional(readOnly = true)
	public UserProfileResponse profile(UUID viewer, UUID target) {
		AppUser user = requireActiveUser(target);
		// 🔴 viewer 가 null 일 수 있다 — 로그인하지 않은 사람이 작성자 이름을 누른 경우다
		//    (S15P21E201-1373). 그 사람과는 «관계가 없다» — 나도 아니고, 차단도 팔로우도 없다.
		boolean me = viewer != null && viewer.equals(target);
		boolean blockedByUser = viewer != null && !me && this.userBlockRepository.isBlockedBy(target, viewer);
		if (blockedByUser) {
			return new UserProfileResponse(target.toString(), user.getDisplayName(), 0L, 0L, 0L, false, false,
					user.getAvatarUrl(), this.userBlockRepository.hasBlocked(viewer, target), true);
		}
		boolean following = viewer != null && !me
				&& this.userFollowRepository.existsByKey(new UserFollow.Key(viewer, target));
		boolean blocked = viewer != null && !me && this.userBlockRepository.hasBlocked(viewer, target);
		long stories = this.storyRepository.countAuthorStories(target, this.storyService.visibleScopesOf(target, viewer),
				this.clock.instant());
		return new UserProfileResponse(target.toString(), user.getDisplayName(),
				this.userFollowRepository.countByKeyFolloweeUserId(target),
				this.userFollowRepository.countByKeyFollowerUserId(target), stories, following, me,
				user.getAvatarUrl(), blocked, false);
	}

	/**
	 * 팔로워 목록 — 이 사람을 팔로우하는 사람들. 그 사람이 나를 차단했으면 빈 목록이 아니라 403 이다.
	 * 한 사람을 지목해 여는 목록이라 속을 비워 답하는 프로필 머리와 다르게 취급한다.
	 */
	@Transactional(readOnly = true)
	public RelationListResponse followers(UUID viewer, UUID target, String cursor, Integer limit) {
		this.blockService.requireNotBlockedBy(target, viewer);
		requireActiveUser(target);
		RelationCursor from = RelationCursor.decode(cursor);
		int size = StoryFeedService.clamp(limit);
		List<RelationRow> rows = this.userFollowRepository.findFollowers(target, from.relatedAt(), from.userId(), size + 1);
		Set<UUID> follows = viewerFollowsAmong(viewer, rows);
		return RelationCursor.pageWithStoryCounts(rows, size, follows, storyCountsFor(viewer, rows, follows));
	}

	/** 팔로잉 목록 — 이 사람이 팔로우하는 사람들. {@link #followers} 와 같은 차단 규칙을 쓴다. */
	@Transactional(readOnly = true)
	public RelationListResponse following(UUID viewer, UUID target, String cursor, Integer limit) {
		this.blockService.requireNotBlockedBy(target, viewer);
		requireActiveUser(target);
		RelationCursor from = RelationCursor.decode(cursor);
		int size = StoryFeedService.clamp(limit);
		List<RelationRow> rows = this.userFollowRepository.findFollowing(target, from.relatedAt(), from.userId(), size + 1);
		Set<UUID> follows = viewerFollowsAmong(viewer, rows);
		return RelationCursor.pageWithStoryCounts(rows, size, follows, storyCountsFor(viewer, rows, follows));
	}

	/**
	 * 이 페이지에 나온 사람들 중, 보는 사람이 팔로우하는 사람의 식별자. 빈 목록이면 질의를 아예 안
	 * 보낸다 — 네이티브 {@code IN ()} 은 파라미터가 없으면 PostgreSQL 구문 오류를 낸다.
	 */
	private Set<UUID> viewerFollowsAmong(UUID viewer, List<RelationRow> rows) {
		if (rows.isEmpty()) {
			return Set.of();
		}
		List<UUID> candidates = rows.stream().map(RelationRow::getUserId).toList();
		return new HashSet<>(this.userFollowRepository.findFollowedAmong(viewer, candidates));
	}

	/**
	 * 이 쪽에 나온 사람들이 각각 기록을 몇 개 썼나.
	 *
	 * <p>보이는 범위가 셋뿐이라 같은 범위끼리 묶어 최대 세 번의 질의로 끝낸다
	 * ({@link StoryCountBuckets}). 기록이 하나도 없는 사람은 {@code GROUP BY} 결과에 안
	 * 들어오므로 여기서 0 으로 채운다.
	 */
	private Map<UUID, Long> storyCountsFor(UUID viewer, List<RelationRow> rows, Set<UUID> viewerFollows) {
		if (rows.isEmpty()) {
			return Map.of();
		}
		List<UUID> userIds = rows.stream().map(RelationRow::getUserId).toList();
		StoryCountBuckets buckets = StoryCountBuckets.of(viewer, userIds, viewerFollows);
		Instant now = this.clock.instant();
		Map<UUID, Long> counts = new HashMap<>();
		userIds.forEach(userId -> counts.put(userId, 0L));
		tally(counts, buckets.self(), StoryService.SELF_SCOPES, now);
		tally(counts, buckets.followed(), StoryService.FOLLOWER_SCOPES, now);
		tally(counts, buckets.strangers(), StoryService.STRANGER_SCOPES, now);
		return counts;
	}

	/** 빈 칸이면 질의를 아예 안 보낸다 — 네이티브 {@code IN ()} 은 값이 없으면 구문 오류다. */
	private void tally(Map<UUID, Long> counts, List<UUID> authors, List<String> visibilities, Instant now) {
		if (authors.isEmpty()) {
			return;
		}
		for (Object[] row : this.storyRepository.countAuthorStoriesGrouped(authors, visibilities, now)) {
			counts.put((UUID) row[0], ((Number) row[1]).longValue());
		}
	}

	private FollowResponse status(UUID me, UUID target, boolean following) {
		return new FollowResponse(target.toString(), following,
				this.userFollowRepository.countByKeyFolloweeUserId(target),
				this.userFollowRepository.countByKeyFollowerUserId(target));
	}

	private AppUser requireActiveUser(UUID userId) {
		AppUser user = this.appUserRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
		if (user.getDeletedAt() != null) {
			throw new UserNotFoundException(userId);
		}
		return user;
	}

	/** 없는 사용자, 탈퇴한 사용자 — 404. */
	public static class UserNotFoundException extends RuntimeException {

		public UserNotFoundException(UUID userId) {
			super("사용자를 찾을 수 없습니다.");
		}
	}
}
