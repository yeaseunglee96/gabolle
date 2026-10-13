package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.api.RestaurantAreaResponse;
import com.gabolle.backend.place.api.RestaurantAreaResponse.DongItem;
import com.gabolle.backend.place.api.RestaurantAreaResponse.GuItem;
import com.gabolle.backend.place.api.RestaurantPageResponse;
import com.gabolle.backend.place.api.RestaurantPageResponse.RestaurantItem;
import com.gabolle.backend.place.domain.CertifiedRestaurant;
import com.gabolle.backend.place.domain.CertifiedRestaurant.Area;
import com.gabolle.backend.place.domain.CertifiedRestaurant.Source;
import com.gabolle.backend.place.domain.InterestTagCode;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.CertifiedRestaurantRepository;
import com.gabolle.backend.place.repository.CertifiedRestaurantRepository.AreaPlace;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;

/**
 * 로컬 탐색 「맛집」 갈래 — 구·동 목록과 구·동으로 거른 맛집 목록 (S15P21E201-2051).
 *
 * <p>무엇이 맛집이고 어느 구·동인지는 전부 {@code place_feature} 표식이 정한다({@link CertifiedRestaurant}). 이름이나 갈래 문자열로
 * 맛집을 가리지 않는다 — 이름에 「맛집」이 없는 집은 영원히 안 나온다. 질의는 둘이다: 구·동 목록은 쌍 하나, 맛집 목록은 장소 하나에
 * 표식 하나를 읽는다. 장소 수만큼 늘지 않는다.
 *
 * <p>목록의 순서는 <b>인증한 목록이 많은 집이 먼저</b>, 같으면 이름 순이다. 이 순서는 DB 가 못 정한다(표식 수로 정렬하려면 JPQL 이
 * 표식을 세어야 한다) — 구·동 하나의 맛집은 많아야 수십 곳이라 {@link #MAX_SCAN} 곳까지 받아 와 자바에서 정렬한 뒤 자른다.
 */
@Service
@Profile({ "db", "dev" })
public class CertifiedRestaurantQueryService {

	/** 한 번에 줄 수 있는 곳의 기본·최대. 한 동의 맛집을 다 담는 크기다. */
	private static final int DEFAULT_LIMIT = 100;

	private static final int MAX_LIMIT = 200;

	/** 정렬하려고 DB 에서 받아 오는 최대 행. 구 전체(동 없이)도 지금은 백 곳 안쪽이다. */
	private static final int MAX_SCAN = 500;

	/** 구·동 이름이 이보다 길면 잘못된 요청이다. 가장 긴 부산 구 이름이 5자다. */
	private static final int NAME_MAX = 30;

	private final CertifiedRestaurantRepository repository;

	private final PlaceFeatureRepository placeFeatureRepository;

	public CertifiedRestaurantQueryService(CertifiedRestaurantRepository repository,
			PlaceFeatureRepository placeFeatureRepository) {
		this.repository = repository;
		this.placeFeatureRepository = placeFeatureRepository;
	}

	/** 구 → 동 목록과 맛집 수. 식당이 많은 곳이 먼저다. 맛집 자료가 아직 없으면 빈 목록이다. */
	public RestaurantAreaResponse areas() {
		List<AreaPlace> pairs = this.repository.findAreaPlaces(InterestTagCode.FEATURE_TYPE,
				CertifiedRestaurant.EXPLORE_KEY, CertifiedRestaurant.AREA_FEATURE_TYPE);

		Map<String, Set<UUID>> placesByGu = new HashMap<>();
		Map<String, Map<String, Set<UUID>>> placesByGuDong = new HashMap<>();
		for (AreaPlace pair : pairs) {
			// 모양이 틀린 열쇠는 건너뛴다 — 남이 다른 꼴로 넣은 표식이 응답을 깨지 못하게.
			Area.fromKey(pair.getAreaKey()).ifPresent(area -> {
				placesByGu.computeIfAbsent(area.gu(), key -> new HashSet<>()).add(pair.getPlaceId());
				placesByGuDong.computeIfAbsent(area.gu(), key -> new HashMap<>())
						.computeIfAbsent(area.dong(), key -> new HashSet<>()).add(pair.getPlaceId());
			});
		}

		List<GuItem> areas = new ArrayList<>();
		placesByGu.forEach((gu, places) -> {
			List<DongItem> dongs = new ArrayList<>();
			placesByGuDong.get(gu).forEach((dong, dongPlaces) -> dongs.add(new DongItem(dong, dongPlaces.size())));
			dongs.sort(Comparator.comparingLong(DongItem::placeCount).reversed().thenComparing(DongItem::dong));
			areas.add(new GuItem(gu, places.size(), List.copyOf(dongs)));
		});
		areas.sort(Comparator.comparingLong(GuItem::placeCount).reversed().thenComparing(GuItem::gu));
		return new RestaurantAreaResponse(List.copyOf(areas), OffsetDateTime.now(ZoneOffset.UTC));
	}

	/**
	 * 구(와 동)로 거른 맛집. {@code dong} 이 비면 구 전체다.
	 *
	 * @throws PlaceRequestException 구가 비었거나, 이름이 너무 길거나, {@code limit} 이 범위 밖이다
	 */
	public RestaurantPageResponse restaurants(String gu, String dong, Integer limit) {
		String guName = requireName(gu, "gu", "구를 지정해 주세요.");
		String dongName = (dong == null || dong.isBlank()) ? null : requireName(dong, "dong", "동 이름이 올바르지 않습니다.");
		int effectiveLimit = requireLimit(limit);

		Limit scan = Limit.of(MAX_SCAN + 1);
		List<Place> found = (dongName == null)
				? this.repository.findInGu(InterestTagCode.FEATURE_TYPE, CertifiedRestaurant.EXPLORE_KEY,
						CertifiedRestaurant.AREA_FEATURE_TYPE, likePrefix(guName), scan)
				: this.repository.findInArea(InterestTagCode.FEATURE_TYPE, CertifiedRestaurant.EXPLORE_KEY,
						CertifiedRestaurant.AREA_FEATURE_TYPE, CertifiedRestaurant.areaKey(guName, dongName), scan);
		List<Place> scanned = found.size() > MAX_SCAN ? found.subList(0, MAX_SCAN) : found;

		Map<UUID, List<PlaceFeature>> featuresByPlace = new HashMap<>();
		if (!scanned.isEmpty()) {
			List<UUID> ids = scanned.stream().map(Place::getPlaceId).toList();
			for (PlaceFeature feature : this.placeFeatureRepository.findByPlaceIdIn(ids)) {
				featuresByPlace.computeIfAbsent(feature.getPlaceId(), key -> new ArrayList<>()).add(feature);
			}
		}

		List<RestaurantItem> items = new ArrayList<>(scanned.size());
		for (Place place : scanned) {
			items.add(toItem(place, featuresByPlace.getOrDefault(place.getPlaceId(), List.of()), guName, dongName));
		}
		items.sort(Comparator.comparingInt((RestaurantItem item) -> item.certifiedSources().size()).reversed()
				.thenComparing(item -> item.place().nameKo(), Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(item -> item.place().placeId()));

		boolean hasNext = items.size() > effectiveLimit || found.size() > MAX_SCAN;
		List<RestaurantItem> page = items.size() > effectiveLimit ? items.subList(0, effectiveLimit) : items;
		return new RestaurantPageResponse(List.copyOf(page), effectiveLimit, hasNext);
	}

	/**
	 * 장소 하나를 응답 한 줄로. 구·동은 요청한 곳과 맞는 표식을 먼저 고른다 — 한 장소가 구·동 표식을 둘 가졌을 때 요청한 곳으로
	 * 보여야 목록이 앞뒤가 맞는다. 표식이 {@link PlaceFeature#indicatesPresence()} 가 아니면(확인된 부재) 없는 것으로 본다.
	 */
	private static RestaurantItem toItem(Place place, List<PlaceFeature> features, String guName, String dongName) {
		Area area = null;
		Set<Source> sources = new TreeSet<>(Comparator.comparingInt(Source::ordinal));
		for (PlaceFeature feature : features) {
			if (!feature.indicatesPresence()) {
				continue;
			}
			if (CertifiedRestaurant.AREA_FEATURE_TYPE.equals(feature.getFeatureType())) {
				Area candidate = Area.fromKey(feature.getFeatureKey()).orElse(null);
				if (candidate != null && (area == null || matches(candidate, guName, dongName))) {
					area = candidate;
				}
			}
			else if (CertifiedRestaurant.SOURCE_FEATURE_TYPE.equals(feature.getFeatureType())) {
				Source.fromCode(feature.getFeatureKey()).ifPresent(sources::add);
			}
		}
		return new RestaurantItem(PlaceSummaryResponse.ofFacetMatch(place), area == null ? null : area.gu(),
				area == null ? null : area.dong(), sources.stream().map(Source::name).toList());
	}

	private static boolean matches(Area area, String guName, String dongName) {
		return area.gu().equals(guName) && (dongName == null || area.dong().equals(dongName));
	}

	/** {@code 해운대구/%} — 구 이름의 {@code % _ \} 는 이스케이프한다(저장소 쪽 질의가 {@code ESCAPE '\'} 로 고정). */
	private static String likePrefix(String gu) {
		String escaped = CertifiedRestaurant.guKeyPrefix(gu).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return escaped + "%";
	}

	private static String requireName(String value, String field, String message) {
		if (value == null || value.isBlank()) {
			throw new PlaceRequestException("INVALID_REQUEST", message, List.of(field));
		}
		String trimmed = value.strip();
		if (trimmed.length() > NAME_MAX) {
			throw new PlaceRequestException("INVALID_REQUEST", "이름이 너무 깁니다.", List.of(field));
		}
		return trimmed;
	}

	private static int requireLimit(Integer limit) {
		int effective = (limit == null) ? DEFAULT_LIMIT : limit;
		if (effective < 1 || effective > MAX_LIMIT) {
			throw new PlaceRequestException("INVALID_REQUEST", "limit 은 1에서 " + MAX_LIMIT + " 사이여야 합니다.",
					List.of("limit"));
		}
		return effective;
	}
}
