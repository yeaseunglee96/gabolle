package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryCoauthor;
import com.gabolle.backend.story.presentation.dto.MyRepliesResponse;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.repository.StoryCoauthorRepository;
import com.gabolle.backend.story.repository.StoryRepository;
import com.gabolle.backend.story.repository.UserBlockRepository;
import com.gabolle.backend.story.repository.UserFollowRepository;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 내 댓글 목록 (S15P21E201-1600).
 *
 * <p>🔴 <b>왜.</b> 원글을 지워도 댓글은 남는다(남의 글을 같이 지우지 않으려고 — {@link StoryService#delete}). 그런데
 * 피드·프로필 목록은 전부 원글만 싣고, 원글이 지워지면 그 밑 댓글 목록도 404 라 쓴 사람이 자기 댓글을 찾을 길이
 * 없었다. 운영(2026-09-25)에서 살아 있는 댓글 15개 중 11개가 그랬다.
 *
 * <p>바로 위 글의 상태({@code parent.state})는 {@link StoryVisibilityPolicy#canView} 와 같은 규칙을 한 쪽에 한 번씩
 * 읽은 자료로 판정한다 — 글마다 물으면 한 쪽 20건이 수십 번 왕복한다. 작성자가 나를 차단했으면 피드와 같이 가린다.
 */
@Service
@Profile({ "db", "dev" })
public class MyRepliesService {

	/** 원글 미리보기 글자 수. 앱과 맞춘 값이다. */
	static final int PREVIEW_LENGTH = 60;

	private final StoryRepository storyRepository;

	private final StoryResponseAssembler assembler;

	private final StoryCoauthorRepository coauthorRepository;

	private final UserFollowRepository followRepository;

	private final UserBlockRepository blockRepository;

	private final AppUserRepository appUserRepository;

	private final Clock clock;

	public MyRepliesService(StoryRepository storyRepository, StoryResponseAssembler assembler,
			StoryCoauthorRepository coauthorRepository, UserFollowRepository followRepository,
			UserBlockRepository blockRepository, AppUserRepository appUserRepository, Clock clock) {
		this.storyRepository = storyRepository;
		this.assembler = assembler;
		this.coauthorRepository = coauthorRepository;
		this.followRepository = followRepository;
		this.blockRepository = blockRepository;
		this.appUserRepository = appUserRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public MyRepliesResponse list(UUID me, String cursor, Integer size) {
		Instant now = this.clock.instant();
		FeedCursor from = FeedCursor.decode(cursor);
		int limit = StoryFeedService.clamp(size);
		List<Story> rows = this.storyRepository.findMyReplies(me, now, from.publishAt(), from.storyId(), limit + 1);
		boolean hasMore = rows.size() > limit;
		List<Story> shown = hasMore ? rows.subList(0, limit) : rows;
		if (shown.isEmpty()) {
			return new MyRepliesResponse(List.of(), null);
		}

		List<StoryResponse> replies = this.assembler.many(shown, me, now);
		Map<UUID, MyRepliesResponse.Parent> parents = parentsOf(shown, me, now);

		List<MyRepliesResponse.Item> items = new ArrayList<>(shown.size());
		for (int i = 0; i < shown.size(); i++) {
			items.add(new MyRepliesResponse.Item(replies.get(i), parents.get(shown.get(i).getParentStoryId())));
		}
		Story last = shown.get(shown.size() - 1);
		String next = hasMore ? new FeedCursor(last.getPublishAt(), last.getStoryId()).encode() : null;
		return new MyRepliesResponse(items, next);
	}

	/** 바로 위 글들의 상태 — 원글 · 공동 작성 · 팔로우 · 차단 · 작성자 이름을 각각 한 번씩만 읽는다. */
	private Map<UUID, MyRepliesResponse.Parent> parentsOf(List<Story> replies, UUID me, Instant now) {
		Set<UUID> parentIds = new HashSet<>();
		for (Story reply : replies) {
			parentIds.add(reply.getParentStoryId());
		}
		Map<UUID, Story> byId = new HashMap<>();
		// findAllById 는 지운 행도 돌려준다 — 「지워졌다」를 말하려면 그 행이 있어야 한다.
		this.storyRepository.findAllById(parentIds).forEach((story) -> byId.put(story.getStoryId(), story));

		Set<UUID> authors = new HashSet<>();
		byId.values().forEach((story) -> authors.add(story.getAuthorUserId()));
		Set<UUID> coauthoring = new HashSet<>();
		for (StoryCoauthor coauthor : this.coauthorRepository.findByKeyStoryIdInOrderByJoinedAtAscKeyUserIdAsc(parentIds)) {
			if (coauthor.getUserId().equals(me)) {
				coauthoring.add(coauthor.getStoryId());
			}
		}
		Set<UUID> followed = authors.isEmpty() ? Set.of() : new HashSet<>(this.followRepository.findFollowedAmong(me, authors));
		Set<UUID> blockers = authors.isEmpty() ? Set.of() : new HashSet<>(this.blockRepository.findBlockersAmong(me, authors));
		Map<UUID, AppUser> users = new HashMap<>();
		this.appUserRepository.findAllById(authors).forEach((user) -> users.put(user.getUserId(), user));

		Map<UUID, MyRepliesResponse.Parent> out = new HashMap<>();
		for (UUID parentId : parentIds) {
			Story parent = byId.get(parentId);
			String state = stateOf(parent, me, now, coauthoring, followed, blockers);
			boolean visible = "VISIBLE".equals(state);
			out.put(parentId, new MyRepliesResponse.Parent(parentId.toString(), state,
					visible ? preview(parent.getBody()) : null,
					visible ? authorName(users.get(parent.getAuthorUserId())) : null));
		}
		return out;
	}

	/** {@link StoryVisibilityPolicy#canView} 와 {@link StoryService#requireVisible} 의 규칙을 한 쪽 분 자료로 다시 적은 것. */
	private static String stateOf(Story parent, UUID me, Instant now, Set<UUID> coauthoring, Set<UUID> followed,
			Set<UUID> blockers) {
		if (parent == null || parent.getDeletedAt() != null) {
			return "DELETED";
		}
		if (!parent.getModerationState().visibleToOthers() || blockers.contains(parent.getAuthorUserId())) {
			return "HIDDEN";
		}
		if (parent.isAuthor(me) || coauthoring.contains(parent.getStoryId())) {
			return "VISIBLE";
		}
		if (!parent.isPublishedAt(now)) {
			return "HIDDEN";
		}
		return switch (parent.getVisibility()) {
			case PUBLIC -> "VISIBLE";
			case FOLLOWERS -> followed.contains(parent.getAuthorUserId()) ? "VISIBLE" : "HIDDEN";
			case PRIVATE -> "HIDDEN";
		};
	}

	/**
	 * 앞 60자(글자 단위 — 이모지를 반으로 자르지 않는다).
	 *
	 * <p>🔴 원문 그대로 낸다 (S15P21E201-1655). 이 미리보기는 앱이 마크다운을 거치지 않고 Text 로 바로 그려, 인코딩해서
	 * 내면 「&amp;」가 그대로 보였다. 앱은 모든 글을 Text 로 그려 주입 경로가 없다 — 근거는 docs/VULNERABILITY-REPORT.md.
	 */
	static String preview(String body) {
		if (body == null) {
			return null;
		}
		int end = body.codePointCount(0, body.length()) <= PREVIEW_LENGTH ? body.length()
				: body.offsetByCodePoints(0, PREVIEW_LENGTH);
		return body.substring(0, end);
	}

	/** {@link StoryResponseAssembler} 의 작성자 이름과 같은 규칙 — 탈퇴했으면 「탈퇴한 사용자」, 이름은 원문 그대로. */
	private static String authorName(AppUser author) {
		return (author == null || author.getDeletedAt() != null || author.getDisplayName() == null
				|| author.getDisplayName().isBlank()) ? "탈퇴한 사용자" : author.getDisplayName();
	}
}
