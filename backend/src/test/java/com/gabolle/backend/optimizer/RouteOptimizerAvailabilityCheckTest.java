package com.gabolle.backend.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/**
 * 파이썬 경로 최적화(OR-Tools) 실행 환경이 실제로 갖춰졌는지 검증한다.
 *
 * 일부러 {@code Assumptions}/{@code @EnabledIf} 를 쓰지 않는다 — 라이브러리가 빠지면 건너뛰는
 * 것이 아니라 빨갛게 실패해야 한다. Spring 컨텍스트 없이
 * {@link RouteOptimizerAvailabilityCheck#tryInvoke()} 를 직접 부른다.
 *
 * 실행 파일 경로는 {@code GABOLLE_ROUTE_OPTIMIZER_PYTHON}(+{@code _SCRIPT}) 환경변수를 먼저
 * 본다 — CI 는 시스템 {@code python3} 대신 uv 로 받은 3.13 을 이 변수로 가리킨다. 변수가
 * 없으면 시스템 {@code python3} 를 쓴다.
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
