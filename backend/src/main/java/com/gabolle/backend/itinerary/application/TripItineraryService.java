package com.gabolle.backend.itinerary.application;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.presentation.dto.TripItineraryResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 한 여행의 일정 목록.
 * {@code GET /api/v1/trips} 가 내 여행 목록을 주지만 일정 식별자는 담지 않는다 — 담으면 여행
 * 모듈이 일정 표를 알게 된다. 그래서 사용자가 여행 하나를 눌렀을 때 이 자리에 묻는다. 목록을
 * 그릴 때가 아니라 열 때 한 번이라 요청 수가 여행 수만큼 늘지 않는다.
 * 권한은 {@link TripActivityService} 와 같다 — 회원이면(VIEWER 도) 볼 수 있고, 아니면 여행이
 * 없는 것처럼 404 다. 판정을 여기서 새로 쓰지 않고 여행 조회를 그대로 부르는 이유는 두 곳이
 * 같은 규칙을 각자 들고 있으면 나중에 한쪽만 바뀌기 때문이다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripItineraryService {

	private final TripQueryService tripQueryService;

	private final ItineraryRepository itineraryRepository;

	public TripItineraryService(TripQueryService tripQueryService, ItineraryRepository itineraryRepository) {
		this.tripQueryService = tripQueryService;
		this.itineraryRepository = itineraryRepository;
	}

	/**
	 * @throws TripQueryService.TripNotFoundException 여행이 없거나 요청자가 그 여행의 회원이
	 *     아니다. 둘을 구분해 답하지 않는다 — 구분하면 응답 코드 자체가 남의 여행이 있다는
	 *     신호가 된다
	 */
	@Transactional(readOnly = true)
	public TripItineraryResponse list(String tripId, String requesterUserId) {
		TripQueryService.View view = this.tripQueryService.get(tripId, requesterUserId);

		List<TripItineraryResponse.Entry> entries = this.itineraryRepository.findByTripId(tripId).stream()
				.map(TripItineraryService::toEntry)
				.toList();

		return new TripItineraryResponse(tripId, view.role().name(), entries);
	}

	private static TripItineraryResponse.Entry toEntry(Itinerary itinerary) {
		return new TripItineraryResponse.Entry(itinerary.itineraryId(), itinerary.latestVersion());
	}
}
