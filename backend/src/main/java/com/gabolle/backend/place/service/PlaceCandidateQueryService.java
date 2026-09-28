package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.place.api.PlaceCandidateRequest;
import com.gabolle.backend.place.config.PlaceProperties;
import com.gabolle.backend.place.api.PlaceCandidateResponse;
import com.gabolle.backend.place.api.PlaceFeatureView;
import com.gabolle.backend.place.domain.AccommodationCategories;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 추천 엔진에 넘길 후보를 뽑는다.
 *
 * <p>질의는 장소 수와 무관하게 두 번이다 — 경계상자(+ required 첫 번째)로 장소 목록 하나,
 * 그 장소들의 피처를 {@code IN :ids} 로 하나. 나머지 조건은 전부 자바에서 거른다. DB 는 좁히는
 * 데만 쓴다. JPQL 은 {@code evidence_status} 까지만 볼 수 있어 확인된 부재(값이 {@code false}
 * 인 행)를 못 거르므로, 있고·없음의 판정은 {@code PlaceFeature.indicatesPresence()} 가 한다.
 *
 * <p>영업시간은 일부 장소에만 있다. 문 닫은 곳은 빼되, 후보 하나라도 영업시간을 모르는 채
 * 남았으면 이 조건을 적용했다고 말하지 않고 {@code notApplied} 에 {@code NOT_COLLECTED} 로
 * 적는다. 여기서 {@code OpeningHoursFilterPort} 를 부르지 않는 것은 장소마다 질의가 나가기
 * 때문이다 — 이미 읽어 둔 피처를 {@link OpeningHoursValue} 로 판정한다.
 *
 * <p>자르기가 두 번 일어난다. 경계상자 상한({@code gabolle.place.candidate-max-scanned})은
 * {@code place_id} 순으로 자르고(JPQL 에 삼각함수가 없어 거리로는 못 자른다) 잘리면
 * {@code scanTruncated} 로 알린다. {@code limit} 은 거리순 정렬 뒤에 자른다.
 *
 * <p>{@code limit} 은 "가까운 순 N 곳" 이지 "좋은 순 N 곳" 이 아니다 — 이 서비스는 점수를
 * 모른다. 부르는 쪽이 채점을 한다면 작은 {@code limit} 을 주면 안 된다. 잘려 나간 장소는 점수를
 * 매길 기회조차 없고 그 사실이 어디에도 안 남는다.
 *
 * <p>최소 개수를 못 채워도 조건을 자동으로 풀지 않는다. 알레르기 조건을 풀어 채운 목록은 빈
 * 목록보다 나쁘다. {@code belowMinimum} 으로 알리고 판단은 호출자에게 넘긴다.
 */
@Service
@Profile({"db", "dev"})
public class PlaceCandidateQueryService {

	private static final Logger log = LoggerFactory.getLogger(PlaceCandidateQueryService.class);

	/**
	 * 일반 후보에서 뺄 숙소 갈래. 조회 쪽 목록을 그대로 따라가므로 숙소 낱말이 늘면 여기도 같이
	 * 는다 — 두 곳에 따로 적으면 새 낱말이 후보에 새는 것을 아무도 못 본다. 소문자로 두는 것은
	 * {@code place.category} 비교가 대소문자를 안 가리기 때문이다.
	 */
	private static final Set<String> ACCOMMODATION_CATEGORIES = AccommodationCategories.CODES.stream()
			.map(code -> code.toLowerCase(Locale.ROOT))
			.collect(Collectors.toUnmodifiableSet());

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	private final ObjectMapper objectMapper;

	/**
	 * 경계상자에서 읽어 올 최대 행 수({@code gabolle.place.candidate-max-scanned}).
	 * 부산 반경 5km 안에는 음식점만 평균 9,422곳이라, 이 값이 작으면 자바가 거리를 재기 전에
	 * 대부분이 사라진다. 상수가 아니라 설정인 것은 운영에서 올릴 수 있어야 하기 때문이다.
	 */
	private final int maxScanned;

	public PlaceCandidateQueryService(PlaceRepository placeRepository,
			PlaceFeatureRepository placeFeatureRepository,
			ObjectMapper objectMapper, PlaceProperties properties) {
		this.placeRepository = placeRepository;
		this.placeFeatureRepository = placeFeatureRepository;
		this.objectMapper = objectMapper;
		this.maxScanned = properties.getCandidateMaxScanned();
	}

	@Transactional(readOnly = true)
	public PlaceCandidateResponse findCandidates(PlaceCandidateRequest request) {
		double lat = request.center().lat();
		double lng = request.center().lng();
		GeoDistance.BoundingBox box = GeoDistance.boundingBox(lat, lng, request.radiusM());

		List<String> applied = new ArrayList<>();
		List<PlaceCandidateResponse.NotApplied> notApplied = new ArrayList<>();
		applied.add("CENTER_RADIUS");

		// 질의 1. 경계상자, 그리고 required 가 있으면 그중 첫 번째까지 DB 에서 건다.
		List<PlaceCandidateRequest.FeatureMatch> required = request.requiredOrEmpty();
		List<Place> scanned;
		if (required.isEmpty()) {
			scanned = this.placeRepository.findWithinBoundingBox(
					box.minLat(), box.maxLat(), box.minLng(), box.maxLng(), Limit.of(this.maxScanned + 1));
		}
		else {
			PlaceCandidateRequest.FeatureMatch first = required.get(0);
			scanned = this.placeRepository.findWithinBoundingBoxHavingFeature(
					box.minLat(), box.maxLat(), box.minLng(), box.maxLng(),
					first.featureType(), first.featureKey(), Limit.of(this.maxScanned + 1));
			applied.add("REQUIRED_FEATURES");
		}

		boolean scanTruncated = scanned.size() > this.maxScanned;
		if (scanTruncated) {
			scanned = scanned.subList(0, this.maxScanned);
		}

		// 반경과 카테고리는 자바에서 — 경계상자는 반경보다 넓은 사각형이라 바깥이 섞여 있다.
		Set<String> categories = new LinkedHashSet<>();
		for (String category : request.categoriesOrEmpty()) {
			categories.add(category.toLowerCase(Locale.ROOT));
		}
		if (!categories.isEmpty()) {
			applied.add("CATEGORY");
		}
		// 갈래가 비어 있는 장소는 요청이 갈래를 좁혔는지와 무관하게 언제나 뺀다. 적재가 갈래를
		// 비우는 것은 "앱의 여섯 낱말 중 이것을 가리키는 것이 없다" 는 뜻이라(TourApiCategory 가
		// 레포츠를 그렇게 둔다) 어떤 취향으로도 안 골라져야 한다.
		// 🔴 이 줄은 "가리킬 낱말이 없다" 와 "아직 분류 안 했다" 를 못 가른다. 2026-09-21 실측으로는
		// 빈칸이 레포츠 29곳뿐이라 문제가 안 되지만, 앞으로 "모른다" 를 빈칸으로 남기는 적재기가
		// 생기면 그 장소들이 여기서 조용히 사라진다. 그때는 빈칸 대신 모름을 값으로 남겨야 한다.
		// 필수 방문지 지정은 PlaceRepository.findByCategoryIn 을 따로 쓰므로 이 제외에 영향받지 않는다.
		applied.add("NON_EMPTY_CATEGORY");

		// 🔴 숙소는 갈래가 채워져 있어도 일반 후보에서 뺀다 (S15P21E201-1383).
		// 2026-09-21 에 숙박 65곳의 빈 갈래를 LODGING 으로 채웠다. 그전까지 숙소가 후보에 안
		// 들어온 유일한 이유는 위의 "빈 갈래 제외" 였고, 값을 채우는 순간 그 방벽이 없어진다.
		// 아래 categories 비교는 요청이 갈래를 좁혔을 때만 걸리므로, 안 좁힌 요청에서는 호텔이
		// 관광지처럼 일정에 섞이게 된다. 숙소는 취향으로 고르는 갈래가 아니라 따로 지정하는
		// 것이고, 그 길은 PlaceRepository.findByCategoryIn 으로 이미 따로 있다.
		applied.add("NOT_ACCOMMODATION");

		// 🔴 부산 시 경계 밖은 추천 후보에서만 뺀다 (S15P21E201-1617, 사용자 결정). 오픈스트리트맵 적재가 부산을 덮는
		//    사각형으로 받아 와 김해·양산 장소가 섞였다(운영 858곳). 검색·근처 보기는 이 경로를 안 거쳐 그대로 보이고,
		//    장소 행도 그대로다. 사용자가 직접 고른 「꼭 갈 곳」은 추천 엔진이 이 조회 뒤에 따로 채워 넣는다.
		applied.add("WITHIN_BUSAN");

		Map<UUID, Long> distances = new HashMap<>();
		List<Place> withinRadius = new ArrayList<>();
		for (Place place : scanned) {
			String placeCategory = place.getCategory();
			if (placeCategory == null || placeCategory.isBlank()) {
				continue;
			}
			if (ACCOMMODATION_CATEGORIES.contains(placeCategory.toLowerCase(Locale.ROOT))) {
				continue;
			}
			if (!BusanBoundary.contains(place.getLat(), place.getLng())) {
				continue;
			}
			if (!categories.isEmpty() && !categories.contains(placeCategory.toLowerCase(Locale.ROOT))) {
				continue;
			}
			double meters = GeoDistance.meters(lat, lng, place.getLat(), place.getLng());
			if (meters > request.radiusM()) {
				continue;
			}
			distances.put(place.getPlaceId(), Math.round(meters));
			withinRadius.add(place);
		}

		// 질의 2. 남은 후보의 피처를 한 번에 — 장소마다 읽지 않는다.
		Map<UUID, List<PlaceFeature>> featuresByPlace = loadFeatures(withinRadius);

		// required 를 DB 에서 한 번 걸렀어도 자바에서 전부 다시 본다. DB 필터는 확인된 부재
		// (값이 false)를 못 거른다.
		List<PlaceCandidateRequest.FeatureMatch> excluded = request.excludedOrEmpty();
		if (!excluded.isEmpty()) {
			applied.add("EXCLUDED_FEATURES");
		}

		List<PlaceCandidateResponse.Candidate> candidates = new ArrayList<>();
		Set<String> datasetVersions = new LinkedHashSet<>();
		OffsetDateTime openNowAt = request.openNowAt();
		boolean someHoursUnknown = false;
		for (Place place : withinRadius) {
			List<PlaceFeature> features = featuresByPlace.getOrDefault(place.getPlaceId(), List.of());
			if (!hasAll(features, required) || hasAny(features, excluded)) {
				continue;
			}
			if (openNowAt != null) {
				OpeningHoursFilterPort.Answer answer = openingHoursAt(features, openNowAt);
				if (answer == OpeningHoursFilterPort.Answer.CLOSED) {
					// 원천이 "그 시각에 닫는다" 고 말한 곳이다. 빼는 것이 틀릴 여지가 없다.
					continue;
				}
				if (answer == OpeningHoursFilterPort.Answer.NOT_COLLECTED) {
					// 빼지 않는다. 모른다고 지우면 영업시간을 아직 안 넣은 장소가 통째로
					// 사라지고, 사용자에게는 그것이 "그 시각에 여는 곳이 없다" 로 보인다.
					someHoursUnknown = true;
				}
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

		// 하나라도 모르는 채 남았으면 이 조건을 "적용했다" 고 말하지 않는다.
		if (openNowAt != null) {
			if (someHoursUnknown) {
				notApplied.add(new PlaceCandidateResponse.NotApplied(
						OpeningHoursFilterPort.CHECK, OpeningHoursFilterPort.REASON_NOT_COLLECTED));
			}
			else {
				applied.add(OpeningHoursFilterPort.CHECK);
			}
		}

		int minimum = request.minimumCountOrDefault();
		return new PlaceCandidateResponse(candidates, candidates.size(), minimum,
				candidates.size() < minimum, List.copyOf(applied), List.copyOf(notApplied),
				scanTruncated, List.copyOf(datasetVersions));
	}

	/**
	 * 이미 읽어 둔 피처에서 영업시간 하나를 찾아 판정한다. 행이 없으면 모른다이고, 지금은 그것이
	 * 대부분의 장소에서 나오는 정상적인 답이다.
	 */
	private static OpeningHoursFilterPort.Answer openingHoursAt(List<PlaceFeature> features,
			OffsetDateTime at) {
		for (PlaceFeature feature : features) {
			if (PlaceFeatureOpeningHoursFilter.FEATURE_TYPE.equals(feature.getFeatureType())) {
				return OpeningHoursValue.answerAt(feature.getValue(), at);
			}
		}
		return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
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
	 * 확실히 있는 것만 통과시킨다. {@code UNKNOWN} 도, 확인된 부재({@code VERIFIED} + 값
	 * {@code false})도 "있다" 가 아니다.
	 */
	private boolean hasAll(List<PlaceFeature> features, List<PlaceCandidateRequest.FeatureMatch> required) {
		for (PlaceCandidateRequest.FeatureMatch match : required) {
			if (!hasConfirmedFeature(features, match)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * {@link #hasAll} 과 판정 방향이 반대다. 알레르기 같은 안전 제약이 지나가는 길이라
	 * "모른다" 를 통과시키면 안 된다 — 확인된 부재만 안전으로 보고 그 밖에는 전부 뺀다.
	 */
	private boolean hasAny(List<PlaceFeature> features, List<PlaceCandidateRequest.FeatureMatch> excluded) {
		for (PlaceCandidateRequest.FeatureMatch match : excluded) {
			for (PlaceFeature feature : features) {
				if (sameFeature(feature, match) && feature.cannotRuleOutPresence()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean hasConfirmedFeature(List<PlaceFeature> features, PlaceCandidateRequest.FeatureMatch match) {
		for (PlaceFeature feature : features) {
			if (sameFeature(feature, match) && feature.indicatesPresence()) {
				return true;
			}
		}
		return false;
	}

	private boolean sameFeature(PlaceFeature feature, PlaceCandidateRequest.FeatureMatch match) {
		if (!feature.getFeatureType().equals(match.featureType())) {
			return false;
		}
		return match.featureKey() == null || match.featureKey().equals(feature.getFeatureKey());
	}

	/**
	 * 값이 있는데 파싱에 실패한 행은 옮기지 않고 버린다. {@code null} 을 그대로 실으면 채점 쪽이
	 * "행이 있고 값이 null" 과 "행이 없음" 을 구분하지 못한 채 안전 판정을 내린다. 행을 빼면
	 * {@code UNVERIFIED} 로 읽혀, 판정 방향에 무관하게 안전한 기본값으로 떨어진다.
	 */
	private List<PlaceFeatureView> toViews(List<PlaceFeature> features) {
		List<PlaceFeatureView> views = new ArrayList<>(features.size());
		for (PlaceFeature feature : features) {
			String raw = feature.getValue();
			JsonNode value = readValue(raw);
			if (value == null && raw != null && !raw.isBlank()) {
				log.warn(
						"place_feature 값 파싱 실패 — 안전을 위해 이 행을 후보 응답에서 뺀다. "
								+ "placeId={}, featureType={}, featureKey={}",
						feature.getPlaceId(), feature.getFeatureType(), feature.getFeatureKey());
				continue;
			}
			views.add(new PlaceFeatureView(feature.getFeatureType(), feature.getFeatureKey(),
					feature.getEvidenceStatus().name(), value, feature.getObservedAt(), feature.getSourceType()));
		}
		return views;
	}

	/** 못 읽으면 {@code null}. 부르는 쪽이 "값이 있었는가" 와 함께 보고 판단한다. */
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
