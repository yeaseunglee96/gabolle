package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.gabolle.backend.place.domain.CertifiedRestaurant.Kind;
import com.gabolle.backend.place.domain.CertifiedRestaurant.Source;
import com.gabolle.backend.place.domain.InterestTagCode;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.CertifiedRestaurantRepository;
import com.gabolle.backend.place.repository.CertifiedRestaurantRepository.AreaPlace;
import com.gabolle.backend.place.repository.CertifiedRestaurantRepository.AreaPoint;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 로컬 탐색 「맛집」·「카페」 갈래 — 구·동 목록과 구·동으로 거른 목록 (S15P21E201-2051, 카페는 S15P21E201-2059).
 *
 * <p>무엇이 그 갈래이고 어느 구·동인지는 전부 {@code place_feature} 표식이 정한다({@link CertifiedRestaurant}). 이름이나 갈래 문자열로
 * 가리지 않는다 — 이름에 「맛집」이 없는 집은 영원히 안 나온다. 질의는 둘이다: 구·동 목록은 쌍 하나, 목록은 장소 하나에
 * 표식 하나를 읽는다. 장소 수만큼 늘지 않는다. 맛집과 카페는 <b>같은 질의에 갈래 낱말({@link Kind#exploreKey()})만 바꿔</b> 부른다.
 *
 * <p>목록의 순서는 <b>소개·인증한 목록이 많은 집이 먼저</b>, 같으면 이름 순이다. 이 순서는 DB 가 못 정한다(표식 수로 정렬하려면 JPQL 이
 * 표식을 세어야 한다) — 구·동 하나의 곳은 많아야 수십 곳이라 {@link #MAX_SCAN} 곳까지 받아 와 자바에서 정렬한 뒤 자른다.
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

	/**
	 * 「내 위치의 구」를 정할 때 가장 가까운 곳이 이 거리 안에 있어야 한다(미터). 넘으면 부산 밖이거나 그 갈래가 드문 곳이라 구를
	 * 짐작하지 않는다 — 5km 밖의 구를 「내 구」라고 하면 사용자는 엉뚱한 목록을 받고도 이유를 모른다.
	 */
	static final double NEAREST_GU_MAX_METERS = 5_000;

	/** 가장 가까운 곳을 찾으려고 상자 안에서 받아 오는 최대 행 — 5km 상자에 한 갈래의 곳이 이만큼 있는 일은 지금 없다. */
	private static final int NEAREST_SCAN_MAX = 5_000;

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private final CertifiedRestaurantRepository repository;

	private final PlaceFeatureRepository placeFeatureRepository;

	public CertifiedRestaurantQueryService(CertifiedRestaurantRepository repository,
			PlaceFeatureRepository placeFeatureRepository) {
		this.repository = repository;
		this.placeFeatureRepository = placeFeatureRepository;
	}

	/**
	 * 요청의 {@code kind} 글자를 갈래로. 안 주면 맛집이다 — 카페가 생기기 전의 앱이 이 끝점을 {@code kind} 없이 불러도 예전과
	 * 같은 답을 받는다. 모르는 값은 400 이다(오타를 맛집으로 읽으면 엉뚱한 목록을 받고도 모른다).
	 */
	public Kind parseKind(String text) {
		if (text == null || text.isBlank()) {
			return Kind.RESTAURANT;
		}
		return Kind.fromParam(text).orElseThrow(() -> new PlaceRequestException("INVALID_REQUEST",
				"지원하지 않는 종류입니다. RESTAURANT 또는 CAFE 여야 합니다.", List.of("kind")));
	}

	/** 맛집의 구 → 동 목록. 옛 호출자를 위해 남겨 둔다 — 좌표 없이 부르면 {@code nearestGu} 는 {@code null} 이다. */
	public RestaurantAreaResponse areas() {
		return areas(Kind.RESTAURANT, null, null);
	}

	/**
	 * 구 → 동 목록과 곳 수. 곳이 많은 쪽이 먼저다. 자료가 아직 없으면 빈 목록이다.
	 *
	 * @param lat 내 위치의 위도. {@code lng} 와 함께만 온다. 주면 {@code nearestGu} 를 채운다
	 * @param lng 내 위치의 경도
	 * @throws PlaceRequestException 좌표가 하나만 왔거나 범위 밖이다
	 */
	public RestaurantAreaResponse areas(Kind kind, Double lat, Double lng) {
		boolean located = requireCoordinates(lat, lng);

		List<AreaPlace> pairs = this.repository.findAreaPlaces(InterestTagCode.FEATURE_TYPE, kind.exploreKey(),
				CertifiedRestaurant.AREA_FEATURE_TYPE);

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

		String nearestGu = located ? nearestGu(kind, lat, lng) : null;
		return new RestaurantAreaResponse(List.copyOf(areas), nearestGu, OffsetDateTime.now(ZoneOffset.UTC));
	}

	/**
	 * 내 위치에서 가장 가까운 곳의 구. 그 갈래의 장소 가운데 {@link #NEAREST_GU_MAX_METERS} 안에서 제일 가까운 곳의 구이고,
	 * 없으면 {@code null} 이다. 외부 지도 서비스를 부르지 않는다 — 이미 우리 DB 에 있는 구·동 표식과 좌표만 쓴다. 거리가 같으면
	 * 장소 번호·구/동 열쇠 순으로 골라 늘 같은 답이 나온다.
	 */
	private String nearestGu(Kind kind, double lat, double lng) {
		GeoDistance.BoundingBox box = GeoDistance.boundingBox(lat, lng, NEAREST_GU_MAX_METERS);
		List<AreaPoint> points = this.repository.findAreaPointsWithin(InterestTagCode.FEATURE_TYPE, kind.exploreKey(),
				CertifiedRestaurant.AREA_FEATURE_TYPE, box.minLat(), box.maxLat(), box.minLng(), box.maxLng(),
				Limit.of(NEAREST_SCAN_MAX));

		AreaPoint best = null;
		Area bestArea = null;
		double bestMeters = Double.MAX_VALUE;
		for (AreaPoint point : points) {
			if (point.getLat() == null || point.getLng() == null) {
				continue;
			}
			Optional<Area> area = Area.fromKey(point.getAreaKey());
			if (area.isEmpty()) {
				continue;
			}
			double meters = GeoDistance.meters(lat, lng, point.getLat(), point.getLng());
			// 상자는 반경을 감싸는 사각형이라 모서리에서는 반경 밖의 장소가 섞여 온다 — 실제 거리로 다시 거른다.
			if (meters > NEAREST_GU_MAX_METERS) {
				continue;
			}
			if (best == null || meters < bestMeters || (meters == bestMeters && isBefore(point, best))) {
				best = point;
				bestArea = area.get();
				bestMeters = meters;
			}
		}
		return bestArea == null ? null : bestArea.gu();
	}

	/** 거리가 같을 때의 순서 — 장소 번호, 그다음 구/동 열쇠. */
	private static boolean isBefore(AreaPoint candidate, AreaPoint current) {
		int byPlace = candidate.getPlaceId().compareTo(current.getPlaceId());
		return byPlace != 0 ? byPlace < 0 : candidate.getAreaKey().compareTo(current.getAreaKey()) < 0;
	}

	/**
	 * 좌표 두 칸을 검사한다. 둘 다 없으면 위치를 안 쓰는 요청이라 {@code false}, 둘 다 있고 올바르면 {@code true}.
	 * 하나만 왔거나 숫자가 아니거나 범위 밖이면 400 이다 — 조용히 무시하면 앱이 좌표를 잘못 보내도 아무도 모른다.
	 */
	private static boolean requireCoordinates(Double lat, Double lng) {
		if (lat == null && lng == null) {
			return false;
		}
		if (lat == null || lng == null) {
			throw new PlaceRequestException("INVALID_REQUEST", "위도와 경도는 함께 보내 주세요.", List.of("lat", "lng"));
		}
		if (!Double.isFinite(lat) || lat < -90.0 || lat > 90.0) {
			throw new PlaceRequestException("INVALID_REQUEST", "위도는 -90에서 90 사이여야 합니다.", List.of("lat"));
		}
		if (!Double.isFinite(lng) || lng < -180.0 || lng > 180.0) {
			throw new PlaceRequestException("INVALID_REQUEST", "경도는 -180에서 180 사이여야 합니다.", List.of("lng"));
		}
		return true;
	}

	/** 맛집 목록. 옛 호출자를 위해 남겨 둔다. */
	public RestaurantPageResponse restaurants(String gu, String dong, Integer limit) {
		return restaurants(Kind.RESTAURANT, gu, dong, limit);
	}

	/**
	 * 구(와 동)로 거른 목록. {@code dong} 이 비면 구 전체다.
	 *
	 * @throws PlaceRequestException 구가 비었거나, 이름이 너무 길거나, {@code limit} 이 범위 밖이다
	 */
	public RestaurantPageResponse restaurants(Kind kind, String gu, String dong, Integer limit) {
		String guName = requireName(gu, "gu", "구를 지정해 주세요.");
		String dongName = (dong == null || dong.isBlank()) ? null : requireName(dong, "dong", "동 이름이 올바르지 않습니다.");
		int effectiveLimit = requireLimit(limit);

		Limit scan = Limit.of(MAX_SCAN + 1);
		List<Place> found = (dongName == null)
				? this.repository.findInGu(InterestTagCode.FEATURE_TYPE, kind.exploreKey(),
						CertifiedRestaurant.AREA_FEATURE_TYPE, likePrefix(guName), scan)
				: this.repository.findInArea(InterestTagCode.FEATURE_TYPE, kind.exploreKey(),
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
		boolean broadcastOrAward = false;
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
				Optional<Source> source = Source.fromCode(feature.getFeatureKey());
				source.ifPresent(sources::add);
				if (source.filter(found -> found == Source.REDTABLE_FEATURED).isPresent()
						&& readsBroadcastOrAward(feature.getValue())) {
					broadcastOrAward = true;
				}
			}
		}
		return new RestaurantItem(PlaceSummaryResponse.ofFacetMatch(place), area == null ? null : area.gu(),
				area == null ? null : area.dong(), sources.stream().map(Source::name).toList(),
				broadcastOrAward ? Boolean.TRUE : null);
	}

	/**
	 * 레드테이블 표식의 값({@code {"source":"레드테이블","broadcastOrAward":true}})에서 방송·수상 표시를 읽는다. 값이 비었거나 JSON 이 아니거나
	 * 칸이 참이 아니면 거짓이다 — 남이 다른 꼴로 넣은 값이 응답을 깨지 못하게 하고, 모르는 것을 방송·수상으로 부풀리지 않는다.
	 */
	static boolean readsBroadcastOrAward(String valueJson) {
		if (valueJson == null || valueJson.isBlank()) {
			return false;
		}
		try {
			JsonNode value = MAPPER.readTree(valueJson);
			return value != null && value.path("broadcastOrAward").isBoolean() && value.path("broadcastOrAward").asBoolean();
		}
		catch (RuntimeException exception) {
			return false;
		}
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
