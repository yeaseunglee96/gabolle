package com.gabolle.backend.notification.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Expo 푸시 발송 설정 — S15P21E201-1391.
 *
 * <p>🔴 {@code enabled} 가 꺼져 있으면 실제로 안 보내고 로그만 남긴다
 * ({@code LoggingPushSender}). 기본값이 꺼짐인 이유는 <b>알림은 되돌릴 수 없기 때문</b>이다 —
 * 잘못 만든 문구로 메일을 보내면 사람이 안 열면 그만이지만, 푸시는 남의 잠금화면에 이미 떴다.
 * 켜는 것은 배포 환경의 결정이지 코드의 기본값이 아니다.
 *
 * <p>Expo 푸시에는 열쇠가 없다. 토큰을 아는 쪽이 보낼 수 있고, 토큰은 우리 표에만 있다.
 * (Expo 계정에 «Enhanced Security» 를 켜면 접근 표가 필요해진다. 지금은 안 켜져 있다.)
 */
@ConfigurationProperties(prefix = "gabolle.push")
public class PushProperties {

	/** 실제로 보낼 것인가. 꺼져 있으면 로그만 남긴다. */
	private boolean enabled = false;

	private String baseUrl = "https://exp.host/--/api/v2/push/send";

	/**
	 * 한 번에 보낼 기기 수. Expo 가 정한 상한이 100 이다.
	 *
	 * <p>이 값을 문서에서 읽고 박은 것이지 재 본 것이 아니다. 넘기면 Expo 가 거절하므로
	 * 낮추는 것은 안전하고 올리는 것은 아니다.
	 */
	private int batchSize = 100;

	private Duration connectTimeout = Duration.ofSeconds(2);

	/**
	 * 응답을 기다리는 한도.
	 *
	 * <p>커밋 뒤에 도는 자리라 요청을 붙잡지는 않지만, 무한정 기다리면 그 스레드가 안 돌아온다.
	 */
	private Duration readTimeout = Duration.ofSeconds(5);

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public int getBatchSize() {
		return this.batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public Duration getConnectTimeout() {
		return this.connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Duration getReadTimeout() {
		return this.readTimeout;
	}

	public void setReadTimeout(Duration readTimeout) {
		this.readTimeout = readTimeout;
	}
}
