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
 * 팔로우·해제·프로필 — S15P21E201-242 · -126.
 *
 * <p>🔴 팔로우는 <b>멱등</b>이다. 이미 팔로우한 사람을 다시 팔로우해도 오류가 아니라 "이미 된 상태" 로
 * 답한다. 두 줄이 생기지 않는 것은 {@code user_follow} 의 기본키(두 사람의 쌍)가 보장하고, 두 요청이
 * 정확히 동시에 와서 둘 다 "없다" 를 읽은 경우에는 두 번째 INSERT 가 그 키에 막힌다 — 그것도 성공으로
 * 답한다. 화면에서 빠르게 두 번 눌러도 팔로워 수가 틀리지 않는 이유가 이 둘이다.
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
	 * <p>🔴 <b>차단당한 경우에도 404 를 내지 않는다</b> (S15P21E201-990). 팀이 「없는 사람인 척하지
	 * 않기로」 정했고, 화면이 「차단되어 볼 수 없습니다」를 띄우려면 그 사람이 있다는 것까지는
	 * 와야 한다. 대신 <b>속을 비워서</b> 보낸다 — 팔로워·팔로잉·기록 수는 전부 0 이다. 숫자를
	 * 그대로 실어 보내면 화면이 가려도 응답에는 남아 있고, 그건 가린 것이 아니다.
	 */
	@Transactional(readOnly = true)
	public UserProfileResponse profile(UUID viewer, UUID target) {
		AppUser user = requireActiveUser(target);
		boolean me = viewer.equals(target);
		boolean blockedByUser = !me && this.userBlockRepository.isBlockedBy(target, viewer);
		if (blockedByUser) {
			return new UserProfileResponse(target.toString(), user.getDisplayName(), 0L, 0L, 0L, false, false,
					user.getAvatarUrl(), this.userBlockRepository.hasBlocked(viewer, target), true);
		}
		boolean following = !me && this.userFollowRepository.existsByKey(new UserFollow.Key(viewer, target));
		boolean blocked = !me && this.userBlockRepository.hasBlocked(viewer, target);
		long stories = this.storyRepository.countAuthorStories(target, this.storyService.visibleScopesOf(target, viewer),
				this.clock.instant());
		return new UserProfileResponse(target.toString(), user.getDisplayName(),
				this.userFollowRepository.countByKeyFolloweeUserId(target),
				this.userFollowRepository.countByKeyFollowerUserId(target), stories, following, me,
				user.getAvatarUrl(), blocked, false);
	}

	/**
	 * 팔로워 목록 — 이 사람을 팔로우하는 사람들. S15P21E201-1179.
	 *
	 * <p>🔴 {@code stories()} 와 같은 이유로, 그 사람이 나를 차단했으면 빈 목록이 아니라 403 이다
	 * ({@code requireNotBlockedBy} 참고) — 한 사람을 지목해 여는 목록이라 프로필 머리(zeroed 로
	 * 답하는 쪽)와 다르게 취급한다.
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
	 * 이 페이지에 나온 사람들 중, 보는 사람이 팔로우하는 사람의 식별자 — S15P21E201-1179.
	 *
	 * <p>빈 목록이면 질의를 아예 안 보낸다 — 네이티브 {@code IN ()} 은 파라미터가 없으면
	 * PostgreSQL 구문 오류를 낸다.
	 */
	private Set<UUID> viewerFollowsAmong(UUID viewer, List<RelationRow> rows) {
		if (rows.isEmpty()) {
			return Set.of();
		}
		List<UUID> candidates = rows.stream().map(RelationRow::getUserId).toList();
		return new HashSet<>(this.userFollowRepository.findFollowedAmong(viewer, candidates));
	}

	/**
	 * 이 쪽에 나온 사람들이 각각 기록을 몇 개 썼나 — S15P21E201-1317.
	 *
	 * <p>🔴 <b>한 명씩 세지 않는다.</b> 목록 스무 줄이면 질의가 스무 개 나가는데, 그 값은 줄 옆의
	 * 작은 글씨 하나다. 보이는 범위가 사람마다 다른 것이 한 명씩 세게 만드는 이유인데,
	 * <b>범위는 셋뿐</b>이라 같은 범위끼리 묶으면 쪽 크기와 무관하게 최대 세 번이면 끝난다
	 * ({@link StoryCountBuckets}).
	 *
	 * <p>🔴 <b>안 나온 사람은 0 이다.</b> 기록이 하나도 없는 사람은 {@code GROUP BY} 결과에 아예
	 * 안 들어온다 — 그것을 「모른다」로 두면 화면에 빈 자리가 생긴다. 여기서 0 으로 채운다.
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
