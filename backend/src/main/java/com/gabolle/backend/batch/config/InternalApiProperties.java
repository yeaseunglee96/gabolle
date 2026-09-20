package com.gabolle.backend.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기계가 부르는 내부 배치 API 의 인증 설정.
 *
 * <p>{@code /api/v1/admin/**} 를 쓰지 않는 것은 그쪽이 사람을 위한 문이기 때문이다. 운영자
 * 권한은 배포 설정의 이메일 목록을 보고 기동할 때마다 다시 계산되므로, 배치를 거기 끼워
 * 넣으면 Airflow 가 사람 계정의 비밀번호를 들어야 하고 운영자 목록을 고치는 사람이 모른 채
 * 배치를 멈춘다. {@code /internal/**} 는 공유 토큰 하나로만 열리고 사람 계정과 무관하다.
 *
 * <p>토큰이 비어 있으면 문이 열리는 것이 아니라 잠긴다 —
 * {@link com.gabolle.backend.batch.security.InternalTokenAuthenticationFilter} 가 전부 거부한다.
 * 배치가 멈추는 것은 시끄럽고 문이 열려 있는 것은 조용하다.
 */
@ConfigurationProperties(prefix = "gabolle.internal-api")
public class InternalApiProperties {

	/**
	 * {@code X-Internal-Token} 헤더로 받을 값. 운영에서는 환경변수
	 * {@code GABOLLE_INTERNAL_API_TOKEN} 으로 넣는다 — 설정 파일에 적으면 git 에 들어간다.
	 */
	private String token = "";

	public String getToken() {
		return this.token;
	}

	public void setToken(String token) {
		this.token = (token == null) ? "" : token.trim();
	}

	/** 토큰이 설정되어 있는가. 없으면 내부 API 는 아무도 못 쓴다. */
	public boolean isConfigured() {
		return !this.token.isEmpty();
	}
}
