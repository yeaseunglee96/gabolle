package com.gabolle.backend.auth.api;

import java.util.Map;

import jakarta.validation.constraints.NotEmpty;

/**
 * {@code PATCH /api/v1/auth/me/consents} 요청 바디 — 동의 항목을 부분 수정한다 (S15P21E201-735).
 *
 * <p>모양은 가입 요청의 {@code consents} 와 <b>일부러 같다</b> — {@code {"BEHAVIOR_PERSONALIZATION":
 * true}} 처럼 항목 이름과 참/거짓의 맵이다. 앱이 가입에서 쓰던 것을 그대로 쓸 수 있어야
 * "가입에서는 이렇게, 변경에서는 저렇게" 라는 두 번째 규칙이 안 생긴다.
 *
 * <p>🔴 <b>보내지 않은 항목은 안 바꾼다.</b> 전체 상태를 통째로 덮는 {@code PUT} 이 아니라
 * 부분 수정이다. 통째로 덮게 만들면 앱이 화면에 없는 항목(정밀 위치·건강 제약)까지 매번
 * 실어 보내야 하고, 안 실으면 <b>그 동의가 조용히 철회된다.</b>
 *
 * <p>🔴 <b>빈 맵은 거부한다</b>({@code @NotEmpty}). 아무것도 안 바꾸는 요청은 성공도 실패도
 * 아니어서, 200 을 받은 쪽이 무언가 바뀌었다고 오해한다.
 */
public record UpdateConsentsRequest(@NotEmpty Map<String, Boolean> consents) {
}
