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
		Instant now = this.clock.instant();
		FeedCursor from = FeedCursor.decode(cursor);
		int size = clamp(limit);
		List<Story> rows = switch (scope) {
			case ALL -> viewer == null
					? this.storyRepository.findPublicFeedForAnonymous(now, from.publishAt(), from.storyId(), size + 1)
					: this.storyRepository.findPublicFeed(viewer, now, from.publishAt(), from.storyId(), size + 1);
			case FOLLOWING -> {
				if (viewer == null) {
					throw new AnonymousFollowingFeedException();
				}
				yield this.storyRepository.findFollowingFeed(viewer, now, from.publishAt(), from.storyId(), size + 1);
			}
		};
		return page(rows, size, viewer, now);
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
		boolean hasMore = rows.size() > size;
		List<Story> shown = hasMore ? rows.subList(0, size) : rows;
		List<StoryResponse> items = this.assembler.many(shown, viewer, now);
		String next = null;
		if (hasMore) {
			Story last = shown.get(shown.size() - 1);
			next = new FeedCursor(last.getPublishAt(), last.getStoryId()).encode();
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
