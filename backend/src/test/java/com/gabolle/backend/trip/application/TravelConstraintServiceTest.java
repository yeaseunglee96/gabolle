package com.gabolle.backend.trip.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.trip.infra.TravelConstraintJpaRepository;

/**
 * 계정 「여행 조건」 저장은 2026-09-25 부터 {@code constraint_set} 으로 적는다 (S15P21E201-1689) — 제약이지 취향이 아니다.
 * 전에는 {@code preference_set} 이었다(운영 기존 행은 그대로 둔다). 값은 싣지 않는다.
 */
class TravelConstraintServiceTest {

	@Test
	@DisplayName("🔴 여행 조건을 저장하면 constraint_set(scope ACCOUNT) — 값(JSON)은 싣지 않고 「값 있음」만")
	void savingTravelConstraintsRecordsAConstraintSetWithoutTheValue() {
		TravelConstraintJpaRepository repository = mock(TravelConstraintJpaRepository.class);
		EventIngestService events = mock(EventIngestService.class);
		when(repository.findById(any())).thenReturn(Optional.empty());
		when(repository.save(any())).thenAnswer((call) -> call.getArgument(0));
		TravelConstraintService service = new TravelConstraintService(repository, events,
				Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC));
		UUID userId = UUID.randomUUID();

		service.put(userId, "{\"stroller\": true, \"maxWalkingMeters\": 800}", "SAVED", null);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
		verify(events).recordFromServer(any(), eq(EventType.CONSTRAINT_SET), eq(1), eq(userId), isNull(), isNull(),
				payload.capture());
		assertThat(payload.getValue()).containsEntry("scope", "ACCOUNT").containsEntry("answer_status", "SAVED")
				.containsEntry("has_value", true);
		assertThat(payload.getValue().toString()).doesNotContain("stroller").doesNotContain("800");
	}
}
