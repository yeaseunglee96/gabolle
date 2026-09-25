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
 * 일정 포트의 테스트용 대역. {@code RecommendationSliceApplication} 이 {@code itinerary}
 * 패키지를 스캔하지 않아 진짜 구현이 없고, 대역이 없으면 {@code ITINERARY_GENERATION} 성공
 * 경로가 {@code ERROR_ITINERARY_PORT_NOT_CONFIGURED} 로 실패한다.
 *
 * {@link #persist} 는 원시 SQL 로 최소한의 실제 행을 넣는다 —
 * {@code recommendation_job.itinerary_id} 에 FK 가 걸려 있어 가짜 UUID 를 붙이면 저장 자체가
 * 실패한다.
 */
public class FakeItineraryDraftPort implements ItineraryDraftPort {

	private final JdbcTemplate jdbcTemplate;

	/** 조립이 이만큼 걸린 것처럼 기다린다(밀리초). 조립 시간이 작업 기록에 적히는지 볼 때만 쓴다. */
	private volatile long assembleDelayMs;

	public FakeItineraryDraftPort(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * 필요한 자리 수. 진짜 구현은 여행의 날 수 × 기분을 보지만 이 대역은 여행을 안 읽는다 —
	 * 추천 쪽 테스트가 보는 것은 「설정 기본값보다 크면 그쪽을 쓴다」 하나뿐이고, 날 수 계산
	 * 자체는 {@code ItineraryDraftServiceTest}(일정 슬라이스)가 본다.
	 *
	 * <p>1 을 준다 — 설정 기본값보다 작아서 지금까지의 동작을 안 바꾼다. 이 대역이 큰 값을
	 * 주면 추천 로깅 테스트들이 갑자기 후보를 더 많이 요구하게 되고, 그건 이 대역이 재려던
	 * 것과 무관한 변화다.
	 */
	@Override
	public int placesNeeded(String tripId) {
		return 1;
	}

	/** 다음 조립부터 이만큼 기다린다. 0 이면 안 기다린다 — 쓴 시험이 끝나면 0 으로 되돌린다. */
	public void delayAssembleBy(long millis) {
		this.assembleDelayMs = millis;
	}

	@Override
	public ItineraryDraft assemble(ItineraryDraftCommand command) {
		if (this.assembleDelayMs > 0) {
			try {
				Thread.sleep(this.assembleDelayMs);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
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
	 * 하루 재계산의 진짜 구현은 일정 슬라이스에서 검증한다. 여기서 조용히 빈 초안을 돌려주면
	 * 성공한 것처럼 보이는 거짓 초록이 생기므로 시끄럽게 실패한다.
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
