package com.gabolle.backend.story.application;

import java.time.Clock;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.UserFollow;
import com.gabolle.backend.story.presentation.dto.FollowResponse;
import com.gabolle.backend.story.presentation.dto.UserProfileResponse;
import com.gabolle.backend.story.repository.StoryRepository;
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

	private final AppUserRepository appUserRepository;

	private final StoryRepository storyRepository;

	private final StoryService storyService;

	private final Clock clock;

	public FollowService(UserFollowRepository userFollowRepository, AppUserRepository appUserRepository,
			StoryRepository storyRepository, StoryService storyService, Clock clock) {
		this.userFollowRepository = userFollowRepository;
		this.appUserRepository = appUserRepository;
		this.storyRepository = storyRepository;
		this.storyService = storyService;
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

	@Transactional(readOnly = true)
	public UserProfileResponse profile(UUID viewer, UUID target) {
		AppUser user = requireActiveUser(target);
		boolean me = viewer.equals(target);
		boolean following = !me && this.userFollowRepository.existsByKey(new UserFollow.Key(viewer, target));
		long stories = this.storyRepository.countAuthorStories(target, this.storyService.visibleScopesOf(target, viewer),
				this.clock.instant());
		return new UserProfileResponse(target.toString(), user.getDisplayName(),
				this.userFollowRepository.countByKeyFolloweeUserId(target),
				this.userFollowRepository.countByKeyFollowerUserId(target), stories, following, me,
				user.getAvatarUrl());
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
