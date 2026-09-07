package com.gabolle.backend.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 보안 이벤트 급증 알림(S15P21E201-682)이 쓰는 설정.
 *
 * <h2>🔴 왜 이 클래스 자체에 {@code @Component} 를 붙였나</h2>
 *
 * 이 저장소의 다른 설정 클래스(예: {@code AuthProperties}·{@code PlaceProperties})는 전용
 * {@code @Configuration} 클래스에 {@code @EnableConfigurationProperties} 를 붙여 등록한다. 이
 * 티켓은 만들 파일 목록이 {@code common/security} 안으로 정해져 있어 새 {@code @Configuration}
 * 클래스를 추가하지 않는다. {@code @Component} 를 직접 붙이는 것도 스프링 부트가 지원하는
 * 동등한 방법이다 — 컴포넌트 스캔이 이 클래스를 찾아 {@code @ConfigurationProperties} 바인딩을
 * 그대로 적용한다.
 *
 * <h2>🔴 웹훅 주소의 기본값이 빈 문자열인 이유</h2>
 *
 * 이 값을 만들어 낼 수 없다(다른 작업이 같은 값을 기다리는 중이다). 값이 없으면
 * {@link SecurityAlertNotifier} 가 경보를 로그로만 남기고 조용히 넘어간다 — 기동을 막지 않는다.
 */
@Component
@ConfigurationProperties(prefix = "gabolle.security.alert")
public class SecurityAlertProperties {

	/** MatterMost 수신 웹훅 URL. 비어 있으면 알림을 보내지 않는다. */
	private String webhookUrl = "";

	/**
	 * 슬라이딩 윈도(최근 N분 동안의 건수만 세는 것)의 길이.
	 *
	 * <p>티켓에 값이 정해져 있지 않아 권고값(5분)을 기본으로 둔다.
	 */
	private Duration window = Duration.ofMinutes(5);

	/** 이 윈도 안에 이 건수 이상 쌓이면 급증(무차별 대입 의심)으로 본다. */
	private int threshold = 20;

	/**
	 * 같은 종류의 경보를 다시 보내지 않는 시간(cooldown).
	 *
	 * <p>공격이 계속되는 동안 매 요청마다 알림이 가면 채널이 마비되고 결국 사람이 알림을 끈다.
	 * 한 번 보낸 뒤에는 이 시간 동안 같은 {@link SecurityEvent} 종류의 경보를 다시 보내지 않는다.
	 */
	private Duration cooldown = Duration.ofMinutes(10);

	public String getWebhookUrl() {
		return this.webhookUrl;
	}

	public void setWebhookUrl(String webhookUrl) {
		this.webhookUrl = webhookUrl == null ? "" : webhookUrl;
	}

	public Duration getWindow() {
		return this.window;
	}

	public void setWindow(Duration window) {
		this.window = window;
	}

	public int getThreshold() {
		return this.threshold;
	}

	public void setThreshold(int threshold) {
		this.threshold = threshold;
	}

	public Duration getCooldown() {
		return this.cooldown;
	}

	public void setCooldown(Duration cooldown) {
		this.cooldown = cooldown;
	}
}
