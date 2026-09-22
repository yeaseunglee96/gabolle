package com.gabolle.backend.itinerary.presentation.dto;

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
public record LockItemRequest(@NotNull Integer baseVersion, Boolean locked) {

    /** 값이 없으면 고정이다. 위 javadoc 참고. */
    public boolean lockedOrDefault() {
        return locked == null || locked;
    }
}
