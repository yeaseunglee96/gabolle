package com.gabolle.backend.common.security;

/**
 * 인증 정보 없이, 또는 알아볼 수 없는 인증 정보로 보호 API 에 닿았을 때. 운영에서는 {@code SecurityConfig}
 * 가 앞에서 막으므로 여기까지 거의 오지 않는다 — 의미를 갖는 자리는 필터 없이 컨트롤러만 띄우는
 * 테스트와 permitAll 로 열린 경로다.
 *
 * <p>{@code auth} 패키지의 {@code AuthException} 을 쓰지 않는 것은 의존 방향 때문이다. {@code common} 이
 * {@code auth} 를 알면 모든 도메인이 인증 모듈에 묶인다.
 */
public class UnauthenticatedRequestException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String code;

	public UnauthenticatedRequestException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
