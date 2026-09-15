package com.gabolle.backend.tripnaming.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 이 여행의 일정에 <b>실제로 들어 있는</b> 장소 이름을 모은다 — S15P21E201-1025.
 *
 * <h2>🔴 이것이 이름 짓기에서 모델에게 주는 전부다</h2>
 *
 * 모델에게 「부산 여행 이름을 지어라」라고만 하면 <b>가 보지도 않은 장소</b>를 넣는다.
 * 그래서 줄 수 있는 낱말을 여기서 못박고, 받은 이름이 이 목록 밖으로 나가는지는
 * {@link PlaceWordGuard} 가 본다 — <b>주는 것과 검사하는 것을 둘 다 한다.</b>
 * 지시만으로는 못 막는다.
 *
 * <p>일정이 없는 여행(아직 추천을 안 돌린 여행)은 <b>빈 목록</b>이다. 그때 부르는 쪽은
 * 모델을 부르지 않고 템플릿으로 물러선다 — 기댈 곳이 없는데 지어내게 두면 그게 전부
 * 거짓이 된다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnBean(ItineraryRepository.class)
public class TripPlaceVocabulary {

	/**
	 * 모델에게 주는 장소 이름의 상한.
	 *
	 * <p>🔴 일정이 길면 그대로 보내는 것이 곧 토큰 비용이다. 이름 하나를 짓는 데 스무 곳이면
	 * 충분하고, 그 이상은 이름에 안 쓰인다.
	 */
	private static final int MAX_PLACES = 20;

	private final ItineraryRepository itineraryRepository;

	private final PlaceRepository placeRepository;

	public TripPlaceVocabulary(ItineraryRepository itineraryRepository, PlaceRepository placeRepository) {
		this.itineraryRepository = itineraryRepository;
		this.placeRepository = placeRepository;
	}

	/**
	 * @return 방문 순서대로, 중복 없이. 일정이 없으면 <b>빈 목록</b>
	 */
	public List<String> placeNamesOf(String tripId) {
		List<Itinerary> itineraries = this.itineraryRepository.findByTripId(tripId);
		if (itineraries.isEmpty()) {
			return List.of();
		}
		// 🔴 지금은 여행마다 일정이 하나다 — SharedItineraryAssembler 가 같은 전제를 쓴다.
		Itinerary itinerary = itineraries.get(0);

		ItineraryContent content = this.itineraryRepository
				.findContent(itinerary.itineraryId(), itinerary.latestVersion())
				.orElse(null);
		if (content == null || content.items().isEmpty()) {
			return List.of();
		}

		List<UUID> placeIds = content.items().stream()
				.map(ItineraryItem::placeId)
				.map(UUID::fromString)
				.distinct()
				.toList();

		Map<UUID, String> nameByPlaceId = this.placeRepository.findByPlaceIdIn(placeIds).stream()
				.collect(Collectors.toMap(Place::getPlaceId, Place::getNameKo, (a, b) -> a));

		// 방문 순서를 지킨다 — 앞에 오는 곳이 그 여행의 대표일 확률이 높다.
		LinkedHashSet<String> names = new LinkedHashSet<>();
		for (UUID placeId : placeIds) {
			String name = nameByPlaceId.get(placeId);
			if (name != null && !name.isBlank()) {
				names.add(name.trim());
			}
		}
		return new ArrayList<>(names).stream().limit(MAX_PLACES).toList();
	}
}
