package com.gabolle.backend.feed.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 피드 한 장 — 조회 결과.
 *
 * <p>🔴 <b>비어 있는 것과 없는 것을 가른다.</b> {@code buildId} 가 {@code null} 이면
 * 아직 이 사람의 피드를 <b>만든 적이 없다</b>. 만들었는데 줄이 0개인 것과는 다르다.
 * 앞은 "잠시 후 다시 오세요" 이고 뒤는 "조건에 맞는 게 없어요" 라 화면이 달라진다.
 * 둘 다 빈 목록으로 답하면 앱은 그 차이를 알 수 없다.
 *
 * @param buildId    이 장을 낸 세대. 없으면 {@code null}
 * @param items      줄들. 저장된 순서 그대로다 — 다시 정렬하지 않았다
 * @param nextCursor 다음 장을 부를 때 넘길 위치. 더 없으면 {@code null}
 * @param stale      만료 시각이 지났는가. <b>지나도 보여준다</b> — 낡은 화면이 빈 화면보다 낫다
 * @param builtAt    이 세대를 언제 만들었나. 화면에 "몇 시 기준" 을 띄울 수 있게
 * @param emptyReason 피드가 없을 때만 채워진다
 */
public record FeedPage(UUID buildId, List<FeedItem> items, Integer nextCursor, boolean stale, OffsetDateTime builtAt,
		EmptyReason emptyReason) {

	/** 왜 빈 화면인가. 앱이 무엇을 띄울지 정하는 근거다. */
	public enum EmptyReason {

		/** 이 사람의 피드를 아직 만든 적이 없다. 가입 직후의 정상 상태다. */
		NOT_BUILT_YET,

		/** 세대는 있는데 줄이 하나도 없다. 조건이 너무 좁아 후보가 말랐다는 뜻이다. */
		NO_ITEMS
	}

	/** 아직 만든 적이 없다. */
	public static FeedPage notBuiltYet() {
		return new FeedPage(null, List.of(), null, false, null, EmptyReason.NOT_BUILT_YET);
	}
}
