package com.gabolle.backend.notification.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.notification.application.PushTokenService;
import com.gabolle.backend.notification.presentation.dto.PushTokenRequestDto;

/**
 * 기기 푸시 토큰 등록·해제 — S15P21E201-1391.
 *
 * <p>앱이 이미 이 두 경로를 부른다({@code frontend/src/notifications/pushToken.ts}, 1429 로 머지됨).
 * 받을 자리가 없어서 그 호출이 그대로 실패하고 있었다 — 계약(경로·본문)은 앱이 보내는 그대로 맞췄다.
 *
 * <pre>
 *   PUT    /api/v1/me/push-tokens          { "token": "ExponentPushToken[..]", "platform": "ios" }
 *   DELETE /api/v1/me/push-tokens/{token}
 * </pre>
 *
 * <p>둘 다 로그인한 사람만이다. 토큰은 「이 계정으로 이 기기에 보낸다」는 뜻이라 주인이 없으면
 * 의미가 없고, 익명 출입증에 매달면 로그아웃한 뒤에도 그 기기로 알림이 간다.
 *
 * <p>답에 몸이 없다(204). 화면이 쓸 값이 없고, 「있었는지 없었는지」를 돌려주면 남의 토큰 존재
 * 여부를 물어보는 창구가 된다.
 */
@RestController
@RequestMapping("/api/v1/me/push-tokens")
@Profile({ "db", "dev" })
public class PushTokenController {

	private final PushTokenService pushTokenService;

	public PushTokenController(PushTokenService pushTokenService) {
		this.pushTokenService = pushTokenService;
	}

	@PutMapping
	public ResponseEntity<Void> register(@RequestBody PushTokenRequestDto request, Authentication authentication) {
		UUID userId = AuthenticatedUsers.requireId(authentication);

		this.pushTokenService.register(userId, request.token(), request.platform());

		return ResponseEntity.noContent().build();
	}

	/**
	 * 로그아웃할 때 앱이 부른다.
	 *
	 * <p>🔴 토큰이 주소에 실려 온다. 주인을 안 보면 아무나 남의 기기를 알림에서 떼어 낼 수 있어,
	 * 지우는 것은 «내 것» 만이다({@link PushTokenService#unregister}). 없거나 남의 것이어도 204 다.
	 */
	@DeleteMapping("/{token}")
	public ResponseEntity<Void> unregister(@PathVariable String token, Authentication authentication) {
		UUID userId = AuthenticatedUsers.requireId(authentication);

		this.pushTokenService.unregister(userId, token);

		return ResponseEntity.noContent().build();
	}
}
