package com.gabolle.backend.recommendation.support;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftPort;
import com.gabolle.backend.recommendation.application.port.ItineraryHandle;
import com.gabolle.backend.recommendation.application.port.ItineraryRevisionCommand;
import com.gabolle.backend.recommendation.application.port.ItineraryRevisionDraft;

/**
 * 테스트용 대역(fake) — S15P21E201-604.
 *
 * <p>🔴 {@code RecommendationSliceApplication} 은 {@code itinerary} 패키지를 스캔하지
 * 않는다(그 패키지에 다른 슬라이스와 겹치지 않는 이유가 {@code RecommendationSliceApplication}
 * 주석에 있다). 그런데 {@code RecommendationService} 는 {@code ITINERARY_GENERATION} 이고
 * 반환할 후보가 있으면 {@code ItineraryDraftPort} 가 <b>반드시</b> 있어야 성공한다 — 없으면
 * {@code ERROR_ITINERARY_PORT_NOT_CONFIGURED} 로 실패한다({@code RecommendationEnginePort}
 * 가 없을 때와 같은 판단). 이 대역이 없으면 {@code RecommendationLoggingIntegrationTest} 의
 * 성공 경로 테스트들이 전부 그 실패로 빨개진다.
 *
 * <p>{@link #persist} 는 원시 SQL 로 최소한의 실제 행을 넣는다 — 이 판(V20260905120000)이
 * {@code recommendation_job.itinerary_id} 에 FK 를 걸어서, 가짜 UUID 를 그냥 붙이면 그
 * FK 위반으로 저장 자체가 실패한다.
 */
public class FakeItineraryDraftPort implements ItineraryDraftPort {

	private final JdbcTemplate jdbcTemplate;

	public FakeItineraryDraftPort(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public ItineraryDraft assemble(ItineraryDraftCommand command) {
		// 실제 여행 기간·출발지 계산은 ItineraryDraftServiceTest(itinerary 슬라이스)가 본다.
		// 여기서는 추천 로깅 테스트가 필요로 하는 "받은 장소만큼 항목이 생긴다"만 지킨다.
		List<ItineraryDraft.DraftItem> items = new ArrayList<>();
		int sequence = 1;
		for (ItineraryDraftCommand.PlannedPlace place : command.places()) {
			items.add(new ItineraryDraft.DraftItem(0, LocalDate.now(), sequence++, place.placeId(),
					UUID.randomUUID(), null, null, null, "UNKNOWN", place.reasonCodes(), place.warningCodes()));
		}
		return new ItineraryDraft(command.tripId(), command.userId(), command.requestId(),
				command.modelVersion(), command.featureVersion(), command.ontologyVersion(),
				command.policyVersion(), command.datasetVersion(), items, List.of(), List.of());
	}

	@Override
	public ItineraryHandle persist(ItineraryDraft draft) {
		UUID itineraryId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();

		this.jdbcTemplate.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				itineraryId, UUID.fromString(draft.tripId()), now);

		this.jdbcTemplate.update("""
				INSERT INTO itinerary_versions
				    (itinerary_version_id, itinerary_id, version, operation, created_by, request_id,
				     source_request_id, created_at)
				VALUES (?, ?, 1, 'CREATE', ?, ?, ?, ?)
				""", UUID.randomUUID(), itineraryId, UUID.fromString(draft.userId()),
				draft.requestId().toString(), draft.requestId(), now);

		return new ItineraryHandle(itineraryId.toString(), 1);
	}

	/**
	 * 🔴 S15P21E201-249 — 추천 슬라이스 테스트는 편집 Job 을 돌리지 않는다. 하루 재계산의 진짜
	 * 구현은 일정 슬라이스({@code ItineraryRecalculationIntegrationTest})에서 검증한다. 여기서
	 * 조용히 빈 초안을 돌려주면 그 슬라이스에서 "성공한 것처럼" 보이는 거짓 초록이 생기므로
	 * 시끄럽게 실패한다.
	 */
	@Override
	public ItineraryRevisionDraft revise(ItineraryRevisionCommand command) {
		throw new UnsupportedOperationException("FakeItineraryDraftPort 는 하루 재계산을 흉내내지 않는다");
	}

	@Override
	public ItineraryHandle publish(ItineraryRevisionDraft draft) {
		throw new UnsupportedOperationException("FakeItineraryDraftPort 는 하루 재계산을 흉내내지 않는다");
	}
}
