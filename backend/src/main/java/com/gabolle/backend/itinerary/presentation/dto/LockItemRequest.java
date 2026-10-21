package com.gabolle.backend.itinerary.presentation.dto;

import com.gabolle.backend.itinerary.domain.ItineraryItem;

import jakarta.validation.constraints.NotNull;

/**
 * 일정 항목 고정·해제 요청.
 * {@code baseVersion} 이 없으면 편집을 받을 수 없다 — 화면이 무엇을 보고 있었는지 모르면
 * 덮어쓰기를 막을 방법이 없다.
 * {@code locked} 에 {@code @NotNull} 을 걸지 않는다. 명세는 이 경로를 "고정한다" 로만 정의하고
 * 해제는 별도 {@code DELETE} 인데, 앱은 이 한 경로에 {@code {locked, baseVersion}} 을 보내
 * 토글로 쓴다. 둘 다 받아야 어느 쪽도 안 깨진다.
 * 값이 없으면 {@code true}(고정)로 본다 — 명세대로의 {@code {"baseVersion":5}} 가 그대로
 * "고정한다" 를 뜻하고, {@code @NotNull} 을 걸면 그 기존 계약이 400 이 된다.
 */
public record LockItemRequest(@NotNull Integer baseVersion, Boolean locked, String mode) {

    /**
     * 고정 방식 — {@code "ORDER"}(순서 고정) 또는 {@code "TIME"}(시각 고정). 없으면 순서 고정이다(이 칸 이전의 앱이 보내는
     * 요청이 그대로 지금까지의 뜻이 되도록). 모르는 값은 {@link IllegalArgumentException} — 400 으로 나간다(S15P21E201-2017).
     */
    public ItineraryItem.LockMode modeOrDefault() {
        if (mode == null || mode.isBlank()) {
            return ItineraryItem.LockMode.ORDER;
        }
        try {
            return ItineraryItem.LockMode.valueOf(mode.trim().toUpperCase(java.util.Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            throw new InvalidLockModeException(mode);
        }
    }

    /** 모르는 고정 방식 — 400 ITINERARY_LOCK_MODE_INVALID. */
    public static class InvalidLockModeException extends RuntimeException {
        public InvalidLockModeException(String mode) {
            super("고정 방식은 ORDER 또는 TIME 이어야 합니다: " + mode);
        }
    }

    /** 값이 없으면 고정이다. 위 javadoc 참고. */
    public boolean lockedOrDefault() {
        return locked == null || locked;
    }
}
