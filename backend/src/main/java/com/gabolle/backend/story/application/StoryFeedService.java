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
import com.gabolle.backend.story.repository.UserFollowRepository;

/**
 * 기록 피드 — 전체 / 팔로잉 / 맞춤 / 한 사람.
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
		FOLLOWING,
		/**
		 * 맞춤 — 팔로우한 사람의 기록을 인기순으로. 팔로우가 없거나 그 사람들이 아직 안 올렸으면
		 * 전체 인기순으로 대체한다.
		 *
		 * <p>대체가 예외가 아니라 기본이다. 실서버의 팔로우 관계가 한 건뿐이라 거의 모든 요청이
		 * 대체로 간다. 그래서 무엇이 적용됐는지를 {@code X-Feed-Applied} 로 내보낸다 — 화면이
		 * 「맞춤 추천」이라고 써 놓고 실은 인기순을 보여주는 상태를 사용자에게 숨기지 않는다.
		 */
		FOR_YOU
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

	/**
	 * 실제로 적용된 것. {@link Sort} 와 따로 두는 이유는 <b>맞춤은 요청 파라미터가 아니기</b> 때문이다 —
	 * 같은 열거형에 두면 {@code sort=FOR_YOU} 로 부를 수 있게 되고, 그때 서버는 최신순을 주면서
	 * 머리에는 맞춤이라고 적는 상태가 된다.
	 */
	public enum Applied {
		RECENT, POPULAR, FOR_YOU
	}

	/**
	 * 한 쪽과, 그 쪽을 만들 때 <b>실제로 적용된</b> 것. 요청한 것과 다를 수 있어서 함께 낸다.
	 *
	 * @param applied 요청이 {@link Scope#FOR_YOU} 여도 대체가 일어났으면 {@link Applied#POPULAR} 다
	 */
	public record Feed(StoryFeedResponse page, Applied applied) {
	}

	private final StoryRepository storyRepository;

	private final StoryService storyService;

	private final StoryResponseAssembler assembler;

	private final Clock clock;

	private final BlockService blockService;

	/** 맞춤 피드가 「이 사람이 누굴 팔로우하고 있나」를 물을 때만 쓴다. */
	private final UserFollowRepository followRepository;

	public StoryFeedService(StoryRepository storyRepository, StoryService storyService,
			StoryResponseAssembler assembler, BlockService blockService,
			UserFollowRepository followRepository, Clock clock) {
		this.storyRepository = storyRepository;
		this.storyService = storyService;
		this.assembler = assembler;
		this.blockService = blockService;
		this.followRepository = followRepository;
		this.clock = clock;
	}

	/**
	 * @param viewer 로그인한 사람. {@code null} 이면 로그인하지 않은 사람이고, 그때 {@code ALL} 은
	 *     공개 기록만 내보내고 {@code FOLLOWING} 은 거절한다
	 * @throws AnonymousFollowingFeedException 로그인하지 않은 사람이 {@code FOLLOWING} 을 물었을 때
	 */
	@Transactional(readOnly = true)
	public StoryFeedResponse feed(UUID viewer, Scope scope, String cursor, Integer limit) {
		return feed(viewer, scope, Sort.RECENT, cursor, limit).page();
	}

	/**
	 * @param sort {@link Sort#POPULAR} 이면 좋아요 많은 순. 커서는 정렬 갈래마다 모양이 달라서
	 *     갈래를 바꾸면 앞서 받은 커서를 다시 쓸 수 없다 — 화면이 갈래를 바꿀 때 커서를 비운다.
	 *     {@link Scope#FOR_YOU} 는 이 값을 무시한다(언제나 인기순 모양이다)
	 */
	@Transactional(readOnly = true)
	public Feed feed(UUID viewer, Scope scope, Sort sort, String cursor, Integer limit) {
		if (scope == Scope.FOR_YOU) {
			return forYou(viewer, cursor, limit);
		}
		Instant now = this.clock.instant();
		int size = clamp(limit);
		if (scope == Scope.FOLLOWING && viewer == null) {
			throw new AnonymousFollowingFeedException();
		}
		FeedCursor from = decodeFor(sort, cursor);
		List<Story> rows = (sort == Sort.POPULAR)
				? popularRows(viewer, scope, now, from, size + 1)
				: recentRows(viewer, scope, now, from, size + 1);
		return new Feed(page(rows, size, viewer, now, sort), Applied.valueOf(sort.name()));
	}

	/**
	 * 맞춤 — 팔로우한 사람의 기록을 인기순으로 내고, 나올 것이 없으면 전체 인기순으로 대체한다.
	 *
	 * <h2>취향 벡터를 아직 안 쓴다</h2>
	 *
	 * 기록에 장소가 붙어야 취향 벡터와 견줄 수 있는데, 실서버에서 {@code story.place_id} 가 채워진
	 * 기록이 39건 중 3건이다. 열에 아홉은 견줄 것이 없어서, 지금 넣으면 점수가 거의 모든 기록에서
	 * 같은 값이 되고 «취향을 반영했다»는 말만 남는다. 기록에 장소가 붙는 비율이 오르면 그때 더한다.
	 *
	 * <h2>대체는 첫 쪽에서만 판단한다</h2>
	 *
	 * 이어보기 중에 대체로 갈아타면 앞 쪽과 다른 목록이 이어져 같은 기록을 두 번 보거나 건너뛴다.
	 * 커서가 있으면 그 쪽이 무엇이었든 그대로 이어간다 — 두 길의 커서 모양이 같아서 가능하다.
	 *
	 * <p>익명도 거절하지 않는다. 팔로우가 없는 사람일 뿐이라 대체 경로로 간다 —
	 * {@link Scope#FOLLOWING} 이 400 을 내는 것과 다르다. 그쪽은 「팔로잉만 보여 달라」는 요청이고
	 * 이쪽은 「알아서 보여 달라」는 요청이다.
	 */
	private Feed forYou(UUID viewer, String cursor, Integer limit) {
		Instant now = this.clock.instant();
		int size = clamp(limit);
		FeedCursor from = decodeFor(Sort.POPULAR, cursor);

		boolean personalizable = viewer != null && this.followRepository.countByKeyFollowerUserId(viewer) > 0;
		if (personalizable) {
			List<Story> rows = this.storyRepository.findFollowingFeedPopular(viewer, now, from.publishAt(),
					from.storyId(), from.likeCount(), size + 1);
			// 첫 쪽이 비었을 때만 대체한다. 이어보기에서 빈 쪽은 「여기가 끝」이지 대체 신호가 아니다.
			boolean firstPage = (cursor == null || cursor.isBlank());
			if (!rows.isEmpty() || !firstPage) {
				return new Feed(page(rows, size, viewer, now, Sort.POPULAR), Applied.FOR_YOU);
			}
		}
		List<Story> fallback = popularRows(viewer, Scope.ALL, now, from, size + 1);
		return new Feed(page(fallback, size, viewer, now, Sort.POPULAR), Applied.POPULAR);
	}

	private List<Story> recentRows(UUID viewer, Scope scope, Instant now, FeedCursor from, int limit) {
		return switch (scope) {
			case ALL -> viewer == null
					? this.storyRepository.findPublicFeedForAnonymous(now, from.publishAt(), from.storyId(), limit)
					: this.storyRepository.findPublicFeed(viewer, now, from.publishAt(), from.storyId(), limit);
			case FOLLOWING ->
				this.storyRepository.findFollowingFeed(viewer, now, from.publishAt(), from.storyId(), limit);
			case FOR_YOU -> throw new IllegalStateException("FOR_YOU 는 forYou() 가 먼저 가로챈다");
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
			case FOR_YOU -> throw new IllegalStateException("FOR_YOU 는 forYou() 가 먼저 가로챈다");
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
