package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;

/**
 * {@code GET /api/v1/itineraries/{id}/versions} 응답.
 * 배열이 아니라 봉투인 이유는 상한을 두려면 "더 있다" 를 말할 자리가 필요해서다. 판은 일정을
 * 고칠 때마다 쌓여 끝이 없는데, 상한만 두고 안 알리면 목록이 조용히 잘리고 되돌리기 화면에서는
 * 돌아갈 수 있던 판이 사라진 것으로 보인다.
 * 배열을 읽던 화면은 {@code data.items} 를 읽어야 한다 — 배포 순서를 프론트와 맞춘다.
 * 모양은 {@code FestivalResponse} 와 같다({@code items}·{@code count}·{@code hasMore}).
 *
 * @param count items 의 개수. 전체 판 수가 아니라 이번 쪽의 개수다
 * @param hasMore 상한에 걸려 더 있는데 안 보낸 판이 있다. 참이면 화면은 {@code page} 를 올려
 *     다음 쪽을 더 받을 수 있다
 */
public record ItineraryVersionsResponse(List<ItineraryVersionSummaryResponse> items, int count, boolean hasMore) {
}
