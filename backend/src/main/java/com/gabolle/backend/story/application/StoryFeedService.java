package com.gabolle.backend.story.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.presentation.dto.StoryFeedResponse;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.repository.StoryRepository;

/**
 * 기록 피드 — 전체 / 팔로잉 / 한 사람.
 *
 * <p>세 피드 모두 커서 방식이고 "한 개 더 읽기" 로 다음 묶음이 있는지 안다 — {@code limit + 1} 개를 읽어
 * {@code limit} 개보다 많이 왔으면 마지막 것을 잘라 내고 그 앞의 마지막 항목으로 {@code nextCursor} 를
 * 만든다. 전체 개수를 세는 질의가 없다 — 그 질의는 표가 클수록 느려지고, 앱은 "더 있나" 만 알면 된다.
 */
@Service
@Profile({ "db", "dev" })
public class StoryFeedService {

	public static final int DEFAULT_LIMIT = 20;

	public static final int MAX_LIMIT = 50;

	public enum Scope {
		/** 공개 기록 전부 + 내 기록. */
		ALL,
		/** 내가 팔로우한 사람의 기록. */
		FOLLOWING
	}

	/**
	 * 정렬 갈래. 미리 만들어 둔 피드가 아니라 <b>부를 때 계산한다</b> — 기록 수가 적어 그 편이 싸고,
	 * 미리 만들면 새 기록이 다음 빌드까지 안 보인다.
	 */
	public enum Sort {
		/** 최신순. 기본값이다. */
		RECENT,
		/** 좋아요 많은 순. 동점이면 최신순으로 내려간다. */
		POPULAR
	}

	private final StoryRepository storyRepository;

	private final StoryService storyService;

	private final StoryResponseAssembler assembler;

	private final Clock clock;

	private final BlockService blockService;

	public StoryFeedService(StoryRepository storyRepository, StoryService storyService,
			StoryResponseAssembler assembler, BlockService blockService, Clock clock) {
		this.storyRepository = storyRepository;
		this.storyService = storyService;
		this.assembler = assembler;
		this.blockService = blockService;
		this.clock = clock;
	}

	/**
	 * @param viewer 로그인한 사람. {@code null} 이면 로그인하지 않은 사람이고, 그때 {@code ALL} 은
	 *     공개 기록만 내보내고 {@code FOLLOWING} 은 거절한다
	 * @throws AnonymousFollowingFeedException 로그인하지 않은 사람이 {@code FOLLOWING} 을 물었을 때
	 */
	@Transactional(readOnly = true)
	public StoryFeedResponse feed(UUID viewer, Scope scope, String cursor, Integer limit) {
		return feed(viewer, scope, Sort.RECENT, cursor, limit);
	}

	/**
	 * @param sort {@link Sort#POPULAR} 이면 좋아요 많은 순. 커서는 정렬 갈래마다 모양이 달라서
	 *     갈래를 바꾸면 앞서 받은 커서를 다시 쓸 수 없다 — 화면이 갈래를 바꿀 때 커서를 비운다
	 */
	@Transactional(readOnly = true)
	public StoryFeedResponse feed(UUID viewer, Scope scope, Sort sort, String cursor, Integer limit) {
		Instant now = this.clock.instant();
		int size = clamp(limit);
		if (scope == Scope.FOLLOWING && viewer == null) {
			throw new AnonymousFollowingFeedException();
		}
		FeedCursor from = decodeFor(sort, cursor);
		List<Story> rows = (sort == Sort.POPULAR)
				? popularRows(viewer, scope, now, from, size + 1)
				: recentRows(viewer, scope, now, from, size + 1);
		return page(rows, size, viewer, now, sort);
	}

	private List<Story> recentRows(UUID viewer, Scope scope, Instant now, FeedCursor from, int limit) {
		return switch (scope) {
			case ALL -> viewer == null
					? this.storyRepository.findPublicFeedForAnonymous(now, from.publishAt(), from.storyId(), limit)
					: this.storyRepository.findPublicFeed(viewer, now, from.publishAt(), from.storyId(), limit);
			case FOLLOWING ->
				this.storyRepository.findFollowingFeed(viewer, now, from.publishAt(), from.storyId(), limit);
		};
	}

	private List<Story> popularRows(UUID viewer, Scope scope, Instant now, FeedCursor from, int limit) {
		int likes = from.likeCount();
		return switch (scope) {
			case ALL -> viewer == null
					? this.storyRepository.findPublicFeedForAnonymousPopular(now, from.publishAt(), from.storyId(),
							likes, limit)
					: this.storyRepository.findPublicFeedPopular(viewer, now, from.publishAt(), from.storyId(), likes,
							limit);
			case FOLLOWING -> this.storyRepository.findFollowingFeedPopular(viewer, now, from.publishAt(),
					from.storyId(), likes, limit);
		};
	}

	/**
	 * 커서가 없을 때의 출발점이 갈래마다 다르다. 인기순은 좋아요 수의 상한에서 내려와야 하므로
	 * {@link FeedCursor#nonePopular()} 다.
	 *
	 * <p>다른 갈래에서 받은 커서를 그대로 보내면 400 이다. 조용히 첫 쪽으로 되돌리지 않는다 —
	 * 화면은 이어보기를 했다고 믿는데 목록이 처음으로 돌아가 있으면 같은 것을 두 번 보게 된다.
	 */
	private static FeedCursor decodeFor(Sort sort, String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return (sort == Sort.POPULAR) ? FeedCursor.nonePopular() : FeedCursor.NONE;
		}
		FeedCursor from = FeedCursor.decode(cursor);
		if ((sort == Sort.POPULAR) != (from.likeCount() != null)) {
			throw new FeedCursor.InvalidCursorException(cursor);
		}
		return from;
	}

	/**
	 * 로그인하지 않은 사람이 팔로잉 피드를 물었다.
	 *
	 * <p>401 이 아니라 400 으로 번역된다({@code StoryExceptionHandler}). 팔로잉은 계정이 있어야 뜻이
	 * 생기는 요청이지 출입증이 상한 상태가 아니다. 로그인 유도는 화면이 한다.
	 *
	 * <p>빈 목록을 주지 않는 이유도 같다. 빈 목록은 "팔로우한 사람이 아직 안 올렸다" 와 구분되지 않아서,
	 * 화면이 그 탭을 감출지 빈 상태를 띄울지 정할 근거가 없다.
	 */
	public static class AnonymousFollowingFeedException extends RuntimeException {

		public AnonymousFollowingFeedException() {
			super("팔로잉 피드는 로그인한 뒤에 볼 수 있어요.");
		}

	}

	/** 한 사람의 기록(프로필). 요청자와 그 사람의 관계에 따라 보이는 범위가 다르다. */
	@Transactional(readOnly = true)
	public StoryFeedResponse authorFeed(UUID viewer, UUID author, String cursor, Integer limit) {
		// 그 사람이 나를 차단했으면 빈 목록이 아니라 403 이다. 빈 목록으로 답하면 화면이
		// "글이 없는 사람" 과 "나를 차단한 사람" 을 못 가른다.
		this.blockService.requireNotBlockedBy(author, viewer);
		Instant now = this.clock.instant();
		FeedCursor from = FeedCursor.decode(cursor);
		int size = clamp(limit);
		List<String> visibilities = this.storyService.visibleScopesOf(author, viewer);
		List<Story> rows = this.storyRepository.findAuthorFeed(author, visibilities, now, from.publishAt(),
				from.storyId(), size + 1);
		return page(rows, size, viewer, now);
	}

	private StoryFeedResponse page(List<Story> rows, int size, UUID viewer, Instant now) {
		return page(rows, size, viewer, now, Sort.RECENT);
	}

	private StoryFeedResponse page(List<Story> rows, int size, UUID viewer, Instant now, Sort sort) {
		boolean hasMore = rows.size() > size;
		List<Story> shown = hasMore ? rows.subList(0, size) : rows;
		List<StoryResponse> items = this.assembler.many(shown, viewer, now);
		String next = null;
		if (hasMore) {
			Story last = shown.get(shown.size() - 1);
			// 인기순 커서의 좋아요 수는 조립된 응답에서 가져온다. 다시 세면 그 사이 반응이 바뀌어
			// 화면에 보인 수와 커서의 수가 어긋나고, 그러면 다음 쪽이 한 칸 밀리거나 겹친다.
			Integer likes = (sort == Sort.POPULAR) ? items.get(items.size() - 1).likeCount() : null;
			next = new FeedCursor(last.getPublishAt(), last.getStoryId(), likes).encode();
		}
		return new StoryFeedResponse(items, next);
	}

	static int clamp(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}
		if (limit < 1) {
			return 1;
		}
		return Math.min(limit, MAX_LIMIT);
	}
}
