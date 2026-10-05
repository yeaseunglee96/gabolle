package com.gabolle.backend.trip.infra;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 Idempotency-Key 를 재시도로 보는 시간은 10분(S15P21E201-1995). 앱 키가 여행 조건 해시라,
 * 창이 없으면 같은 조건으로 며칠 뒤 새로 만들어도 옛 여행이 열렸다(10/5 녹화 9번).
 */
class IdempotencyWindowTest {

	private static final Instant NOW = Instant.parse("2026-10-06T01:00:00Z");

	@Test
	@DisplayName("🔴 하루 전에 묶인 키는 낡았다 — 새 여행")
	void dayOldKeyIsStale() {
		assertThat(JpaTripRepository.isStale(OffsetDateTime.ofInstant(NOW.minusSeconds(86_400), ZoneOffset.UTC), NOW)).isTrue();
		assertThat(JpaTripRepository.isStale(Timestamp.from(NOW.minusSeconds(3_600)), NOW)).isTrue();
	}

	@Test
	@DisplayName("몇 초 전에 묶인 키는 재시도 — 옛 여행을 돌려준다")
	void recentKeyIsRetry() {
		assertThat(JpaTripRepository.isStale(OffsetDateTime.ofInstant(NOW.minusSeconds(5), ZoneOffset.UTC), NOW)).isFalse();
		assertThat(JpaTripRepository.isStale(NOW.minusSeconds(599), NOW)).isFalse();
	}

	@Test
	@DisplayName("묶인 시각을 모르면 예전처럼 재시도로 본다")
	void unknownTimeKeepsOldBehaviour() {
		assertThat(JpaTripRepository.isStale(null, NOW)).isFalse();
	}
}
