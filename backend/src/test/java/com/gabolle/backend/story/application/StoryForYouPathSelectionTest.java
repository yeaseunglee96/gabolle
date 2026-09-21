package com.gabolle.backend.story.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.repository.StoryRepository;
import com.gabolle.backend.story.repository.UserFollowRepository;

/**
 * 맞춤 피드가 <b>쪽을 넘겨도 같은 길</b>로 가는지 본다. DB 없이 경로 선택만 본다 —
 * 이 판단은 질의 결과의 «있다·없다» 하나로 갈리므로 가짜 저장소로 충분하다.
 *
 * <p>이 검사가 없어서 버그가 나갔다. 통합 검사는 팔로우가 «없는» 사용자로만 이어보기를 봤는데,
 * 깨지는 자리는 팔로우가 «있고» 그 사람들의 기록이 없는 사용자였다.
 */
class StoryForYouPathSelectionTest {

	private static final UUID VIEWER = UUID.randomUUID();

	private StoryRepository stories;
	private UserFollowRepository follows;
	private StoryResponseAssembler assembler;
	private StoryFeedService service;

	@BeforeEach
	void setUp() {
		this.stories = mock(StoryRepository.class);
		this.follows = mock(UserFollowRepository.class);
		this.assembler = mock(StoryResponseAssembler.class);
		when(this.assembler.many(any(), any(), any())).thenReturn(List.of());
		this.service = new StoryFeedService(this.stories, mock(StoryService.class), this.assembler,
				mock(BlockService.class), this.follows,
				Clock.fixed(Instant.parse("2026-09-21T00:00:00Z"), ZoneOffset.UTC));
	}

	@Test
	@DisplayName("🔴 팔로우는 있는데 그 사람들이 글이 없으면 — 2쪽도 대체 경로다 (1쪽만 채우고 끊기면 안 된다)")
	void keepsFallingBackOnLaterPages() {
		when(this.follows.countByKeyFollowerUserId(VIEWER)).thenReturn(1L);
		// 팔로잉 피드는 언제나 비어 있다
		when(this.stories.findFollowingFeedPopular(any(), any(), any(), any(), anyInt(), anyInt()))
				.thenReturn(List.of());
		// 전체 인기순에는 내놓을 것이 있다
		when(this.stories.findPublicFeedPopular(any(), any(), any(), any(), anyInt(), anyInt()))
				.thenReturn(List.of(mock(Story.class)));

		StoryFeedService.Feed first = this.service.feed(VIEWER, StoryFeedService.Scope.FOR_YOU, null, null, 20);
		String cursor = new FeedCursor(Instant.parse("2026-09-20T00:00:00Z"), UUID.randomUUID(), 3).encode();
		StoryFeedService.Feed second = this.service.feed(VIEWER, StoryFeedService.Scope.FOR_YOU, null, cursor, 20);

		assertThat(first.applied()).isEqualTo(StoryFeedService.Applied.POPULAR);
		assertThat(second.applied())
				.as("1쪽이 대체였는데 2쪽이 맞춤으로 가면 목록이 끊기고 화면의 안내도 뒤집힌다")
				.isEqualTo(StoryFeedService.Applied.POPULAR);
	}

	@Test
	@DisplayName("팔로우한 사람의 기록이 있으면 1쪽·2쪽 다 맞춤이다")
	void keepsPersonalisedOnLaterPages() {
		when(this.follows.countByKeyFollowerUserId(VIEWER)).thenReturn(1L);
		when(this.stories.findFollowingFeedPopular(any(), any(), any(), any(), anyInt(), anyInt()))
				.thenReturn(List.of(mock(Story.class)));

		StoryFeedService.Feed first = this.service.feed(VIEWER, StoryFeedService.Scope.FOR_YOU, null, null, 20);
		String cursor = new FeedCursor(Instant.parse("2026-09-20T00:00:00Z"), UUID.randomUUID(), 3).encode();
		StoryFeedService.Feed second = this.service.feed(VIEWER, StoryFeedService.Scope.FOR_YOU, null, cursor, 20);

		assertThat(first.applied()).isEqualTo(StoryFeedService.Applied.FOR_YOU);
		assertThat(second.applied()).isEqualTo(StoryFeedService.Applied.FOR_YOU);
	}

	@Test
	@DisplayName("팔로우가 하나도 없으면 팔로잉 질의를 아예 안 한다")
	void noFollowsSkipsTheFollowingQuery() {
		when(this.follows.countByKeyFollowerUserId(VIEWER)).thenReturn(0L);
		when(this.stories.findPublicFeedPopular(any(), any(), any(), any(), anyInt(), anyInt()))
				.thenReturn(List.of());

		StoryFeedService.Feed feed = this.service.feed(VIEWER, StoryFeedService.Scope.FOR_YOU, null, null, 20);

		assertThat(feed.applied()).isEqualTo(StoryFeedService.Applied.POPULAR);
		org.mockito.Mockito.verify(this.stories, org.mockito.Mockito.never())
				.findFollowingFeedPopular(any(), any(), any(), any(), anyInt(), anyInt());
	}

	@Test
	@DisplayName("익명은 대체로 간다 — 거절하지 않는다")
	void anonymousFallsBack() {
		when(this.stories.findPublicFeedForAnonymousPopular(any(), any(), any(), anyInt(), anyInt()))
				.thenReturn(List.of());

		StoryFeedService.Feed feed = this.service.feed(null, StoryFeedService.Scope.FOR_YOU, null, null, 20);

		assertThat(feed.applied()).isEqualTo(StoryFeedService.Applied.POPULAR);
		org.mockito.Mockito.verify(this.follows, org.mockito.Mockito.never())
				.countByKeyFollowerUserId(eq(null));
	}
}
