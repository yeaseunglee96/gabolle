package com.gabolle.backend.feed.presentation.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 피드 한 장 — 앱이 받는 모양.
 *
 * <h2>🔴 빈 화면일 때 앱이 무엇을 띄울지 서버가 알려준다</h2>
 *
 * 빈 목록을 그냥 내면 앱은 "아직 안 만들어졌다" 와 "조건에 맞는 게 없다" 를 구분할 수
 * 없다. 둘은 사람에게 전혀 다른 상황이다.
 *
 * <ul>
 * <li>{@code NOT_BUILT_YET} — 가입 직후. "잠시 후 다시 열어 주세요"</li>
 * <li>{@code NO_ITEMS} — 세대는 있는데 후보가 말랐다. "조건을 넓혀 보세요"</li>
 * </ul>
 *
 * 앞은 기다리면 해결되고 뒤는 사람이 뭔가 바꿔야 해결된다. 서버가 안 알려주면 앱은
 * 둘 다 같은 안내를 띄우게 되고, 둘 중 한쪽 사람은 영영 막힌다.
 *
 * @param buildId     이 장을 낸 세대. 아직 만든 적이 없으면 {@code null}
 * @param items       줄들. <b>저장된 순서 그대로</b>다 — 서버가 다시 정렬하지 않았다
 * @param nextCursor  다음 장을 부를 때 {@code cursor} 로 넘길 값. 더 없으면 {@code null}
 * @param stale       만료 시각이 지났는가. <b>지나도 내용은 그대로 준다</b>
 * @param builtAt     이 세대를 언제 만들었나. 화면에 "몇 시 기준" 을 띄우는 데 쓴다
 * @param emptyReason 목록이 빌 때만 채워진다
 */
public record FeedPageResponse(UUID buildId, List<FeedItemResponse> items, Integer nextCursor, boolean stale,
		OffsetDateTime builtAt, String emptyReason) {
}
