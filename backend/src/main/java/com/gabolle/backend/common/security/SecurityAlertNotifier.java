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
 * 보안 이벤트가 짧은 시간에 급증하면(무차별 대입 의심) MatterMost 로 알린다 — S15P21E201-682.
 *
 * <h2>급증 판정 — 메모리 슬라이딩 윈도</h2>
 *
 * {@link SecurityEvent} 종류별로 최근 발생 시각을 큐에 쌓아 두고, {@link SecurityAlertProperties#getWindow()}
 * 보다 오래된 것은 버린다. 남은 개수가 {@link SecurityAlertProperties#getThreshold()} 이상이면 급증으로 본다.
 * DB 표를 두지 않는다 — 이 티켓은 마이그레이션을 넣지 않는다.
 *
 * <p>🔴 <b>이 판정의 한계</b>: 메모리에만 있으므로 서버가 재시작되면 창이 비고(재시작 직후는 급증을
 * 못 본 것처럼 시작한다), 인스턴스를 여러 대로 늘리면 <b>각 인스턴스가 따로 센다</b> — 인스턴스 3대가
 * 요청을 나눠 받으면 실제로는 임계치를 넘었는데 어느 인스턴스도 혼자서는 못 넘을 수 있다. 여러
 * 인스턴스가 같은 창을 공유하려면 Redis 같은 공유 저장소가 필요한데, 그러면 이 티켓 범위를 넘는
 * 인프라 변경이 된다. 지금은 단일 인스턴스 배포를 전제로 한 잠정 구현이다.
 *
 * <h2>중복 억제 — cooldown</h2>
 *
 * 무차별 대입이 계속되는 동안 매 요청마다 알림이 가면 채널이 마비되고 사람이 결국 알림을 끈다.
 * 한 번 보낸 뒤 {@link SecurityAlertProperties#getCooldown()} 동안은 같은 {@link SecurityEvent}
 * 종류의 경보를 다시 보내지 않는다.
 *
 * <h2>웹훅이 없을 때 · 실패했을 때</h2>
 *
 * 웹훅 주소가 비어 있으면(팀에 아직 값이 없다) 조용히 로그로만 남기고 예외를 던지지 않는다 —
 * 그것 때문에 서버 기동이 막히면 안 된다. 전송이 실패해도(주소가 닿지 않거나 타임아웃) 예외를
 * 삼킨다 — 로그인 실패 응답 같은 <b>원래 요청의 결과가 알림 실패 때문에 바뀌면 안 된다.</b>
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
	 * 이 사건이 한 번 더 일어났다고 기록하고, 최근 윈도 안 건수가 임계치를 넘었으면(그리고
	 * cooldown 중이 아니면) MatterMost 로 알린다.
	 *
	 * <p>이 메서드는 통째로 {@code synchronized} 다. 보안 이벤트는 로그인 실패·인가 실패처럼
	 * 드물게(요청마다 한 번) 일어나는 것이라 잠금이 병목이 되지 않는다. 반대로 잠금 없이 두면
	 * 동시에 임계치를 넘는 두 요청이 경보를 두 번 보낼 수 있다 — cooldown 이 있어도 그 cooldown
	 * 자체를 세우는 시점이 겹치면 뚫린다.
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
			// 웹훅 주소가 아직 없다(다른 작업이 같은 값을 기다리는 중이다). 예외를 던지지 않는다 —
			// 이 기능이 웹훅 하나 때문에 서버를 못 뜨게 하면 안 된다.
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
			// 🔴 여기서 던지지 않는다. 이 메서드를 부른 쪽(로그인·인가 실패 처리)의 원래 응답이
			//    MatterMost 가 안 되어서 바뀌면 안 된다.
			log.error("event=SECURITY_ALERT_SEND_FAILED alertEvent={} windowCount={} reason={}", event, count,
					exception.getClass().getSimpleName());
		}
	}

	private String alertText(SecurityEvent event, int count) {
		return "[GABOLLE 보안 경보] " + event + " 가 최근 " + this.properties.getWindow().toMinutes() + "분 동안 "
				+ count + "건 발생했습니다 (" + event.getDescription() + "). 무차별 대입 의심 — 확인이 필요합니다.";
	}
}
