package com.gabolle.backend.itinerary.presentation.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 일정 항목 고정 요청 — ITN-03 {@code POST /itineraries/{id}/items/{itemId}/lock}.
 *
 * <p>명세가 받는 것으로 {@code baseVersion} 하나를 지정한다.
 *
 * <p>🔴 이 값이 없으면 편집을 받을 수 없다. API-09 — "일정 변경은
 * {@code baseVersion} 또는 {@code If-Match} 를 요구한다." 화면이 무엇을 보고
 * 있었는지 모르면 덮어쓰기를 막을 방법이 없다.
 */
public record LockItemRequest(@NotNull Integer baseVersion) {
}
