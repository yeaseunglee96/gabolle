package com.gabolle.backend.story.application;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.UserBlock;
import com.gabolle.backend.story.domain.UserFollow;
import com.gabolle.backend.story.presentation.dto.BlockResponse;
import com.gabolle.backend.story.presentation.dto.RelationListResponse;
import com.gabolle.backend.story.repository.UserBlockRepository;
import com.gabolle.backend.story.repository.UserFollowRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 차단·해제.
 *
 * <p>A 가 B 를 차단하면 B 가 A 의 기록·프로필을 못 보고, A 의 피드·댓글 목록에서도 B 의 기록·댓글이 빠진다
 * (S15P21E201-1714 — App Store 가이드라인 1.2). A 가 B 의 프로필을 직접 여는 것은 막지 않는다 — 차단을 풀 수 있어야 한다.
 *
 * <p>차단은 멱등이다. 이미 차단한 사람을 다시 차단해도 오류가 아니라 "이미 된 상태" 로 답한다.
 * 두 줄이 생기지 않는 것은 {@code user_block} 의 기본키가 보장한다.
 */
@Service
@Profile({ "db", "dev" })
public class BlockService {

	private final UserBlockRepository userBlockRepository;

	private final UserFollowRepository userFollowRepository;

	private final AppUserRepository appUserRepository;

	private final Clock clock;

	public BlockService(UserBlockRepository userBlockRepository, UserFollowRepository userFollowRepository,
			AppUserRepository appUserRepository, Clock clock) {
		this.userBlockRepository = userBlockRepository;
		this.userFollowRepository = userFollowRepository;
		this.appUserRepository = appUserRepository;
		this.clock = clock;
	}

	/**
	 * 차단한다. 팔로우 관계를 양쪽 다 끊고, 차단을 풀어도 복구하지 않는다 — 복구하려면 "차단 전에
	 * 팔로우했었나" 를 기억해야 하는데 그 기록이 차단의 뜻과 어긋난다. 화면이 누르기 전에 이 사실을
	 * 알려야 한다.
	 */
	@Transactional
	public BlockResponse block(UUID me, UUID target) {
		if (me.equals(target)) {
			throw new UserBlock.SelfBlockException(me);
		}
		requireActiveUser(target);
		UserBlock.Key key = new UserBlock.Key(me, target);
		if (!this.userBlockRepository.existsByKey(key)) {
			try {
				this.userBlockRepository.saveAndFlush(new UserBlock(me, target, this.clock.instant()));
			}
			catch (DataIntegrityViolationException raced) {
				// 같은 순간 다른 요청이 먼저 넣었다. 결과는 같다 — 차단한 상태.
			}
		}
		unfollowBothWays(me, target);
		return new BlockResponse(target.toString(), true);
	}

	/** 차단을 푼다. 팔로우는 복구하지 않는다 — {@link #block} 의 설명 참고. */
	@Transactional
	public BlockResponse unblock(UUID me, UUID target) {
		if (me.equals(target)) {
			throw new UserBlock.SelfBlockException(me);
		}
		requireActiveUser(target);
		UserBlock.Key key = new UserBlock.Key(me, target);
		if (this.userBlockRepository.existsById(key)) {
			this.userBlockRepository.deleteById(key);
		}
		return new BlockResponse(target.toString(), false);
	}

	/**
	 * 이 사람이 나를 차단했으면 막는다 — 프로필·상세처럼 한 사람을 지목해 여는 경로가 쓴다.
	 *
	 * <p>피드 목록은 이 메서드를 쓰지 않는다. 거기서는 차단된 글이 조용히 빠지는 것이 맞다 —
	 * 목록에서 예외를 던지면 그 사람 글 하나 때문에 피드 전체가 실패한다.
	 */
	@Transactional(readOnly = true)
	public void requireNotBlockedBy(UUID author, UUID viewer) {
		if (viewer == null || author.equals(viewer)) {
			return;
		}
		if (this.userBlockRepository.isBlockedBy(author, viewer)) {
			throw new UserBlock.BlockedByUserException(author);
		}
	}

	/** 내가 이 사람을 차단했나 — 버튼 상태를 정한다. */
	@Transactional(readOnly = true)
	public boolean hasBlocked(UUID me, UUID target) {
		return me != null && !me.equals(target) && this.userBlockRepository.hasBlocked(me, target);
	}

	/** 이 사람이 나를 차단했나 — 「차단되어 볼 수 없습니다」를 띄울지 정한다. */
	@Transactional(readOnly = true)
	public boolean isBlockedBy(UUID target, UUID me) {
		return me != null && !me.equals(target) && this.userBlockRepository.isBlockedBy(target, me);
	}

	/**
	 * 내가 차단한 사람 목록. 팔로워·팔로잉과 달리 남의 것은 볼 수 없다 — 팔로우는 공개 관계지만
	 * 차단은 차단한 사람의 판단이 새어 나가면 안 되는 정보다.
	 */
	@Transactional(readOnly = true)
	public RelationListResponse myBlocks(UUID viewer, UUID me, String cursor, Integer limit) {
		if (!viewer.equals(me)) {
			throw new BlockListForbiddenException();
		}
		RelationCursor from = RelationCursor.decode(cursor);
		int size = StoryFeedService.clamp(limit);
		// following 은 물어볼 필요가 없다. block() 이 팔로우를 양쪽 다 끊으므로, 차단한 사람이
		// 목록에 있다는 것 자체가 이미 "팔로우 아님" 을 보장한다. 그래서 빈 집합을 준다.
		return RelationCursor.page(this.userBlockRepository.findBlocked(me, from.relatedAt(), from.userId(), size + 1),
				size, Set.of());
	}

	/** 남의 차단 목록을 물었다 — 403 으로 답할 자리다. */
	public static class BlockListForbiddenException extends RuntimeException {

		public BlockListForbiddenException() {
			super("차단 목록은 본인만 볼 수 있습니다.");
		}
	}

	private void unfollowBothWays(UUID me, UUID target) {
		deleteFollowIfPresent(new UserFollow.Key(me, target));
		deleteFollowIfPresent(new UserFollow.Key(target, me));
	}

	private void deleteFollowIfPresent(UserFollow.Key key) {
		if (this.userFollowRepository.existsById(key)) {
			this.userFollowRepository.deleteById(key);
		}
	}

	private AppUser requireActiveUser(UUID userId) {
		AppUser user = this.appUserRepository.findById(userId)
				.orElseThrow(() -> new FollowService.UserNotFoundException(userId));
		if (user.getDeletedAt() != null) {
			throw new FollowService.UserNotFoundException(userId);
		}
		return user;
	}
}
