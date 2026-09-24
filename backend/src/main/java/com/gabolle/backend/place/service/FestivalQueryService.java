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
 * 여행 기간과 겹치는 축제 조회.
 *
 * <p>겹침 판정은 {@link PlaceEventPeriodRepository#findOverlapping} 이 SQL 에서 끝내고 여기서
 * 다시 하지 않는다. 자바에서 또 거르면 같은 규칙이 두 곳에 생겨 한쪽만 고쳐지는 날이 온다.
 *
 * <p>장소와 입장료는 placeId 를 모아 한 번씩만 읽는다. 회차마다 따로 조회하면 질의 개수가
 * 회차 수에 비례한다.
 */
@Service
@Profile({ "db", "dev" })
public class FestivalQueryService {

	/** 입장료는 태그형이 아니라 값형이라 {@code feature_key} 가 없다. */
	private static final String PRICE_LEVEL_FEATURE_TYPE = "PRICE_LEVEL";

	/**
	 * 조회 기간 상한(포함 일수). 없으면 넓은 범위 하나로 {@code place_event_period} 표 전체를
	 * 훑게 된다.
	 */
	private static final long MAX_RANGE_DAYS = 366;

	/** 한 여행 기간에 겹치는 축제가 이보다 많은 일은 드물다. */
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

	public FestivalResponse findOverlapping(LocalDate startDate, LocalDate endDate) {
		return findOverlapping(startDate, endDate, null, null);
	}

	/**
	 * 기간 상한({@link #MAX_RANGE_DAYS})은 얼마나 넓게 찾을지만 막고 한 기간 안의 회차 수는 못
	 * 막으므로 쪽을 나눈다. 잘렸다는 사실은 {@code hasMore} 로 반드시 함께 알린다.
	 *
	 * <p>쪽 크기 기본값은 여기서만 정한다. 컨트롤러도 기본값을 알고 있으면 둘이 어긋나는 날
	 * 안 주고 부른 첫 쪽과 {@code page=0} 의 크기가 달라져 행이 겹치거나 건너뛰어진다.
	 *
	 * @param page 0부터. {@code null} 이거나 음수면 0
	 * @param size {@code null} 이면 {@link #DEFAULT_PAGE_SIZE}, {@link #MAX_PAGE_SIZE} 로
	 *     자르고, 1보다 작으면 1
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
				// ON DELETE CASCADE 라 정상 운영에서는 짝 없는 회차가 없다. 그래도 응답 전체가
				// 죽는 것보다 그 회차만 건너뛰는 편이 낫다.
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
					place.getPhotoSource(),
					place.getPhotoSubject(),
					place.getPhotoLicense(),
					period.getStartDate(),
					period.getEndDate(),
					toPriceLevel(priceLevelByPlace.get(place.getPlaceId())),
					overlapDates(period, startDate, endDate)));
		}

		return new FestivalResponse(items, items.size(), hasMore);
	}

	/**
	 * {@code place_feature} 는 (place_id, feature_type) 이 UNIQUE 라 장소 하나에 입장료 행은
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

	/** 파싱이 깨지면 조회 전체를 실패시키지 않고 {@code null} 로 둔다. */
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

	/** 두 날짜는 컨트롤러가 필수로 받으므로 여기서는 순서와 폭만 본다. */
	private void validate(LocalDate startDate, LocalDate endDate) {
		if (endDate.isBefore(startDate)) {
			throw new PlaceRequestException("INVALID_REQUEST", "종료일은 시작일보다 빠를 수 없습니다.",
					List.of("endDate"));
		}

		// 포함 일수 = 두 날짜 사이 일수 + 1 (시작일도 포함).
		long inclusiveDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
		if (inclusiveDays > MAX_RANGE_DAYS) {
			throw new PlaceRequestException("INVALID_REQUEST",
					"조회 기간은 최대 " + MAX_RANGE_DAYS + "일까지 가능합니다.", List.of("startDate", "endDate"));
		}
	}
}
