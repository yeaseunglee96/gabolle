package com.gabolle.backend.itinerary.presentation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.gabolle.backend.itinerary.domain.RemovalReasons;

/**
 * 장소 제외·재계산 요청 본문. 두 경로가 이 하나의 레코드를 함께 쓰고, 각 경로가 실제로 쓰는
 * 칸이 다르다.
 * {@code POST /items/{itemId}/remove} 는 {@code baseVersion} 필수 · {@code operationalReason}
 * 선택이고, 뺄 항목은 URL 의 {@code itemId} 라 {@code fromItemId} 는 이 경로에서 안 쓴다.
 * {@code POST /recalculate} 는 {@code baseVersion} 필수에 {@code dayIndex} 와
 * {@code fromItemId} 중 하나다. {@code dayIndex} 만 주면 그 날의 고정되지 않은 자리를 전부
 * 다시 채우고, {@code fromItemId} 를 주면 그 항목이 속한 날에서 그 항목부터 뒤만 다시 채운다.
 * 둘 다 주면 {@code fromItemId} 가 우선이고, {@code operationalReason} 은 이 경로에서 안 쓴다.
 * {@code baseVersion} 이 없으면 편집을 받을 수 없다 — 화면이 무엇을 보고 있었는지 모르면
 * 덮어쓰기를 막을 수 없다.
 */
public record ItineraryEditJobRequest(@NotNull Integer baseVersion, String operationalReason, String fromItemId,
		@Min(0) Integer dayIndex) {

	public boolean hasFromItem() {
		return fromItemId != null && !fromItemId.isBlank();
	}

	/**
	 * 빼기 이유는 정해진 코드만 받는다 (S15P21E201-1689) — {@link RemovalReasons}. 비어 있으면 받는다: 이미 나간 앱은 이 칸을
	 * 보내지 않는다.
	 */
	@AssertTrue(message = "operationalReason 은 정해진 코드 중 하나여야 합니다(예: ALREADY_VISITED · NOT_INTERESTED · TOO_FAR · CLOSED · OTHER)")
	public boolean isOperationalReasonKnown() {
		return RemovalReasons.isKnownOrEmpty(operationalReason);
	}
}
