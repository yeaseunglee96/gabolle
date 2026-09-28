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
 * 이 여행의 일정에 실제로 들어 있는 장소 이름을 모은다. 이름 짓기에서 모델에게 주는 낱말은
 * 이것이 전부고, 받은 이름이 이 목록 밖으로 나가는지는 {@link PlaceWordGuard} 가 본다 —
 * 주는 것과 검사하는 것을 둘 다 한다.
 *
 * <p>일정이 없는 여행은 빈 목록이다. 그때 부르는 쪽은 모델을 부르지 않고 템플릿으로 물러선다.
 */
@Component
@Profile({ "db", "dev" })
@ConditionalOnBean(ItineraryRepository.class)
public class TripPlaceVocabulary {

	/** 모델에게 주는 장소 이름의 상한. 일정이 길면 그대로 보내는 것이 곧 토큰 비용이다. */
	private static final int MAX_PLACES = 20;

	private final ItineraryRepository itineraryRepository;

	private final PlaceRepository placeRepository;

	public TripPlaceVocabulary(ItineraryRepository itineraryRepository, PlaceRepository placeRepository) {
		this.itineraryRepository = itineraryRepository;
		this.placeRepository = placeRepository;
	}

	/**
	 * @return 방문 순서대로, 중복 없이. 일정이 없으면 빈 목록
	 */
	public List<String> placeNamesOf(String tripId) {
		return placeNamesOf(tripId, false);
	}

	/**
	 * @param english 영어 화면이 부르는가. 그러면 장소마다 영어 이름({@code name_en})을 쓰고, 없으면 한국어 이름 그대로 —
	 *     영어 이름을 지어내지 않는다(S15P21E201-1780)
	 * @return 방문 순서대로, 중복 없이. 일정이 없으면 빈 목록
	 */
	public List<String> placeNamesOf(String tripId, boolean english) {
		List<Itinerary> itineraries = this.itineraryRepository.findByTripId(tripId);
		if (itineraries.isEmpty()) {
			return List.of();
		}
		// 지금은 여행마다 일정이 하나다 — SharedItineraryAssembler 가 같은 전제를 쓴다.
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
				.collect(Collectors.toMap(Place::getPlaceId, (place) -> displayName(place, english), (a, b) -> a));

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

	private static String displayName(Place place, boolean english) {
		String en = place.getNameEn();
		return (english && en != null && !en.isBlank()) ? en : place.getNameKo();
	}
}
