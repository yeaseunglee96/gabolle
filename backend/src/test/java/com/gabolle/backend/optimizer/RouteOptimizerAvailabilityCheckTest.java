package com.gabolle.backend.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CI 가 파이썬 경로 최적화(OR-Tools) 실행 환경을 실제로 갖췄는지 검증한다 — S15P21E201-161.
 *
 * <p>🔴 <b>일부러 {@code Assumptions}/{@code @EnabledIf} 를 쓰지 않는다.</b> 이 티켓의 완료
 * 기준이 정확히 그 반대를 요구한다 — "라이브러리를 일부러 빼면 그 시험이 빨갛게 실패한다".
 * backend:build 는 건너뛴 테스트가 하나라도 있으면 빌드를 실패시키는 별도 게이트가 있다
 * (S15P21E201-266 후속, {@code S3FileStorageTest} 의 같은 절 참고) — 그 게이트와 이 테스트는
 * 목적이 다르다. 저 게이트는 "건너뜀이 조용한 통과가 되지 않게" 막고, 이 테스트는 "환경 자체가
 * 갖춰졌는지" 를 직접 확인한다. 그래서 이 테스트는 Spring 컨텍스트 없이(느리지 않게) 직접
 * {@link RouteOptimizerAvailabilityCheck#tryInvoke()} 를 부른다.
 *
 * <p>실행 파일 경로는 {@code GABOLLE_ROUTE_OPTIMIZER_PYTHON}(+{@code _SCRIPT}) 환경변수를
 * 먼저 본다 — CI 는 시스템 {@code python3}(3.14, ortools 휠이 없다) 대신 uv 로 받은 3.13 을
 * 이 변수로 가리킨다(.gitlab-ci.yml 의 backend:build). 로컬 개발 PC 에서 이 변수가 없으면
 * 시스템 {@code python3}(+ 로컬에 직접 설치한 ortools)를 쓴다 — 없으면 이 테스트가 그대로
 * 빨갛게 실패하는 것이 맞다: 로컬에서도 이 환경은 갖춰야 한다.
 */
class RouteOptimizerAvailabilityCheckTest {

	@Test
	void tryInvokeSucceedsWhenOrtoolsIsInstalled() throws Exception {
		RouteOptimizerProperties properties = new RouteOptimizerProperties();
		String python = setting("GABOLLE_ROUTE_OPTIMIZER_PYTHON");
		if (python != null) {
			properties.setPythonExecutable(python);
		}
		String script = setting("GABOLLE_ROUTE_OPTIMIZER_SCRIPT");
		if (script != null) {
			properties.setScriptPath(script);
		}

		RouteOptimizerAvailabilityCheck check = new RouteOptimizerAvailabilityCheck(properties, new ObjectMapper());

		RouteOptimizerAvailabilityCheck.TrialOutcome outcome = check.tryInvoke();

		assertThat(outcome.available()).as("실행 파일=%s, 스크립트=%s, 이유=%s",
				properties.getPythonExecutable(), properties.getScriptPath(), outcome.detail()).isTrue();
	}

	/** 환경변수를 먼저 보고, 없으면 같은 이름의 시스템 프로퍼티({@code -D})를 본다. */
	private static String setting(String key) {
		String value = System.getenv(key);
		if (value == null) {
			value = System.getProperty(key);
		}
		return (value == null || value.isBlank()) ? null : value;
	}
}
