package com.gabolle.backend.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 보안 이벤트가 짧은 시간에 급증하면(무차별 대입 의심) MatterMost 로 알린다. 종류별로 최근 발생 시각을
 * 큐에 쌓고 {@link SecurityAlertProperties#getWindow()} 보다 오래된 것은 버린 뒤, 남은 개수가
 * {@link SecurityAlertProperties#getThreshold()} 이상이면 급증으로 본다.
 *
 * <p>창이 메모리에만 있으므로 단일 인스턴스 배포를 전제로 한 잠정 구현이다. 서버를 재시작하면 창이
 * 비고, 인스턴스를 여러 대로 늘리면 각 인스턴스가 따로 세어 아무도 혼자서는 임계치를 못 넘을 수 있다.
 *
 * <p>한 번 보낸 뒤 {@link SecurityAlertProperties#getCooldown()} 동안은 같은 종류의 경보를 다시 보내지
 * 않는다 — 공격이 계속되는 동안 매 요청마다 알림이 가면 사람이 결국 알림을 끈다.
 *
 * <p>웹훅 주소가 없거나 전송이 실패해도 예외를 던지지 않는다. 원래 요청의 결과가 알림 실패 때문에
 * 바뀌면 안 된다.
 */
@Component
public class SecurityAlertNotifier {

	private static final Logger log = LoggerFactory.getLogger(SecurityAlertNotifier.class);

	// 웹훅 하나가 느리다고 로그인 같은 원래 요청까지 오래 걸리면 안 된다. 짧게 끊는다.
	private static final Duration WEBHOOK_CONNECT_TIMEOUT = Duration.ofSeconds(2);
	private static final Duration WEBHOOK_READ_TIMEOUT = Duration.ofSeconds(3);

	private final SecurityAlertProperties properties;
	private final RestClient restClient;
	private final Clock clock;

	private final Map<SecurityEvent, Deque<Instant>> recentOccurrences = new ConcurrentHashMap<>();
	private final Map<SecurityEvent, Instant> lastAlertSentAt = new ConcurrentHashMap<>();

	public SecurityAlertNotifier(SecurityAlertProperties properties, RestClient.Builder restClientBuilder, Clock clock) {
		this.properties = properties;
		this.restClient = restClientBuilder.requestFactory(timeoutRequestFactory()).build();
		this.clock = clock;
	}

	private static ClientHttpRequestFactory timeoutRequestFactory() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(WEBHOOK_CONNECT_TIMEOUT);
		requestFactory.setReadTimeout(WEBHOOK_READ_TIMEOUT);
		return requestFactory;
	}

	/**
	 * {@code synchronized} 를 풀면 동시에 임계치를 넘는 두 요청이 경보를 두 번 보낸다 — cooldown 을
	 * 세우는 시점 자체가 겹치기 때문이다. 보안 이벤트는 드물어 잠금이 병목이 되지 않는다.
	 */
	public synchronized void recordAndMaybeAlert(SecurityEvent event) {
		Instant now = this.clock.instant();
		int count = recordOccurrence(event, now);
		if (count < this.properties.getThreshold()) {
			return;
		}
		if (isWithinCooldown(event, now)) {
			return;
		}
		this.lastAlertSentAt.put(event, now);
		send(event, count);
	}

	private int recordOccurrence(SecurityEvent event, Instant now) {
		Deque<Instant> occurrences = this.recentOccurrences.computeIfAbsent(event, key -> new ArrayDeque<>());
		occurrences.addLast(now);
		Instant windowStart = now.minus(this.properties.getWindow());
		while (!occurrences.isEmpty() && occurrences.peekFirst().isBefore(windowStart)) {
			occurrences.pollFirst();
		}
		return occurrences.size();
	}

	private boolean isWithinCooldown(SecurityEvent event, Instant now) {
		Instant lastSentAt = this.lastAlertSentAt.get(event);
		return lastSentAt != null && lastSentAt.plus(this.properties.getCooldown()).isAfter(now);
	}

	private void send(SecurityEvent event, int count) {
		String webhookUrl = this.properties.getWebhookUrl();
		if (webhookUrl == null || webhookUrl.isBlank()) {
			// 이 기능이 웹훅 하나 때문에 서버를 못 뜨게 하면 안 되므로 예외를 던지지 않는다.
			log.warn("event=SECURITY_ALERT_SUPPRESSED reason=NO_WEBHOOK_CONFIGURED alertEvent={} windowCount={}",
					event, count);
			return;
		}
		try {
			this.restClient.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("text", alertText(event, count)))
					.retrieve().toBodilessEntity();
			log.warn("event=SECURITY_ALERT_SENT alertEvent={} windowCount={}", event, count);
		}
		catch (RestClientException exception) {
			// 여기서 던지지 않는다. 부른 쪽의 원래 응답이 MatterMost 가 안 되어서 바뀌면 안 된다.
			log.error("event=SECURITY_ALERT_SEND_FAILED alertEvent={} windowCount={} reason={}", event, count,
					exception.getClass().getSimpleName());
		}
	}

	private String alertText(SecurityEvent event, int count) {
		return "[GABOLLE 보안 경보] " + event + " 가 최근 " + this.properties.getWindow().toMinutes() + "분 동안 "
				+ count + "건 발생했습니다 (" + event.getDescription() + "). 무차별 대입 의심 — 확인이 필요합니다.";
	}
}
