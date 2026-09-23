package com.gabolle.backend.batch.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 *
 * <p>🔴 값 하나만 넣으면 그 값을 바꾸는 순간 옛 값을 쓰는 호출자가 전부 즉시 끊긴다 — 무중단
 * 회전이 안 된다(S15P21E201-1551). 그래서 {@code GABOLLE_INTERNAL_API_TOKEN} 은 콤마로 구분한
 * 복수 값을 받는다: 새 토큰을 목록 끝에 추가해 배포 → 호출자들을 새 토큰으로 전환 → 옛 토큰을
 * 목록에서 빼고 다시 배포, 순서로 무중단 회전이 된다.
 */
@ConfigurationProperties(prefix = "gabolle.internal-api")
public class InternalApiProperties {

	/**
	 * {@code X-Internal-Token} 헤더로 받을 값들. 운영에서는 환경변수
	 * {@code GABOLLE_INTERNAL_API_TOKEN} 으로 넣는다 — 설정 파일에 적으면 git 에 들어간다.
	 * 콤마로 여러 값을 넣을 수 있고, 그중 하나만 맞아도 통과한다.
	 */
	private List<String> tokens = Collections.emptyList();

	public void setToken(String token) {
		if (token == null) {
			this.tokens = Collections.emptyList();
			return;
		}
		List<String> parsed = new ArrayList<>();
		for (String candidate : token.split(",")) {
			String trimmed = candidate.trim();
			if (!trimmed.isEmpty()) {
				parsed.add(trimmed);
			}
		}
		this.tokens = List.copyOf(parsed);
	}

	/** 지금 유효한 토큰 전부 — 헤더 값이 이 중 하나와만 맞으면 통과한다. */
	public List<String> getTokens() {
		return this.tokens;
	}

	/** 토큰이 하나라도 설정되어 있는가. 없으면 내부 API 는 아무도 못 쓴다. */
	public boolean isConfigured() {
		return !this.tokens.isEmpty();
	}
}
