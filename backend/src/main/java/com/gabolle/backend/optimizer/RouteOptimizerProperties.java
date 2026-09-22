package com.gabolle.backend.optimizer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 경로 최적화(하루 동선 순서를 정하는 OR-Tools 프로그램) 실행 설정.
 * 이 프로그램은 자바가 아니라 파이썬이다({@code backend/solver/route_optimizer.py}) — 서버가
 * 서브프로세스로 불러 표준입력에 JSON 을 주고 표준출력에서 JSON 을 받는다.
 * 설정이 아예 없는 슬라이스 테스트에서도 바인딩이 실패하지 않도록 필드 기본값을 직접 넣는다.
 */
@ConfigurationProperties("gabolle.route-optimizer")
public class RouteOptimizerProperties {

	/**
	 * 부를 파이썬 실행 파일. 배포 이미지에서는 {@code /usr/local/python3.13/bin/python3.13}
	 * (Dockerfile 참고) — 실행 이미지의 apt 가 주는 python3(3.14)에는 ortools 휠이 없어서
	 * 별도 스테이지에서 만든 3.13 을 그대로 복사해 쓴다.
	 */
	private String pythonExecutable = "python3";

	/** 부를 스크립트 경로. 상대경로면 서버 작업 디렉터리(보통 {@code backend/}) 기준. */
	private String scriptPath = "solver/route_optimizer.py";

	/** 시험 호출(그리고 실제 호출)이 이 시간을 넘기면 실패로 본다. */
	private long timeoutSeconds = 5;

	public String getPythonExecutable() {
		return this.pythonExecutable;
	}

	public void setPythonExecutable(String pythonExecutable) {
		this.pythonExecutable = pythonExecutable;
	}

	public String getScriptPath() {
		return this.scriptPath;
	}

	public void setScriptPath(String scriptPath) {
		this.scriptPath = scriptPath;
	}

	public long getTimeoutSeconds() {
		return this.timeoutSeconds;
	}

	public void setTimeoutSeconds(long timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}
}
