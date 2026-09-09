package com.gabolle.backend.itinerary.presentation.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 일정 항목 고정·해제 요청 — ITN-03 {@code POST /itineraries/{id}/items/{itemId}/lock}.
 *
 * <p>🔴 {@code baseVersion} 이 없으면 편집을 받을 수 없다. API-09 — "일정 변경은
 * {@code baseVersion} 또는 {@code If-Match} 를 요구한다." 화면이 무엇을 보고
 * 있었는지 모르면 덮어쓰기를 막을 방법이 없다.
 *
 * <h2>🔴 {@code locked} 에 {@code @NotNull} 을 걸지 않는 이유 (S15P21E201-662)</h2>
 * 명세 ITN-03 은 {@code baseVersion} 하나만 받는 "고정한다" 이고, ITN-04 는 별도
 * {@code DELETE} 로 "푼다" 이다. 그런데 <b>앱은 이 한 경로에 {@code {locked, baseVersion}}
 * 을 보내 토글로 쓴다</b>({@code frontend/src/plan/itinerary.ts} 의
 * {@code setItineraryItemLocked}). 둘 다 받아야 어느 쪽도 안 깨진다.
 *
 * <p>그래서 값이 없으면 {@code true}(고정)로 본다 — 명세대로의 {@code {"baseVersion":5}}
 * 가 그대로 "고정한다" 를 뜻하고, 앱은 값을 명시적으로 보내므로 그쪽 뜻대로 동작한다.
 * {@code @NotNull} 을 걸면 기존 계약이 400 이 된다.
 */
public record LockItemRequest(@NotNull Integer baseVersion, Boolean locked) {

    /** 값이 없으면 고정이다. 위 javadoc 참고. */
    public boolean lockedOrDefault() {
        return locked == null || locked;
    }
}
