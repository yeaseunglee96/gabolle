package com.gabolle.backend.share.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.share.domain.TripShareLink;
import com.gabolle.backend.share.presentation.dto.SharedItineraryResponse;
import com.gabolle.backend.trip.domain.Trip;

/**
 * {@link SharedItineraryResponse} 를 만든다.
 *
 * <p>이 응답은 상세 응답에서 칸을 비운 것이 아니라 보낼 것만 담은 별도 모양이다. 출발지
 * 좌표·예산·인원·연락처는 여기서 읽지 않는다 — record 자체에 그 칸이 없다.
 *
 * <p>날짜별 묶기·정렬·시각 조합 규칙은 {@code ItineraryQueryService} 와 같아야 한다.
 */
@Component
@Profile({ "db", "dev" })
public class SharedItineraryAssembler {

	private final ItineraryRepository itineraryRepository;

	private final PlaceRepository placeRepository;

	public SharedItineraryAssembler(ItineraryRepository itineraryRepository, PlaceRepository placeRepository) {
		this.itineraryRepository = itineraryRepository;
		this.placeRepository = placeRepository;
	}

	public SharedItineraryResponse assemble(Trip trip, TripShareLink link) {
		List<Itinerary> itineraries = this.itineraryRepository.findByTripId(trip.tripId());
		// 지금은 여행마다 일정이 하나다(ItineraryRepository.findByTripId 의 javadoc). 첫 일정을 쓴다.
		Itinerary itinerary = itineraries.isEmpty() ? null : itineraries.get(0);

		Integer version = null;
		Map<Integer, List<ItineraryItem>> itemsByDay = Map.of();
		Map<UUID, Place> placesByPlaceId = Map.of();

		if (itinerary != null) {
			int latestVersion = itinerary.latestVersion();
			version = latestVersion;
			ItineraryContent content = this.itineraryRepository.findContent(itinerary.itineraryId(), latestVersion)
					.orElseThrow(() -> new IllegalStateException(
							"일정의 최신 판(version=" + latestVersion + ") 내용이 없다: itineraryId=" + itinerary.itineraryId()));
			itemsByDay = content.items().stream().collect(Collectors.groupingBy(ItineraryItem::dayIndex));
			placesByPlaceId = lookupPlaces(content.items());
		}

		List<SharedItineraryResponse.Day> days = buildDays(trip, itemsByDay, placesByPlaceId);

		return new SharedItineraryResponse(
				trip.displayTitle(),
				trip.startDate().toString(),
				trip.finishDate().toString(),
				version,
				days,
				link.getExpiresAt().toString(),
				SharedItineraryResponse.NOT_SHARED);
	}

	/** 여행 기간의 날짜 전부를 만든다. 항목이 0개인 날도 빈 items 로 넣는다. */
	private List<SharedItineraryResponse.Day> buildDays(Trip trip, Map<Integer, List<ItineraryItem>> itemsByDay,
			Map<UUID, Place> placesByPlaceId) {

		List<SharedItineraryResponse.Day> days = new ArrayList<>(trip.days());
		LocalDate date = trip.startDate();
		for (int dayIndex = 0; dayIndex < trip.days(); dayIndex++) {
			List<ItineraryItem> itemsOfDay = itemsByDay.getOrDefault(dayIndex, List.of()).stream()
					.sorted((a, b) -> Integer.compare(a.sequence(), b.sequence()))
					.toList();

			List<SharedItineraryResponse.Item> items = new ArrayList<>(itemsOfDay.size());
			for (ItineraryItem item : itemsOfDay) {
				items.add(toItemDto(item, placesByPlaceId));
			}

			days.add(new SharedItineraryResponse.Day(date.toString(), items));
			date = date.plusDays(1);
		}
		return days;
	}

	private SharedItineraryResponse.Item toItemDto(ItineraryItem item, Map<UUID, Place> placesByPlaceId) {
		Place place = placesByPlaceId.get(UUID.fromString(item.placeId()));
		if (place == null) {
			// FK 가 있는 한 있을 수 없는 상태다 — 조용히 넘기면 화면에 빈 이름이 뜬다.
			throw new IllegalStateException("일정 항목이 가리키는 place 를 찾을 수 없다: placeId=" + item.placeId());
		}
		return new SharedItineraryResponse.Item(
				item.sequence(),
				place.getNameKo(),
				place.getCategory(),
				zonedIso(item, item.startTime()),
				zonedIso(item, item.endTime()),
				item.stayMinutes());
	}

	/** {@code visit_date} + 시각을 ISO-8601 로 합친다. 시간대는 항상 Asia/Seoul(API-03). 시각이 없으면 null. */
	private String zonedIso(ItineraryItem item, LocalTime time) {
		if (time == null) {
			return null;
		}
		ZonedDateTime zoned = ZonedDateTime.of(item.visitDate(), time, ZoneId.of("Asia/Seoul"));
		return zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	private Map<UUID, Place> lookupPlaces(List<ItineraryItem> items) {
		if (items.isEmpty()) {
			return Map.of();
		}
		List<UUID> placeIds = items.stream()
				.map(item -> UUID.fromString(item.placeId()))
				.distinct()
				.toList();
		Map<UUID, Place> result = new HashMap<>();
		for (Place place : this.placeRepository.findByPlaceIdIn(placeIds)) {
			result.put(place.getPlaceId(), place);
		}
		return result;
	}
}
