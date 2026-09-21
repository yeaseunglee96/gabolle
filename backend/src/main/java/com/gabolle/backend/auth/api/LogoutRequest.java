package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /api/v1/auth/logout}.
 *
 * <p>{@code allDevices} 가 원시 {@code boolean} 이면 이 키를 뺀 요청이 본문을 읽는 단계에서
 * 죽는다. 기기 하나만 끊는 것이 보통이라 그 키를 안 보내는 쪽이 자연스럽다.
 */
public record LogoutRequest(@NotBlank String refreshToken, Boolean allDevices) {

	/** 비우면 이 기기만 끊는 것으로 본다. */
	public boolean allDevicesOrFalse() {
		return Boolean.TRUE.equals(allDevices);
	}
}
