package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/v1/auth/logout} — S15P21E201-816 에서 불리언을 감싼 타입으로 바꿨다.
 *
 * <p>{@code allDevices} 가 원시 {@code boolean} 이면 이 키를 뺀 요청이 본문을 읽는 단계에서
 * 죽는다. 로그아웃은 기기 하나만 끊는 것이 보통이라 그 키를 안 보내는 쪽이 오히려 자연스럽고,
 * 그때 "요청 형식이 올바르지 않습니다" 만 돌아가면 부르는 쪽이 이유를 알 수 없다. 이유는
 * {@link LocalSignupRequest} 주석에 적었다.
 */
public record LogoutRequest(@NotBlank String refreshToken, Boolean allDevices) {

	/** 비우면 이 기기만 끊는 것으로 본다. */
	public boolean allDevicesOrFalse() {
		return Boolean.TRUE.equals(allDevices);
	}
}
