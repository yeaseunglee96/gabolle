package com.gabolle.backend.place.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.gabolle.backend.place.api.FestivalResponse;
import com.gabolle.backend.place.api.FestivalResponse.FestivalItem;
import com.gabolle.backend.place.api.FestivalResponse.PriceLevel;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEventPeriod;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceEventPeriodRepository;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 여행 기간과 겹치는 축제 조회 (S15P21E201-465).
 *
 * <h2>🔴 겹침 판정을 여기서 다시 하지 않는 이유</h2>
 *
 * 완료 기준이 "화면이 거르면 안 된다" 다. {@link PlaceEventPeriodRepository#findOverlapping} 이
 * 이미 SQL 에서 걸러 온다 — 전체를 읽어 자바에서 다시 거르면 축제가 늘 때 이 조회만 느려지는 것이
 * 아니라, 걸러 오는 규칙이 두 곳(SQL 과 자바)에 생겨 한쪽만 고쳐지는 날이 온다.
 *
 * <h2>🔴 장소·입장료를 한 번에 읽는 이유</h2>
 *
 * 회차마다 장소나 피처를 따로 조회하면 질의 개수가 축제 회차 수에 비례한다({@code
 * itinerary.ActorNames} 가 같은 이유로 사용자 이름을 한 번에 읽는 것과 같은 구조). 여기서는 회차
 * 목록에서 placeId 를 모아 {@link PlaceRepository#findAllById} 와
 * {@link PlaceFeatureRepository#findByPlaceIdIn} 을 각각 한 번씩만 부른다. 후자는 그 장소의
 * 피처를 전부(입장료 말고 다른 종류도) 받아 오지만, 그래도 회차 수와 무관하게 한 번이다.
 */
@Service
@Profile({ "db", "dev" })
public class FestivalQueryService {

	/** {@code place_feature.feature_type} 값. 입장료는 태그형이 아니라 값형이라 feature_key 가 없다. */
	private static final String PRICE_LEVEL_FEATURE_TYPE = "PRICE_LEVEL";

	/**
	 * 조회 기간 상한(포함 일수). 🔴 상한이 없으면 누군가 100년 범위를 보내 place_event_period 표
	 * 전체를 훑어가게 된다 — 겹침 질의가 (start_date, end_date) 인덱스를 쓰긴 하지만, 조건 자체가
	 * 넓으면 인덱스가 걸러 주는 행 수도 그만큼 늘어난다.
	 */
	private static final long MAX_RANGE_DAYS = 366;

	/**
	 * 쪽 크기를 안 주면 이만큼 (S15P21E201-1011). 한 여행 기간에 겹치는 축제가 이보다 많은
	 * 일은 드물어서, 지금까지처럼 한 번만 부르는 화면은 <b>보이는 동작이 달라지지 않는다.</b>
	 */
	private static final int DEFAULT_PAGE_SIZE = 100;

	/** 부르는 쪽이 아무리 크게 달라고 해도 여기까지. 상한이 없으면 파라미터 하나로 상한이 풀린다. */
	private static final int MAX_PAGE_SIZE = 200;

	private final PlaceEventPeriodRepository eventPeriodRepository;

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	private final ObjectMapper objectMapper;

	public FestivalQueryService(PlaceEventPeriodRepository eventPeriodRepository, PlaceRepository placeRepository,
			PlaceFeatureRepository placeFeatureRepository, ObjectMapper objectMapper) {
		this.eventPeriodRepository = eventPeriodRepository;
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
		this.objectMapper = objectMapper;
	}

	/** 쪽을 지정하지 않으면 첫 쪽을 기본 크기로 준다 — 지금까지 이 서비스를 부르던 코드가 그대로 온다. */
	public FestivalResponse findOverlapping(LocalDate startDate, LocalDate endDate) {
		return findOverlapping(startDate, endDate, null, null);
	}

	/**
	 * 🔴 S15P21E201-1011 — 회차 수에 상한을 두고 쪽을 나눈다.
	 *
	 * <p>기간 상한({@link #MAX_RANGE_DAYS})은 <b>얼마나 넓게 찾을까</b>만 막는다. 한 기간
	 * <i>안</i>에 회차가 몇 개인지는 못 막으므로, 축제가 쌓이면 이 응답만 계속 커졌다.
	 *
	 * <p>🔴 상한을 두면 <b>알리는 칸을 함께</b> 둬야 한다({@code hasMore}). 상한만 두고 안
	 * 알리면 목록이 조용히 잘리고, 사용자에게는 "있던 축제가 사라졌다" 로 보인다 — 이 티켓이
	 * 다른 목록들에 대해 지적하는 것이 정확히 그 상태다.
	 *
	 * <p>🔴 기본값을 <b>여기서만</b> 정한다. 부르는 쪽은 안 준 값을 {@code null} 로 그대로
	 * 넘긴다 — 컨트롤러도 기본값을 알고 있으면 둘이 어긋나는 날이 오고, 그러면 안 주고 부른
	 * 첫 쪽과 {@code page=0} 으로 부른 쪽의 크기가 달라져 행이 겹치거나 건너뛰어진다.
	 *
	 * @param page 0부터. {@code null} 이거나 음수면 0
	 * @param size 한 쪽에 실을 최대 회차 수. {@code null} 이면 {@link #DEFAULT_PAGE_SIZE},
	 *     {@link #MAX_PAGE_SIZE} 로 자르고, 1보다 작으면 1
	 */
	public FestivalResponse findOverlapping(LocalDate startDate, LocalDate endDate, Integer page, Integer size) {
		validate(startDate, endDate);

		int pageNumber = (page == null) ? 0 : Math.max(page, 0);
		int pageSize = (size == null) ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

		Page<PlaceEventPeriod> found = this.eventPeriodRepository.findOverlapping(startDate, endDate,
				PageRequest.of(pageNumber, pageSize));
		List<PlaceEventPeriod> periods = found.getContent();
		boolean hasMore = found.hasNext();

		List<UUID> placeIds = periods.stream().map(PlaceEventPeriod::getPlaceId).distinct().toList();
		Map<UUID, Place> placesById = new HashMap<>();
		for (Place place : this.placeRepository.findAllById(placeIds)) {
			placesById.put(place.getPlaceId(), place);
		}
		Map<UUID, PlaceFeature> priceLevelByPlace = findPriceLevels(placeIds);

		List<FestivalItem> items = new ArrayList<>();
		for (PlaceEventPeriod period : periods) {
			Place place = placesById.get(period.getPlaceId());
			if (place == null) {
				// place_event_period 는 place 를 ON DELETE CASCADE 로 참조하므로 정상 운영에서는
				// 짝 없는 회차가 생기지 않는다. 그래도 응답이 죽는 것보다는 그 회차만 건너뛰는 편이
				// 안전하다 — 화면의 다른 축제 카드까지 함께 사라질 이유가 없다.
				continue;
			}
			items.add(new FestivalItem(
					place.getPlaceId(),
					place.getNameKo(),
					place.getNameEn(),
					period.getTitle(),
					place.getAddress(),
					place.getLat(),
					place.getLng(),
					place.getPhotoUrl(),
					period.getStartDate(),
					period.getEndDate(),
					toPriceLevel(priceLevelByPlace.get(place.getPlaceId())),
					overlapDates(period, startDate, endDate)));
		}

		return new FestivalResponse(items, items.size(), hasMore);
	}

	/**
	 * 장소별 입장료 피처를 한 번에 읽는다. {@code place_feature} 는 (place_id, feature_type) 이
	 * UNIQUE 라(값형·점수형은 feature_key 가 없어 이 조합이 곧 유일 키다) 장소 하나에 입장료 행은
	 * 최대 하나다.
	 */
	private Map<UUID, PlaceFeature> findPriceLevels(List<UUID> placeIds) {
		Map<UUID, PlaceFeature> byPlace = new HashMap<>();
		for (PlaceFeature feature : this.placeFeatureRepository.findByPlaceIdIn(placeIds)) {
			if (PRICE_LEVEL_FEATURE_TYPE.equals(feature.getFeatureType())) {
				byPlace.put(feature.getPlaceId(), feature);
			}
		}
		return byPlace;
	}

	/** 입장료 행이 아예 없으면 {@code null} — 호출부가 그대로 응답에 실으면 칸이 통째로 빠진다. */
	private PriceLevel toPriceLevel(PlaceFeature feature) {
		if (feature == null) {
			return null;
		}
		return new PriceLevel(readValue(feature.getValue()), feature.getEvidenceStatus().name());
	}

	/**
	 * JSONB 문자열을 응답에 실을 수 있는 모양으로 바꾼다. {@code PlaceDetailService.readValue} 와
	 * 같은 이유로 파싱이 깨지면 조회 전체를 실패시키지 않고 {@code null} 로 둔다.
	 */
	private JsonNode readValue(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return this.objectMapper.readTree(raw);
		}
		catch (JacksonException exception) {
			return null;
		}
	}

	/** 축제 기간과 여행 기간의 교집합 날짜를 시작일부터 하나씩 나열한다. */
	private List<LocalDate> overlapDates(PlaceEventPeriod period, LocalDate tripFrom, LocalDate tripTo) {
		LocalDate from = period.getStartDate().isAfter(tripFrom) ? period.getStartDate() : tripFrom;
		LocalDate to = period.getEndDate().isBefore(tripTo) ? period.getEndDate() : tripTo;

		List<LocalDate> dates = new ArrayList<>();
		for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
			dates.add(date);
		}
		return dates;
	}

	/**
	 * {@code startDate}·{@code endDate} 는 컨트롤러가 {@code @RequestParam} 필수로 받으므로 여기까지
	 * 오면 둘 다 {@code null} 이 아니다. 남는 것은 순서와 폭이다.
	 */
	private void validate(LocalDate startDate, LocalDate endDate) {
		if (endDate.isBefore(startDate)) {
			throw new PlaceRequestException("INVALID_REQUEST", "종료일은 시작일보다 빠를 수 없습니다.",
					List.of("endDate"));
		}

		// 포함 일수 = 두 날짜 사이 일수 + 1 (시작일도 포함해야 하므로). 366 을 초과하면(367일 범위부터) 거절한다.
		long inclusiveDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
		if (inclusiveDays > MAX_RANGE_DAYS) {
			throw new PlaceRequestException("INVALID_REQUEST",
					"조회 기간은 최대 " + MAX_RANGE_DAYS + "일까지 가능합니다.", List.of("startDate", "endDate"));
		}
	}
}
