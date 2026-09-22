package com.gabolle.backend.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 보안 이벤트 급증 알림이 쓰는 설정. 다른 설정 클래스와 달리 전용 {@code @Configuration} 없이
 * {@code @Component} 로 직접 등록한다.
 *
 * <p>웹훅 주소의 기본값은 빈 문자열이다. 값이 없으면 {@link SecurityAlertNotifier} 가 경보를 로그로만
 * 남기고 넘어간다 — 기동을 막지 않는다.
 */
@Component
@ConfigurationProperties(prefix = "gabolle.security.alert")
public class SecurityAlertProperties {

	/** MatterMost 수신 웹훅 URL. 비어 있으면 알림을 보내지 않는다. */
	private String webhookUrl = "";

	/** 슬라이딩 윈도(최근 N분 동안의 건수만 세는 것)의 길이. */
	private Duration window = Duration.ofMinutes(5);

	/** 이 윈도 안에 이 건수 이상 쌓이면 급증(무차별 대입 의심)으로 본다. */
	private int threshold = 20;

	/** 한 번 보낸 뒤 같은 {@link SecurityEvent} 종류의 경보를 다시 보내지 않는 시간(cooldown). */
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
