package com.gabolle.backend.itinerary.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 직접 짜기 — AI 추천 없이 <b>곳이 하나도 없는</b> 일정을 만든다 (S15P21E201-2018).
 *
 * <p>여행(날짜·인원·출발지·숙소)은 추천 때와 같은 {@code POST /api/v1/trips} 로 만들고, 여기서는 그 여행에 빈 1판만 붙인다.
 * 날은 여행 날짜에서 나온다({@link ItineraryQueryService} 가 여행 일수만큼 날을 그린다). 곳은 그 뒤에 「장소 더하기」
 * ({@code POST /api/v1/itineraries/{id}/items}, 자리 지정)로 넣고, 구간·시각은 그 경로가 매긴다 — 빈 날의 첫 곳은
 * 여행의 활동 시작 시각에서 시작한다.
 *
 * <p>이미 일정이 있으면 새로 만들지 않고 그것을 준다 — 단추를 두 번 눌러도, 네트워크가 끊겨 다시 보내도 일정이 둘 생기지 않는다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class SelfPlanItineraryService {

	/** 판의 {@code request_id}. 어디서 왔는지 남긴다 — 추천 요청 번호가 아니다. */
	static final String REQUEST_ID = "self_plan";

	private final TripQueryService tripQueryService;
	private final ItineraryRepository itineraryRepository;
	private final TripRepository tripRepository;
	private final Clock clock;

	public SelfPlanItineraryService(TripQueryService tripQueryService, ItineraryRepository itineraryRepository,
			TripRepository tripRepository, Clock clock) {
		this.tripQueryService = tripQueryService;
		this.itineraryRepository = itineraryRepository;
		this.tripRepository = tripRepository;
		this.clock = clock;
	}

	/**
	 * @return 만든(또는 이미 있던) 일정 번호
	 * @throws TripQueryService.TripNotFoundException 여행이 없거나 요청자가 회원이 아니다
	 * @throws NotEditableException 보기만 하는 회원이다
	 */
	@Transactional
	public String createBlank(String tripId, String requesterUserId) {
		TripQueryService.View view = this.tripQueryService.get(tripId, requesterUserId);
		if (!view.role().canEdit()) {
			throw new NotEditableException(tripId);
		}
		List<Itinerary> existing = this.itineraryRepository.findByTripId(tripId);
		if (!existing.isEmpty()) {
			return existing.get(0).itineraryId();
		}

		Instant now = this.clock.instant();
		String itineraryId = UUID.randomUUID().toString();
		Itinerary itinerary = new Itinerary(itineraryId, tripId, 1);
		ItineraryVersion first = new ItineraryVersion(UUID.randomUUID().toString(), itineraryId, 1, null,
				ItineraryVersion.Operation.CREATE, requesterUserId, REQUEST_ID,
				new ItineraryVersion.Versions(null, null, null, null, null), now);
		this.itineraryRepository.create(itinerary, first, List.of(), List.of());

		// 추천이 일정을 만들 때와 같이 여행을 「준비됨」으로 — 안 바꾸면 목록이 계속 「만드는 중」으로 그린다.
		this.tripRepository.findById(tripId)
				.filter((trip) -> !trip.isDeleted() && trip.status() == Trip.Status.PLANNING)
				.ifPresent((trip) -> {
					trip.markReady(now);
					this.tripRepository.updateStatus(trip);
				});
		return itineraryId;
	}

	/** 보기만 하는 회원이 만들려 했다 — 403. */
	public static class NotEditableException extends RuntimeException {
		public NotEditableException(String tripId) {
			super("이 여행의 일정을 만들 권한이 없습니다: " + tripId);
		}
	}
}
