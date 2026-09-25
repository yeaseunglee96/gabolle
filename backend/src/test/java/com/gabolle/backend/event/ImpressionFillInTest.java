package com.gabolle.backend.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.event.application.BehaviorConsent;
import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.application.ImpressionFacts;
import com.gabolle.backend.event.application.OutboxAppendCommand;
import com.gabolle.backend.event.application.OutboxService;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.repository.AppUserRepository;

/**
 * 노출 이벤트는 앱이 요청 번호 · 장소 번호 · 화면 이름만 보내고, 순위 · 이유 코드 · 판은 서버가 채운다 (S15P21E201-1689).
 * 그리고 행동 기반 개인화를 안 켠 사람의 노출도 적힌다 — 추천 품질을 재는 기록이다.
 */
class ImpressionFillInTest {

	private static final Instant NOW = Instant.parse("2026-09-25T12:00:00Z");

	private final UUID requestId = UUID.randomUUID();

	private final UUID placeId = UUID.randomUUID();

	private OutboxService outboxService;

	private EventIngestService service;

	@BeforeEach
	void setUp() {
		this.outboxService = mock(OutboxService.class);
		AppUserRepository users = mock(AppUserRepository.class);
		given(users.findPersonalizationMode(any())).willReturn(Optional.of(PersonalizationMode.EXPLICIT_ONLY));
		given(this.outboxService.appendReportingDuplicate(any()))
				.willReturn(OutboxService.AppendResult.stored(mock(EventOutbox.class)));
		this.service = new EventIngestService(this.outboxService, new BehaviorConsent(users),
				Clock.fixed(NOW, ZoneOffset.UTC));
		ImpressionFacts facts = (request, place) -> request.equals(this.requestId) && place.equals(this.placeId)
				? Optional.of(new ImpressionFacts.Facts(3, List.of("NEAR_ORIGIN", "TAG_MATCH_INTEREST"), "BASELINE",
						"model-7", "feature-2", "ontology-1", "dataset-9", "policy-4"))
				: Optional.empty();
		this.service.setImpressionFacts(facts);
	}

	@Test
	@DisplayName("🔴 요청 번호 · 장소 번호 · 화면만 보내면 서버가 순위 · 이유 · 판을 채운다 — 개인화를 안 켠 사람도 적힌다")
	void serverFillsRankReasonsAndVersions() {
		EventIngestService.Outcome outcome = ingest(Map.of("placeId", this.placeId.toString(), "sourceScreen",
				"TRIP_ITINERARY"));

		assertThat(outcome).isEqualTo(EventIngestService.Outcome.STORED);
		Map<String, Object> payload = capturedPayload();
		assertThat(payload).containsEntry("finalRank", 3)
				.containsEntry("reasonCodes", List.of("NEAR_ORIGIN", "TAG_MATCH_INTEREST"))
				.containsEntry("fallbackMode", "BASELINE").containsEntry("modelVersion", "model-7")
				.containsEntry("datasetVersion", "dataset-9").containsEntry("policyVersion", "policy-4");
	}

	@Test
	@DisplayName("앱이 보낸 값은 덮지 않는다")
	void clientValuesAreKept() {
		ingest(Map.of("placeId", this.placeId.toString(), "finalRank", 1));

		assertThat(capturedPayload().get("finalRank")).isEqualTo(1);
	}

	@Test
	@DisplayName("그 요청이 그 장소를 낸 기록이 없으면 채우지 않고 그대로 적는다")
	void unknownPlaceIsStoredAsSent() {
		ingest(Map.of("placeId", UUID.randomUUID().toString()));

		assertThat(capturedPayload()).doesNotContainKey("finalRank").doesNotContainKey("modelVersion");
	}

	private EventIngestService.Outcome ingest(Map<String, Object> payload) {
		return this.service.ingestFromClient(UUID.randomUUID(), EventType.RECOMMENDATION_IMPRESSION, 1,
				UUID.randomUUID(), UUID.randomUUID(), this.requestId, OffsetDateTime.parse("2026-09-25T11:59:00Z"),
				payload);
	}

	private Map<String, Object> capturedPayload() {
		ArgumentCaptor<OutboxAppendCommand> captor = ArgumentCaptor.forClass(OutboxAppendCommand.class);
		verify(this.outboxService).appendReportingDuplicate(captor.capture());
		return captor.getValue().payload();
	}
}
