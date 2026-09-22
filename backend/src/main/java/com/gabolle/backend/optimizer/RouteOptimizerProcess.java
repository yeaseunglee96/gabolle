package com.gabolle.backend.optimizer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 파이썬 경로 최적화 프로그램을 한 번 부르고 표준출력을 그대로 돌려준다.
 * 표준입력에 JSON 을 주고 표준출력에서 JSON 을 받는 것이 전부다 — 그 JSON 이 무슨 뜻인지는
 * 부르는 쪽이 안다.
 *
 * <p>기동 시점 점검({@link RouteOptimizerAvailabilityCheck})과 실제 동선 계산
 * ({@link OrToolsRouteOrderAdapter})이 **같은 방식으로** 불러야 해서 여기 한 벌만 둔다.
 * 두 벌이면 한쪽만 고쳐졌을 때 "점검은 통과하는데 실제로는 안 되는" 상태가 생기고,
 * 그 상태는 기동 로그가 초록이라 아무도 모른다.
 *
 * <p>빈으로 만들지 않는다. 설정값 하나만 있으면 되고, 빈으로 만들면 두 클래스의 생성자가
 * 바뀌어 지금 도는 시험들이 같이 흔들린다.
 */
final class RouteOptimizerProcess {

	private final RouteOptimizerProperties properties;

	RouteOptimizerProcess(RouteOptimizerProperties properties) {
		this.properties = properties;
	}

	/**
	 * 한 번 부른다. 예외를 삼키지 않는다 — 삼킬지 말지는 부르는 쪽이 정한다.
	 *
	 * @param payloadJson 표준입력에 그대로 써 넣을 JSON
	 */
	Result run(String payloadJson) throws IOException, InterruptedException {
		ProcessBuilder builder = new ProcessBuilder(this.properties.getPythonExecutable(),
				this.properties.getScriptPath());
		Process process = builder.start();
		try (OutputStream stdin = process.getOutputStream()) {
			stdin.write(payloadJson.getBytes(StandardCharsets.UTF_8));
		}
		boolean finished = process.waitFor(this.properties.getTimeoutSeconds(), TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			return Result.failed("시간 초과(" + this.properties.getTimeoutSeconds() + "초)");
		}
		String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		if (process.exitValue() != 0) {
			String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
			return Result.failed("종료 코드 " + process.exitValue()
					+ (stderr.isBlank() ? "" : " — " + stderr.strip()));
		}
		return Result.of(stdout);
	}

	/**
	 * 부른 결과. {@code ok} 가 거짓이면 {@code detail} 에 사람이 읽을 이유가 담기고
	 * {@code stdout} 은 비어 있다.
	 */
	record Result(boolean ok, String stdout, String detail) {

		static Result of(String stdout) {
			return new Result(true, stdout, null);
		}

		static Result failed(String detail) {
			return new Result(false, "", detail);
		}
	}
}
