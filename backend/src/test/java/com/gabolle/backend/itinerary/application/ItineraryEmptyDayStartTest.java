package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 빈 날에 첫 곳을 넣을 때 시작 시각 — 활동 시작 + 출발지에서 가는 이동 (S15P21E201-2043). */
class ItineraryEmptyDayStartTest {

	@Test
	@DisplayName("🔴 서면역→해운대 45분이면 09:00 이 아니라 09:45 에 닿는다")
	void addsOriginLeg() {
		assertThat(ItineraryEditService.arrivalAfterOriginLeg(LocalTime.of(9, 0), 45)).isEqualTo(LocalTime.of(9, 45));
	}

	@Test
	@DisplayName("이동을 모르면 활동 시작 그대로다 — 지어내지 않는다")
	void unknownLegKeepsStart() {
		assertThat(ItineraryEditService.arrivalAfterOriginLeg(LocalTime.of(9, 0), null)).isEqualTo(LocalTime.of(9, 0));
	}

	@Test
	@DisplayName("자정을 넘기면 되감지 않는다")
	void midnightGuard() {
		assertThat(ItineraryEditService.arrivalAfterOriginLeg(LocalTime.of(23, 30), 45)).isEqualTo(LocalTime.of(23, 30));
	}
}
