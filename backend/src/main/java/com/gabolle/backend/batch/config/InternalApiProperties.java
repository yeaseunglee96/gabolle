package com.gabolle.backend.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기계가 부르는 내부 배치 API 의 인증 설정 — MLOps Phase 1.
 *
 * <h2>🔴 왜 {@code /api/v1/admin/**} 를 안 쓰는가</h2>
 *
 * 그쪽은 <b>사람</b>을 위한 문이다. {@code SecurityConfig} 가 {@code hasRole("ADMIN")} 로
 * 막고, ADMIN 은 {@link com.gabolle.backend.auth.config.AdminRoleStartupSynchronizer} 가
 * 배포 설정의 이메일 목록을 보고 기동할 때마다 다시 계산한다. 즉 <b>그 목록에서 빠지는 순간
 * 권한이 사라진다.</b> 배치 실행자를 거기 끼워 넣으면 두 가지가 같이 나빠진다.
 *
 * <ul>
 * <li>Airflow 가 사람 계정의 비밀번호를 들고 있어야 한다 — 사람의 신원과 기계의 신원이 섞인다</li>
 * <li>운영자 목록을 고치는 사람이, 자기가 배치를 멈춘다는 것을 모른 채 멈춘다</li>
 * </ul>
 *
 * 그래서 기계에게는 <b>따로 문을 낸다.</b> {@code /internal/**} 는 공유 토큰 하나로만 열리고,
 * 사람 계정과 아무 관계가 없다.
 *
 * <h2>🔴 토큰이 비어 있으면 문이 잠긴다 — 열리지 않는다</h2>
 *
 * 설정을 깜빡했을 때 "인증 없이 통과" 가 되면, 그 사고는 아무 소리도 내지 않는다.
 * 그래서 값이 없으면 {@link com.gabolle.backend.batch.security.InternalTokenAuthenticationFilter}
 * 가 <b>전부 거부한다.</b> 배치가 멈추는 것은 시끄럽고, 문이 열려 있는 것은 조용하다.
 */
@ConfigurationProperties(prefix = "gabolle.internal-api")
public class InternalApiProperties {

	/**
	 * {@code X-Internal-Token} 헤더로 받을 값.
	 *
	 * <p>🔴 운영에서는 환경변수 {@code GABOLLE_INTERNAL_API_TOKEN} 으로 넣는다.
	 * 설정 파일에 적지 않는다 — 적으면 git 에 들어간다.
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
