package com.gabolle.backend.common.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

import com.gabolle.backend.auth.config.AnonymousSessionAuthenticationFilter;
import com.gabolle.backend.auth.service.AuthException;

/**
 * 요청 컨트롤러가 "누가 요청했는가" 를 얻는 자리.
 *
 * <p>{@code @RequestHeader("X-User-Id")} 를 쓰지 않는다. 인증 뒤에 "누구인가" 를 헤더로 다시 받으면
 * 로그인한 사람이 남의 ID 를 헤더에 실어 보내는 것만으로 그 사람 행세를 할 수 있다. 신뢰하는 것은
 * {@link Authentication#getName()}(JWT 의 {@code sub} 클레임) 뿐이다.
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
	 * 익명 세션 id. {@link #optionalId} 는 익명 요청에 빈 값을 주므로(principal 이
	 * {@code "anon:" + sessionId} 형식이라 {@code UUID.fromString} 이 실패한다), 회원과 익명을 모두
	 * 알아야 하는 자리는 둘 다 불러야 한다. 인증이 아예 없어도 예외를 던지지 않는다.
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
	 * 여행 생성처럼 익명 세션도 자원의 주인이 될 수 있는 자리 전용. 그 외에는 {@link #requireId} 를 쓴다 —
	 * 회원 전용 자원까지 여기로 바꾸면 익명 세션이 로그인 없이 회원 자원에 닿는 길이 열린다.
	 *
	 * <p>{@code principal} 이 {@link AnonymousSessionAuthenticationFilter#ANONYMOUS_PRINCIPAL_PREFIX} 로
	 * 시작하면 익명 소유자를, 아니면 회원 소유자를 돌려준다. 둘 다 아니면 401.
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
