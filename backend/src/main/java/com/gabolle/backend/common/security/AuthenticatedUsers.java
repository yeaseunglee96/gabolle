package com.gabolle.backend.common.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;

/**
 * 요청자가 누구인지 <b>인증에서만</b> 얻는다.
 *
 * <h2>왜 이 클래스가 필요한가</h2>
 *
 * {@code HmacJwtAuthenticationFilter} 가 JWT 의 {@code sub}(사용자 UUID 문자열)를 principal 로
 * 넣어 두고, {@code AuthController.authenticatedUserId} 가 그것을 꺼내 쓴다. 그런데 그 메서드는
 * {@code private} 이라 다른 도메인이 재사용할 수 없었고, 그 결과 {@code trip} 과 {@code itinerary} 의
 * 컨트롤러가 <b>{@code X-User-Id} 요청 헤더</b>로 사용자를 정하고 있다.
 *
 * <p>🔴 그 방식은 인가 우회다. 인증만 통과하면 남의 ID 를 헤더에 실어 보낼 수 있고, 그러면 소유·참여
 * 검사가 그 주장 값으로 돌아 무력화된다. API 명세 2.1 도 "다른 회원의 ID 를 추측해도 조회·수정할 수
 * 없어야 한다" 고 못 박고 있다. 그래서 새로 만드는 컨트롤러는 <b>처음부터</b> 이것을 쓴다.
 *
 * <p>기존 두 컨트롤러는 다른 사람이 진행 중인 범위라 이번에 손대지 않았다. 이 클래스가 그때 쓸
 * 자리를 미리 만들어 둔 것이다.
 */
public final class AuthenticatedUsers {

	private AuthenticatedUsers() {
	}

	/**
	 * 인증된 사용자 ID. 없거나 UUID 로 읽을 수 없으면 예외를 던진다.
	 *
	 * <p>🔴 기본값을 만들어 내지 않는다. {@code "usr_unknown"} 같은 값을 넣으면 그 뒤의 권한 검사가
	 * 전부 그 가짜 사용자 기준으로 돌고, 실패가 401 이 아니라 "그런 자원 없음" 으로 나타나 원인을
	 * 찾기 어려워진다.
	 */
	public static UUID requireId(Authentication authentication) {
		if (authentication == null || authentication.getName() == null) {
			throw new UnauthenticatedRequestException("AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		try {
			return UUID.fromString(authentication.getName());
		}
		catch (IllegalArgumentException exception) {
			throw new UnauthenticatedRequestException("INVALID_AUTHENTICATION", "인증 정보가 올바르지 않습니다.");
		}
	}

	/**
	 * 있으면 사용자 ID, 없으면 비어 있음. 로그인 없이도 열리는 조회에서 "로그인했으면 개인화된 값을
	 * 얹는다" 같은 자리에 쓴다.
	 */
	public static Optional<UUID> optionalId(Authentication authentication) {
		if (authentication == null || authentication.getName() == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(authentication.getName()));
		}
		catch (IllegalArgumentException exception) {
			return Optional.empty();
		}
	}
}
