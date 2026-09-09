package com.gabolle.backend.itinerary.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 장소 제외(ITN-08)·재계산(ITN-09) 요청 본문 — S15P21E201-249.
 *
 * <p>🔴 두 경로가 이 하나의 레코드를 함께 쓴다. 각 경로가 실제로 쓰는 칸이 다르다.
 * <ul>
 *   <li>{@code POST /items/{itemId}/remove}(ITN-08) — {@code baseVersion} 필수,
 *       {@code operationalReason} 은 선택. 뺄 항목은 URL 의 {@code itemId} 다 —
 *       이 레코드의 {@code fromItemId} 는 이 경로에서 안 쓴다</li>
 *   <li>{@code POST /recalculate}(ITN-09) — {@code baseVersion} 필수, 그리고 {@code dayIndex} 와
 *       {@code fromItemId} 중 <b>하나</b>. {@code dayIndex} 만 주면 그 날의 고정되지 않은 자리를 전부
 *       다시 채우고("이 날 다시 계산" 버튼), {@code fromItemId} 를 주면 그 항목이 속한 날에서 그 항목
 *       부터 뒤만 다시 채운다(이미 다녀온 곳은 남긴다). 둘 다 주면 {@code fromItemId} 가 우선이다.
 *       재계산은 사용자 자유 입력을 받지 않으므로 {@code operationalReason} 은 이 경로에서 안 쓴다</li>
 * </ul>
 *
 * <p>{@code baseVersion} 이 없으면 편집을 받을 수 없다 — API-09 "일정 변경은
 * {@code baseVersion} 또는 {@code If-Match} 를 요구한다" 와 같은 이유다
 * ({@code LockItemRequest} 참고).
 */
public record ItineraryEditJobRequest(@NotNull Integer baseVersion, String operationalReason, String fromItemId,
		@Min(0) Integer dayIndex) {

	public boolean hasFromItem() {
		return fromItemId != null && !fromItemId.isBlank();
	}
}
