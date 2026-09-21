package com.gabolle.backend.feed.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 피드 한 장 — 앱이 받는 모양. 빈 목록일 때 앱이 무엇을 띄울지는 {@code emptyReason} 이
 * 정한다. {@code NOT_BUILT_YET} 은 기다리면 풀리고 {@code NO_ITEMS} 는 사람이 조건을
 * 바꿔야 풀려서, 안내가 달라야 한다.
 *
 * @param buildId     이 장을 낸 세대. 아직 만든 적이 없으면 {@code null}
 * @param items       저장된 순서 그대로다. 서버가 다시 정렬하지 않았다
 * @param nextCursor  다음 장을 부를 때 {@code cursor} 로 넘길 값. 더 없으면 {@code null}
 * @param stale       만료 시각이 지났는가. 지나도 내용은 그대로 준다
 * @param builtAt     이 세대를 만든 시각
 * @param emptyReason 목록이 빌 때만 채워진다
 */
public record FeedPageResponse(UUID buildId, List<FeedItemResponse> items, Integer nextCursor, boolean stale,
		OffsetDateTime builtAt, String emptyReason) {
}
