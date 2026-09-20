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
 * 경로 최적화(OR-Tools) 파이썬 프로그램을 기동 시점에 한 번 시험 삼아 불러 본다.
 * 이 프로그램이 없어도(ortools 가 안 깔려도) 자바 서버는 기동 실패 없이 뜨고, 실제 요청이 올
 * 때 서브프로세스 실행이 실패해 대체 계산으로 조용히 넘어간다. 그래서 기동 때 먼저 불러 보고
 * 안 되면 경고만 남긴다 — 경로 최적화가 없어도 대체 계산으로 서비스는 계속되므로 서버를
 * 죽이지는 않는다.
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
	 * {@link #checkOnStartup()} 의 몫이고, {@code RouteOptimizerAvailabilityCheckTest} 는
	 * ortools 가 실제로 깔려 있는지 보려고 이 메서드를 그대로 부른다.
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
	 * 정적 팩토리 이름이 {@code success}/{@code unavailable} 인 것은, 레코드 컴포넌트
	 * {@code available} 과 이름이 겹치면 자동 생성 접근자와 충돌해 컴파일이 안 되기 때문이다.
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
