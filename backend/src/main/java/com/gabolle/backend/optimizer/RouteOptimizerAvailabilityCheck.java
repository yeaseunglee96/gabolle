package com.gabolle.backend.optimizer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 경로 최적화(OR-Tools) 파이썬 프로그램을 기동 시점에 한 번 시험 삼아 불러 본다 — S15P21E201-161.
 *
 * <p>🔴 <b>조용히 안 도는 것이 가장 위험하다.</b> 이 파이썬 프로그램이 없어도(혹은 ortools 가
 * 안 깔려도) 자바 서버는 컴파일 오류도 기동 실패도 없이 뜬다 — 실제 경로 최적화 요청이 올 때만
 * 서브프로세스 실행이 실패하고, 그 순간 서버는 대체 계산(직선/그리디 등)으로 조용히 넘어간다.
 * 그래서 기동 시점에 <b>먼저 한 번 불러 보고</b>, 안 되면 <b>경고를 기록</b>한다 — 서버는 그래도
 * 뜬다({@code BaselineEngineStartupValidator} 와 달리 여기서는 죽이지 않는다. 경로 최적화가
 * 없어도 대체 계산으로 서비스는 계속되기 때문이다 — 배선이 아예 빠진 추천 엔진과는 위험도가
 * 다르다).
 *
 * <p>{@link #tryInvoke()} 는 이 클래스의 핵심이자 CI 검사의 핵심이기도 하다 — {@code
 * RouteOptimizerAvailabilityCheckTest} 가 이 메서드를 <b>예외를 삼키지 않고</b> 직접 불러
 * ortools 가 실제로 깔려 있는지를 검증한다. backend:build 는 건너뛴 테스트를 빌드 실패로 본다
 * (S15P21E201-266 후속) — 그래서 이 클래스의 시험 호출과 그 테스트의 시험 호출은 <b>같은
 * 메서드</b>를 쓰되, 기동 경로는 실패를 삼켜 경고로 낮추고 테스트 경로는 그대로 올려 보낸다.
 */
@Component
@Profile({ "db", "dev" })
public class RouteOptimizerAvailabilityCheck {

	private static final Logger log = LoggerFactory.getLogger(RouteOptimizerAvailabilityCheck.class);

	/** 실제 동선을 풀지 않는다 — {@code route_optimizer.py} 는 빈 목록을 즉시 {@code OPTIMAL} 로 되돌린다. */
	private static final String TRIAL_PAYLOAD = "{\"places\": []}";

	private final RouteOptimizerProperties properties;

	private final ObjectMapper objectMapper;

	public RouteOptimizerAvailabilityCheck(RouteOptimizerProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	void checkOnStartup() {
		try {
			TrialOutcome outcome = tryInvoke();
			if (outcome.available()) {
				log.info("경로 최적화(OR-Tools) 실행 환경 확인됨 — {}", this.properties.getPythonExecutable());
			} else {
				log.warn(
						"경로 최적화(OR-Tools) 실행 환경을 못 찾았다 — 실제 요청이 오면 대체 계산으로 돈다. "
								+ "실행 파일={}, 스크립트={}, 이유={}",
						this.properties.getPythonExecutable(), this.properties.getScriptPath(), outcome.detail());
			}
		} catch (Exception ex) {
			log.warn(
					"경로 최적화(OR-Tools) 기동 시험 호출 중 예외 — 실제 요청이 오면 대체 계산으로 돈다. 실행 파일={}, 스크립트={}",
					this.properties.getPythonExecutable(), this.properties.getScriptPath(), ex);
		}
	}

	/**
	 * 시험 호출을 한 번 하고 결과를 그대로 돌려준다. 예외를 삼키지 않는다 — 삼키는 것은
	 * {@link #checkOnStartup()} 의 몫이다.
	 */
	TrialOutcome tryInvoke() throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(this.properties.getPythonExecutable(), this.properties.getScriptPath());
		Process process = builder.start();
		try (OutputStream stdin = process.getOutputStream()) {
			stdin.write(TRIAL_PAYLOAD.getBytes(StandardCharsets.UTF_8));
		}
		boolean finished = process.waitFor(this.properties.getTimeoutSeconds(), TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			return TrialOutcome.unavailable("시간 초과(" + this.properties.getTimeoutSeconds() + "초)");
		}
		String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (process.exitValue() != 0) {
			String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
			return TrialOutcome.unavailable("종료 코드 " + process.exitValue()
					+ (stderr.isBlank() ? "" : " — " + stderr.strip()));
		}
		JsonNode node = this.objectMapper.readTree(stdout);
		String status = node.path("status").asText("");
		if (!"OPTIMAL".equals(status)) {
			return TrialOutcome.unavailable("예상 밖 응답: " + stdout.strip());
		}
		return TrialOutcome.success();
	}

	/**
	 * 시험 호출 결과. {@code available} 이 거짓이면 {@code detail} 에 사람이 읽을 이유가 담긴다.
	 *
	 * <p>🔴 정적 팩토리 이름이 {@code success}/{@code unavailable} 인 이유 — 레코드 컴포넌트
	 * {@code available} 과 이름이 겹치면 자동 생성 접근자({@code boolean available()})와
	 * 충돌해 컴파일이 안 된다(반환 타입만 다른 오버로드는 자바가 허용하지 않는다).
	 */
	record TrialOutcome(boolean available, String detail) {

		static TrialOutcome success() {
			return new TrialOutcome(true, "OPTIMAL");
		}

		static TrialOutcome unavailable(String detail) {
			return new TrialOutcome(false, detail);
		}
	}
}
