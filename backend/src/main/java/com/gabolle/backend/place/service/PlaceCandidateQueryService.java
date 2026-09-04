package com.gabolle.backend.place.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 추천 엔진에 넘길 후보를 뽑는다 (S15P21E201-102).
 *
 * <h2>🔴 질의는 정확히 두 번이다</h2>
 *
 * 완료 기준이 <b>"조회 한 번으로 후보가 돌아오고 질의 개수가 장소 수에 비례하지 않는다"</b> 이다.
 * 그래서 이렇게 나눈다.
 *
 * <ol>
 * <li>경계상자(+ 필요하면 표식 하나)로 장소 목록을 읽는다 — 질의 1</li>
 * <li>그 장소들의 피처를 {@code WHERE placeId IN :ids} 로 한 번에 읽는다 — 질의 2</li>
 * <li>나머지 조건(카테고리, 남은 required, excluded, 반경)은 <b>자바에서</b> 거른다</li>
 * </ol>
 *
 * <p>required 가 여럿이어도 질의를 늘리지 않는 이유가 여기 있다. 첫 번째 것만 DB 에서 거르고
 * 나머지는 이미 읽어 둔 피처로 판정한다. DB 에서 전부 거르면 조건 수만큼 EXISTS 가 붙어 질의문이
 * 조건에 따라 달라지고, 그러면 "질의 두 번" 이라는 계약을 테스트로 지키기 어려워진다.
 *
 * <h2>🔴 못 거른 조건을 숨기지 않는다</h2>
 *
 * 영업시간은 칸이 없어 지금 거를 수 없다. 조용히 무시하면 호출자는 걸러진 줄 알고 영업이 끝난 곳을
 * 추천한다. 그래서 {@code notApplied} 에 적어 내보낸다.
 *
 * <h2>🔴 모자라면 모자란 채로 돌려준다</h2>
 *
 * 최소 개수를 못 채워도 조건을 자동으로 풀지 않는다. 알레르기 조건을 슬쩍 풀어 채운 목록은 빈
 * 목록보다 나쁘다 — 사용자는 걸러진 줄 알고 먹는다. {@code belowMinimum} 으로 알리고 판단은
 * 호출자에게 넘긴다.
 */
@Service
@Profile({"db", "dev"})
public class PlaceCandidateQueryService {

	/** 경계상자에서 읽어 올 최대 행. 넘으면 자르되 응답에 잘렸다고 적는다. */
	private static final int MAX_SCANNED = 2_000;

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	private final OpeningHoursFilterPort openingHoursFilter;

	private final ObjectMapper objectMapper;

	public PlaceCandidateQueryService(PlaceRepository placeRepository,
			PlaceFeatureRepository placeFeatureRepository, OpeningHoursFilterPort openingHoursFilter,
			ObjectMapper objectMapper) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
		this.openingHoursFilter = openingHoursFilter;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public PlaceCandidateResponse findCandidates(PlaceCandidateRequest request) {
		double lat = request.center().lat();
		double lng = request.center().lng();
		GeoDistance.BoundingBox box = GeoDistance.boundingBox(lat, lng, request.radiusM());

		List<String> applied = new ArrayList<>();
		List<PlaceCandidateResponse.NotApplied> notApplied = new ArrayList<>();
		applied.add("CENTER_RADIUS");

		// ── 질의 1. 경계상자, 그리고 required 가 있으면 그중 첫 번째까지 DB 에서 건다.
		List<PlaceCandidateRequest.FeatureMatch> required = request.requiredOrEmpty();
		List<Place> scanned;
		if (required.isEmpty()) {
			scanned = this.placeRepository.findWithinBoundingBox(
					box.minLat(), box.maxLat(), box.minLng(), box.maxLng(), Limit.of(MAX_SCANNED + 1));
		}
		else {
			PlaceCandidateRequest.FeatureMatch first = required.get(0);
			scanned = this.placeRepository.findWithinBoundingBoxHavingFeature(
					box.minLat(), box.maxLat(), box.minLng(), box.maxLng(),
					first.featureType(), first.featureKey(), Limit.of(MAX_SCANNED + 1));
			applied.add("REQUIRED_FEATURES");
		}

		boolean scanTruncated = scanned.size() > MAX_SCANNED;
		if (scanTruncated) {
			scanned = scanned.subList(0, MAX_SCANNED);
		}

		// 반경과 카테고리는 자바에서 — 경계상자는 반경보다 넓은 사각형이라 바깥이 섞여 있다.
		Set<String> categories = new LinkedHashSet<>();
		for (String category : request.categoriesOrEmpty()) {
			categories.add(category.toLowerCase(Locale.ROOT));
		}
		if (!categories.isEmpty()) {
			applied.add("CATEGORY");
		}

		Map<UUID, Long> distances = new HashMap<>();
		List<Place> withinRadius = new ArrayList<>();
		for (Place place : scanned) {
			if (!categories.isEmpty()
					&& (place.getCategory() == null
							|| !categories.contains(place.getCategory().toLowerCase(Locale.ROOT)))) {
				continue;
			}
			double meters = GeoDistance.meters(lat, lng, place.getLat(), place.getLng());
			if (meters > request.radiusM()) {
				continue;
			}
			distances.put(place.getPlaceId(), Math.round(meters));
			withinRadius.add(place);
		}

		// ── 질의 2. 남은 후보의 피처를 한 번에. 🔴 장소마다 읽지 않는다.
		Map<UUID, List<PlaceFeature>> featuresByPlace = loadFeatures(withinRadius);

		// 남은 required 와 excluded 는 이미 읽은 피처로 판정한다 — 질의가 늘지 않는다.
		List<PlaceCandidateRequest.FeatureMatch> remainingRequired =
				required.isEmpty() ? List.of() : required.subList(1, required.size());
		List<PlaceCandidateRequest.FeatureMatch> excluded = request.excludedOrEmpty();
		if (!excluded.isEmpty()) {
			applied.add("EXCLUDED_FEATURES");
		}

		List<PlaceCandidateResponse.Candidate> candidates = new ArrayList<>();
		Set<String> datasetVersions = new LinkedHashSet<>();
		for (Place place : withinRadius) {
			List<PlaceFeature> features = featuresByPlace.getOrDefault(place.getPlaceId(), List.of());
			if (!hasAll(features, remainingRequired) || hasAny(features, excluded)) {
				continue;
			}
			if (place.getDatasetVersion() != null) {
				datasetVersions.add(place.getDatasetVersion());
			}
			candidates.add(new PlaceCandidateResponse.Candidate(
					place.getPlaceId(), place.getNameKo(), place.getCategory(),
					place.getLat(), place.getLng(), distances.get(place.getPlaceId()),
					toViews(features)));
		}

		candidates.sort(Comparator.comparingLong(PlaceCandidateResponse.Candidate::distanceM)
				.thenComparing(candidate -> candidate.placeId().toString()));

		int limit = request.limitOrDefault();
		if (candidates.size() > limit) {
			candidates = new ArrayList<>(candidates.subList(0, limit));
		}

		// 🔴 요청은 받았지만 못 건 조건을 적어 내보낸다.
		if (request.openNowAt() != null && !this.openingHoursFilter.isAvailable()) {
			notApplied.add(new PlaceCandidateResponse.NotApplied(
					"OPENING_HOURS", this.openingHoursFilter.unavailableReason()));
		}

		int minimum = request.minimumCountOrDefault();
		return new PlaceCandidateResponse(candidates, candidates.size(), minimum,
				candidates.size() < minimum, List.copyOf(applied), List.copyOf(notApplied),
				scanTruncated, List.copyOf(datasetVersions));
	}

	private Map<UUID, List<PlaceFeature>> loadFeatures(List<Place> places) {
		if (places.isEmpty()) {
			return Map.of();
		}
		List<UUID> ids = places.stream().map(Place::getPlaceId).toList();
		Map<UUID, List<PlaceFeature>> byPlace = new HashMap<>();
		for (PlaceFeature feature : this.placeFeatureRepository.findByPlaceIdIn(ids)) {
			byPlace.computeIfAbsent(feature.getPlaceId(), key -> new ArrayList<>()).add(feature);
		}
		return byPlace;
	}

	/**
	 * 🔴 {@code UNKNOWN} 은 "있다" 로 세지 않는다. 모르는 것을 통과시키면 확인 안 된 장소가
	 * 조건을 만족한 것처럼 후보에 들어간다.
	 */
	private boolean hasAll(List<PlaceFeature> features, List<PlaceCandidateRequest.FeatureMatch> required) {
		for (PlaceCandidateRequest.FeatureMatch match : required) {
			if (!matches(features, match)) {
				return false;
			}
		}
		return true;
	}

	private boolean hasAny(List<PlaceFeature> features, List<PlaceCandidateRequest.FeatureMatch> excluded) {
		for (PlaceCandidateRequest.FeatureMatch match : excluded) {
			if (matches(features, match)) {
				return true;
			}
		}
		return false;
	}

	private boolean matches(List<PlaceFeature> features, PlaceCandidateRequest.FeatureMatch match) {
		for (PlaceFeature feature : features) {
			if (!feature.getFeatureType().equals(match.featureType())) {
				continue;
			}
			if (match.featureKey() != null && !match.featureKey().equals(feature.getFeatureKey())) {
				continue;
			}
			if (feature.getEvidenceStatus() != PlaceEvidenceStatus.UNKNOWN) {
				return true;
			}
		}
		return false;
	}

	private List<PlaceFeatureView> toViews(List<PlaceFeature> features) {
		List<PlaceFeatureView> views = new ArrayList<>(features.size());
		for (PlaceFeature feature : features) {
			views.add(new PlaceFeatureView(feature.getFeatureType(), feature.getFeatureKey(),
					feature.getEvidenceStatus().name(), readValue(feature.getValue()),
					feature.getObservedAt(), feature.getSourceType()));
		}
		return views;
	}

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
}
