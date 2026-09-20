package com.gabolle.backend.feed.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 피드 한 장. 비어 있는 것과 아직 없는 것을 가른다 — 둘은 앱이 띄울 안내가 다르다.
 *
 * @param buildId    이 장을 낸 세대. 아직 만든 적이 없으면 {@code null}
 * @param items      저장된 순서 그대로다. 다시 정렬하지 않았다
 * @param nextCursor 더 없으면 {@code null}
 * @param stale      만료 시각이 지났는가. 지나도 내용은 그대로 준다
 * @param builtAt    이 세대를 만든 시각
 * @param emptyReason 목록이 빌 때만 채워진다
 */
public record FeedPage(UUID buildId, List<FeedItem> items, Integer nextCursor, boolean stale, OffsetDateTime builtAt,
		EmptyReason emptyReason) {

	/** 왜 빈 화면인가. 앱이 무엇을 띄울지 정하는 근거다. */
	public enum EmptyReason {

		/** 아직 만든 적이 없다. 가입 직후의 정상 상태다. */
		NOT_BUILT_YET,

		/** 세대는 있는데 줄이 하나도 없다. 조건이 좁아 후보가 말랐다는 뜻이다. */
		NO_ITEMS
	}

	public static FeedPage notBuiltYet() {
		return new FeedPage(null, List.of(), null, false, null, EmptyReason.NOT_BUILT_YET);
	}
}
