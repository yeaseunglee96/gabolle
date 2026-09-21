package com.gabolle.backend.notification.presentation.dto;

/**
 * {@code PUT /api/v1/me/push-tokens} 요청 본문 — 앱이 보내는 모양 그대로다
 * ({@code frontend/src/notifications/pushToken.ts}).
 *
 * @param token Expo 푸시 토큰({@code ExponentPushToken[...]})
 * @param platform {@code ios} 또는 {@code android}. 앱이 {@code Platform.OS} 로 정해 보낸다
 */
public record PushTokenRequestDto(String token, String platform) {
}
