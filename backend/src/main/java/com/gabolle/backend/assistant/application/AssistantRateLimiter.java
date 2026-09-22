package com.gabolle.backend.assistant.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.assistant.config.AssistantProperties;

/**
 * 사용자별 1분 요청 횟수를 센다. 사용자마다 최근 요청 시각을 쌓아 두고 1분보다 오래된 것만
 * 지우는 슬라이딩 윈도다.
 *
 * 메모리 카운터라 서버를 다시 시작하면 지워지고 인스턴스를 늘리면 인스턴스마다 따로 센다.
 * 여러 대로 늘리면 Redis 같은 공유 저장소로 옮겨야 한다.
 * 맵은 사용자 수만큼 계속 자란다.
 */
@Component
@Profile({ "db", "dev" })
public class AssistantRateLimiter {

	private final Map<UUID, Deque<Instant>> requestTimestampsByUser = new ConcurrentHashMap<>();

	private final AssistantProperties properties;

	private final Clock clock;

	public AssistantRateLimiter(AssistantProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	/** 최근 1분 안에 이미 한도만큼 불렀으면 AssistantRateLimitExceededException. */
	public void checkAndRecord(UUID userId) {
		Instant now = Instant.now(this.clock);
		Instant windowStart = now.minusSeconds(60);
		Deque<Instant> timestamps = this.requestTimestampsByUser.computeIfAbsent(userId,
				id -> new ConcurrentLinkedDeque<>());

		synchronized (timestamps) {
			while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
				timestamps.pollFirst();
			}
			if (timestamps.size() >= this.properties.getMaxRequestsPerMinute()) {
				throw new AssistantRateLimitExceededException(
						"1분에 " + this.properties.getMaxRequestsPerMinute() + "번까지만 물어볼 수 있어요. 잠시 후 다시 시도해 주세요.");
			}
			timestamps.addLast(now);
		}
	}
}
