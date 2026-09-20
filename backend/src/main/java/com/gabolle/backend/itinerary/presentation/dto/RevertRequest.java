package com.gabolle.backend.itinerary.presentation.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 되돌리기 요청 본문.
 * {@code baseVersion} 은 지금 보고 있는 최신 판이다(다른 편집과 같다 — 낡으면 409).
 * {@code toVersion} 은 선택이다. 없으면 "마지막 편집 직전"(최신 판의 바탕 판)으로 돌아가고,
 * 있으면 판 목록에서 고른 그 판으로 돌아간다.
 */
public record RevertRequest(@NotNull Integer baseVersion, Integer toVersion) {
}
