package com.gabolle.backend.common.security;

/**
 * 인증 정보 없이, 또는 알아볼 수 없는 인증 정보로 보호 API 에 닿았을 때.
 *
 * <p>운영에서는 여기까지 거의 오지 않는다 — {@code SecurityConfig} 의 {@code anyRequest().authenticated()}
 * 가 앞에서 막고 공통 401 을 돌려주기 때문이다. 이 예외가 의미를 갖는 자리는 필터 없이 컨트롤러만
 * 띄우는 테스트와, 앞으로 permitAll 로 열리는 경로다.
 *
 * <p>🔴 {@code auth} 패키지의 {@code AuthException} 을 쓰지 않는 이유는 방향이다. {@code common} 이
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
