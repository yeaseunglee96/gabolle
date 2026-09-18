package com.gabolle.backend.itinerary.presentation.dto;

import java.util.List;

/**
 * {@code GET /api/v1/itineraries/{id}/versions} 응답 — S15P21E201-1011.
 *
 * <h2>🔴 이 응답은 예전에 배열 그 자체였다</h2>
 *
 * 판({@code itinerary_versions})은 <b>일정을 고칠 때마다 쌓인다.</b> 끝이 없는 목록인데 상한도
 * 쪽 나눔도 없이 전부 내보내고 있었다 — 오래 쓴 일정일수록 이 조회만 무거워진다.
 *
 * <p>상한을 두려면 <b>"더 있다" 를 말할 자리</b>가 필요하다. 공통 응답 봉투({@code ApiResponse})
 * 의 {@code meta} 에는 요청 번호밖에 없어서, 배열을 그대로 둔 채로는 그 사실을 실을 곳이 없다.
 * 상한만 두고 안 알리면 목록이 <b>조용히 잘리고</b>, 되돌리기 화면에서는 <b>돌아갈 수 있던 판이
 * 사라진 것</b>으로 보인다 — 이 화면에서 그것은 데이터를 잃는 것과 같게 느껴진다.
 *
 * <p>🔴 <b>그래서 이 변경은 화면을 함께 고쳐야 한다.</b> 배열을 읽던 쪽은 이제
 * {@code data.items} 를 읽어야 한다. 백엔드가 먼저 배포되면 그 사이 판 목록 화면이 빈 목록으로
 * 보인다 — 배포 순서를 프론트와 맞춘다.
 *
 * <p>모양은 {@code FestivalResponse} 와 같게 뒀다({@code items}·{@code count}·{@code hasMore}).
 * 목록 응답마다 다른 모양을 만들면 화면이 경로마다 다르게 읽어야 한다.
 *
 * @param count items 의 개수. 전체 판 수가 아니라 <b>이번 쪽</b>의 개수다
 * @param hasMore 상한에 걸려 더 있는데 안 보낸 판이 있다. 참이면 화면은 {@code page} 를 올려
 *     다음 쪽을 더 받을 수 있다
 */
public record ItineraryVersionsResponse(List<ItineraryVersionSummaryResponse> items, int count, boolean hasMore) {
}
