package com.gabolle.backend.common.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import com.gabolle.backend.auth.config.AnonymousSessionAuthenticationFilter;
import com.gabolle.backend.auth.service.AuthException;

/**
 * 요청 컨트롤러가 "누가 요청했는가" 를 얻는 자리 — S15P21E201-610.
 *
 * <p>🔴 {@code @RequestHeader("X-User-Id")} 를 쓰지 않는다. {@code SecurityConfig} 가 이미
 * JWT 로 인증을 요구하는데, 그 뒤에 "누구인가" 를 헤더로 다시 받으면 <b>로그인한 사람이
 * 남의 ID 를 헤더에 실어 보내는 것만으로 그 사람 행세를 할 수 있다.</b> 인증(로그인 여부)은
 * 지켜지는데 인가(그 자원이 정말 이 사람 것인가)가 뚫린다 — API 명세서 2.1절이 금지하는
 * 바로 그 시나리오다.
 *
 * <p>대신 {@link Authentication#getName()}(JWT 의 {@code sub} 클레임 — 로그인 처리 과정에서
 * 서버가 검증해 채운 사용자 UUID 문자열)만 신뢰한다. {@code AuthController.authenticatedUserId}
 * 가 이미 하던 것을 여러 도메인이 재사용할 수 있게 여기로 뽑았다.
 *
 * <p>🔴 반환형은 {@code UUID} 다 — {@code place} 패키지(S15P21E201-462, 박재현)가
 * {@link #optionalId} 를 이미 이 모양으로 쓰고 있어 맞췄다.
 *
 * <p>{@link AuthException} 을 던진다 — {@code AuthExceptionHandler} 가 이미
 * {@code @RestControllerAdvice}(도메인 제한 없는 전역)라 어느 컨트롤러에서 던져도
 * 401 로 번역된다.
 */
public final class AuthenticatedUsers {

	private AuthenticatedUsers() {
	}

	/** 반드시 로그인한 사용자여야 하는 자리. 없으면 401. */
	public static UUID requireId(Authentication authentication) {
		if (authentication == null || authentication.getName() == null) {
			throw new AuthException("AUTHENTICATION_REQUIRED", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
		}
		try {
			return UUID.fromString(authentication.getName());
		}
		catch (IllegalArgumentException ex) {
			throw new AuthException("INVALID_AUTHENTICATION", "인증 정보가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
		}
	}

	/** 로그인 여부가 선택인 자리. 없거나 형식이 이상하면 조용히 빈 값. */
	public static Optional<UUID> optionalId(Authentication authentication) {
		if (authentication == null || authentication.getName() == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(authentication.getName()));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	/**
	 * 익명 세션 id — S15P21E201-1204. 로그인 여부가 선택인 자리에서 <b>{@link #optionalId} 의 짝</b>이다.
	 *
	 * <p>🔴 {@code optionalId} 는 익명 요청에 <b>빈 값</b>을 준다. principal 이
	 * {@code "anon:" + sessionId} 형식이라 {@code UUID.fromString} 이 실패하기 때문이다. 그래서
	 * 「회원이면 누구고 아니면 어느 익명 세션인가」를 알아야 하는 자리는 <b>둘 다 불러야</b> 한다.
	 *
	 * <p>{@link #requireOwner} 와 다른 점은 <b>인증이 아예 없어도 예외를 안 던지는 것</b>이다.
	 * 그쪽은 익명 세션이 자원의 <b>주인</b>이 되는 자리(여행 생성)라 401 이 맞고, 이쪽은 그냥
	 * 「누가 읽었나」를 아는 자리다. 아무도 아니면 빈 값이고, 부르는 쪽이 그때 무엇을 할지 정한다.
	 */
	public static Optional<UUID> optionalAnonymousSessionId(Authentication authentication) {
		if (authentication == null || authentication.getName() == null) {
			return Optional.empty();
		}
		String name = authentication.getName();
		if (!name.startsWith(AnonymousSessionAuthenticationFilter.ANONYMOUS_PRINCIPAL_PREFIX)) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(
					name.substring(AnonymousSessionAuthenticationFilter.ANONYMOUS_PRINCIPAL_PREFIX.length())));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	/**
	 * 🔴 S15P21E201-317 — <b>여행 생성처럼 익명 세션도 자원의 주인이 될 수 있는 자리 전용.</b>
	 * 그 외의 모든 자리는 계속 {@link #requireId}를 쓴다 — 회원 전용 자원(내 여행 목록 등)까지
	 * 여기로 바꾸면 익명 세션이 로그인 없이 회원 자원에 닿는 길이 열린다.
	 *
	 * <p>{@code principal} 이 {@code "anon:" + sessionId} 형식(익명 인증 필터가 채운 것,
	 * {@link AnonymousSessionAuthenticationFilter#ANONYMOUS_PRINCIPAL_PREFIX})이면 익명 소유자를,
	 * 아니면(회원 JWT) 회원 소유자를 돌려준다. 둘 다 아니면 401.
	 */
	public static Owner requireOwner(Authentication authentication) {
		if (authentication == null || authentication.getName() == null) {
			throw new AuthException("AUTHENTICATION_REQUIRED", "로그인이 필요합니다.", HttpStatus.UNAUTHORIZED);
		}
		String name = authentication.getName();
		if (name.startsWith(AnonymousSessionAuthenticationFilter.ANONYMOUS_PRINCIPAL_PREFIX)) {
			try {
				UUID sessionId = UUID.fromString(
						name.substring(AnonymousSessionAuthenticationFilter.ANONYMOUS_PRINCIPAL_PREFIX.length()));
				return new Owner(sessionId, true);
			}
			catch (IllegalArgumentException ex) {
				throw new AuthException("INVALID_AUTHENTICATION", "인증 정보가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
			}
		}
		try {
			return new Owner(UUID.fromString(name), false);
		}
		catch (IllegalArgumentException ex) {
			throw new AuthException("INVALID_AUTHENTICATION", "인증 정보가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
		}
	}

	/** {@code anonymous=true} 면 {@code id} 는 익명 세션 ID(session_id)이지 회원 ID 가 아니다. */
	public record Owner(UUID id, boolean anonymous) {
	}
}
