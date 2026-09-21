package com.gabolle.backend.place.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.gabolle.backend.place.api.NearbyPlaceItem;
import com.gabolle.backend.place.api.NearbyPlaceResponse;
import com.gabolle.backend.place.config.PlaceProperties;
import com.gabolle.backend.place.config.PlaceProperties.PurposeSpec;
import com.gabolle.backend.place.domain.InterestTagCode;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 근처 장소 거리순 조회.
 *
 * <p>정렬은 거리만으로 한다. 인기 점수나 평점이 비교자에 들어오면 "근처순은 항상 거리순" 이라는
 * 계약이 깨지고, 그 실수는 결과가 가끔 섞이는 애매한 증상으로만 드러난다. 거리가 같을 때만
 * {@code placeId} 로 갈라 재현 가능하게 한다. 다른 정렬이 필요하면 이 서비스를 고치지 말고 새
 * API 를 만든다.
 *
 * <p>경계상자로 후보를 좁힌 뒤 자바에서 실제 거리를 재는 이유는 {@link GeoDistance} 에 있다. 첫
 * 반경에서 {@link PlaceProperties#getNearbyMinimumCount()} 를 못 채우면 사다리의 다음 반경으로
 * 넓히고, 넓힌 사실을 {@code radiusExpanded}·{@code effectiveRadiusM}·{@code expansionSteps} 로
 * 응답에 싣는다 — 숨기면 사용자가 "근처" 라고 믿는 결과가 수 km 밖일 수 있다.
 *
 * <p>좁히는 길이 둘이다. {@code facetKey} 는 {@link InterestTagCode} 의 여덟 갈래를
 * {@code INTEREST_TAG} 표식으로 좁히고, 이쪽이 지금의 정본이다. {@code purpose} 는
 * {@code gabolle.place.purposes} 설정으로 카테고리와 표식을 묶는 길이고 설정이 아직 비어 있다 —
 * "점심 먹을 곳" 처럼 여러 갈래를 묶는 목적이 필요해질 때를 위해 남겨 뒀다. 둘을 함께 보내면
 * 400 이다. 하나를 조용히 이기게 하면 요청자는 자기가 보낸 필터가 무시된 것을 모른다.
 *
 * <p>{@code purpose} 는 선택값이다. 설정이 비어 있는 동안 필수로 두면 어떤 입력으로도 200 을 낼
 * 수 없다. 없으면 목적 필터를 적용하지 않고 {@link NearbyPlaceResponse#purposeApplied()} 로
 * 알린다. 보냈는데 설정에 없으면 {@code UNKNOWN_PURPOSE} 로 거부한다 — 오타를 "필터 없음" 으로
 * 넘기지 않기 위해서다.
 *
 * <p>{@code radiusMeters} 가 오면 설정 사다리를 무시하고 그 반경과 두 배, 두 칸만 쓴다. 화면이
 * "1km 안" 이라고 말해 놓고 5km 결과를 보여줄 수는 없다.
 */
@Service
@Profile({ "db", "dev" })
public class NearbyPlaceService {

	private static final List<Integer> DEFAULT_RADIUS_LADDER_METERS = List.of(1000, 2000, 5000);

	private final PlaceRepository placeRepository;

	private final PlaceProperties properties;

	public NearbyPlaceService(PlaceRepository placeRepository, PlaceProperties properties) {
		this.placeRepository = placeRepository;
		this.properties = properties;
	}

	/** 옛 호출자를 위해 남겨 둔다 — 갈래·반경 없이 부르면 예전과 똑같이 동작한다. */
	public NearbyPlaceResponse findNearby(double lat, double lng, String purpose, int limit) {
		return findNearby(lat, lng, purpose, null, null, limit);
	}

	public NearbyPlaceResponse findNearby(double lat, double lng, String purpose, String facetKey,
			Integer radiusMeters, int limit) {
		validateCoordinates(lat, lng);
		validateLimit(limit);

		boolean purposeApplied = purpose != null && !purpose.isBlank();
		boolean facetKeyApplied = facetKey != null && !facetKey.isBlank();
		if (purposeApplied && facetKeyApplied) {
			throw new PlaceRequestException("INVALID_REQUEST", "목적과 갈래 중 하나만 지정해 주세요.",
					List.of("purpose", "facetKey"));
		}

		// spec 이 null 이면 아래(scanRadius·fetchCandidates·applyCategoryFilter)가 전부 그것을
		// "필터 없음" 으로 다룬다.
		PurposeSpec spec = null;
		if (purposeApplied) {
			spec = resolvePurpose(purpose);
		}
		else if (facetKeyApplied) {
			spec = resolveFacetKey(facetKey);
		}

		List<Integer> ladder = ladderFor(radiusMeters);
		int requestedRadiusM = ladder.get(0);
		int effectiveRadiusM = requestedRadiusM;
		int expansionSteps = 0;
		boolean scanTruncated = false;
		List<ScoredPlace> matches = List.of();

		for (int step = 0; step < ladder.size(); step++) {
			int stepRadiusM = ladder.get(step);
			StepResult result = scanRadius(lat, lng, stepRadiusM, spec);
			scanTruncated = scanTruncated || result.truncated();
			matches = result.matches();
			effectiveRadiusM = stepRadiusM;
			expansionSteps = step;

			boolean lastStep = step == ladder.size() - 1;
			if (enough(matches.size(), radiusMeters) || lastStep) {
				break;
			}
		}

		List<NearbyPlaceItem> items = matches.stream()
				// 거리, 그리고 거리가 같을 때만 placeId. 다른 칼럼은 여기 들어오면 안 된다.
				.sorted(Comparator.comparingDouble(ScoredPlace::distanceM)
						.thenComparing(scored -> scored.place().getPlaceId()))
				.limit(limit)
				.map(scored -> NearbyPlaceItem.from(scored.place(), Math.round(scored.distanceM())))
				.toList();

		return new NearbyPlaceResponse(items, requestedRadiusM, effectiveRadiusM,
				expansionSteps > 0, expansionSteps, scanTruncated, limit, purposeApplied, facetKeyApplied);
	}

	private StepResult scanRadius(double lat, double lng, int radiusMeters, PurposeSpec spec) {
		GeoDistance.BoundingBox box = GeoDistance.boundingBox(lat, lng, radiusMeters);
		int scanLimit = this.properties.getNearbyMaxScanned();
		List<Place> candidates = fetchCandidates(box, spec, scanLimit);

		// limit+1 로 받아서 "더 있었는가" 만 본다. 질의에 ORDER BY 가 없어 잘리는 순서가 정해져
		// 있지 않으므로, 잘렸다는 사실을 scanTruncated 로 알린다 — 그러지 않으면 반경 안의 진짜
		// 최단거리 장소가 빠져도 아무도 모른다.
		boolean truncated = candidates.size() > scanLimit;
		List<Place> scanned = truncated ? candidates.subList(0, scanLimit) : candidates;

		List<Place> filtered = applyCategoryFilter(scanned, spec);

		List<ScoredPlace> matches = filtered.stream()
				.filter(Place::hasCoordinates)
				.map(place -> new ScoredPlace(place, GeoDistance.meters(lat, lng, place.getLat(), place.getLng())))
				.filter(scored -> scored.distanceM() <= radiusMeters)
				.toList();

		return new StepResult(matches, truncated);
	}

	/**
	 * {@code featureType} 이 있으면 표식으로 좁히고, 없거나 {@code spec} 이 {@code null} 이면
	 * 경계상자만으로 가져온다.
	 */
	private List<Place> fetchCandidates(GeoDistance.BoundingBox box, PurposeSpec spec, int scanLimit) {
		Limit limit = Limit.of(scanLimit + 1);
		if (spec != null && spec.featureType() != null && !spec.featureType().isBlank()) {
			return this.placeRepository.findWithinBoundingBoxHavingFeature(
					box.minLat(), box.maxLat(), box.minLng(), box.maxLng(),
					spec.featureType(), spec.featureKey(), limit);
		}
		return this.placeRepository.findWithinBoundingBox(
				box.minLat(), box.maxLat(), box.minLng(), box.maxLng(), limit);
	}

	/**
	 * {@code categories} 가 있으면 한 번 더 거른다. {@code featureType} 으로 이미 좁힌 뒤라도
	 * 똑같이 적용된다. {@code spec} 이 {@code null} 이면 그대로 반환한다.
	 */
	private List<Place> applyCategoryFilter(List<Place> places, PurposeSpec spec) {
		List<String> categories = spec == null ? null : spec.categories();
		if (categories == null || categories.isEmpty()) {
			return places;
		}
		return places.stream()
				.filter(place -> place.getCategory() != null
						&& categories.stream().anyMatch(category -> category.equalsIgnoreCase(place.getCategory())))
				.toList();
	}

	/**
	 * 멈출 기준이 호출자가 반경을 정했는지에 따라 다르다. 안 정했으면 {@code nearbyMinimumCount}
	 * 를 채울 때까지 넓히고, 정했으면 하나라도 있으면 멈춘다 — 개수를 채우려고 넓히면 요청한
	 * 반경을 서버가 무시하는 것이 된다.
	 */
	private boolean enough(int found, Integer radiusMeters) {
		if (radiusMeters != null) {
			return found > 0;
		}
		return found >= this.properties.getNearbyMinimumCount();
	}

	/**
	 * 호출자가 반경을 정했으면 그 반경과 두 배, 두 칸만 쓴다. 안 정했으면 설정 사다리다.
	 * 설정 사다리를 그대로 쓰면 "500m 안" 을 요청한 화면에 5km 결과가 갈 수 있다.
	 */
	private List<Integer> ladderFor(Integer radiusMeters) {
		if (radiusMeters == null) {
			return sortedLadder();
		}
		validateRadius(radiusMeters);
		return List.of(radiusMeters, radiusMeters * 2);
	}

	/**
	 * 여덟 갈래 코드를 {@code INTEREST_TAG} 표식 필터로 바꾼다. 모르는 코드는 400 으로 거부한다 —
	 * "필터 없음" 으로 넘기면 요청한 갈래가 아닌 온갖 장소가 온 이유를 화면이 알 수 없다.
	 */
	private PurposeSpec resolveFacetKey(String facetKey) {
		InterestTagCode code = InterestTagCode.from(facetKey)
				.orElseThrow(() -> new PlaceRequestException("UNKNOWN_FACET_KEY",
						"지원하지 않는 갈래입니다.", List.of("facetKey")));
		return new PurposeSpec(null, InterestTagCode.FEATURE_TYPE, code.name());
	}

	private void validateRadius(int radiusMeters) {
		// 반경이 커지면 경계상자가 넓어져 표를 통째로 훑는 질의가 된다. 20km 는 도시 하나를
		// 덮는 크기라 "근처" 라는 말이 유지되는 상한이다.
		if (radiusMeters < 100 || radiusMeters > 20000) {
			throw new PlaceRequestException("INVALID_REQUEST", "반경은 100m에서 20000m 사이여야 합니다.",
					List.of("radiusMeters"));
		}
	}

	private List<Integer> sortedLadder() {
		List<Integer> ladder = this.properties.getNearbyRadiusLadderMeters();
		if (ladder == null || ladder.isEmpty()) {
			return DEFAULT_RADIUS_LADDER_METERS;
		}
		return ladder.stream().sorted().toList();
	}

	/** 값이 있는데 설정에 없으면 {@code UNKNOWN_PURPOSE} 다. 비어 있으면 애초에 안 불린다. */
	private PurposeSpec resolvePurpose(String purpose) {
		if (purpose == null || purpose.isBlank()) {
			throw new PlaceRequestException("UNKNOWN_PURPOSE", "지원하지 않는 목적입니다.", List.of("purpose"));
		}
		// 대문자로 맞춰 찾는다 — 설정의 purposes 키를 대문자로 적는 것이 이 서비스와의 계약이다.
		PurposeSpec spec = this.properties.getPurposes().get(purpose.strip().toUpperCase(Locale.ROOT));
		if (spec == null) {
			throw new PlaceRequestException("UNKNOWN_PURPOSE", "지원하지 않는 목적입니다.", List.of("purpose"));
		}
		return spec;
	}

	private void validateCoordinates(double lat, double lng) {
		if (lat < -90.0 || lat > 90.0) {
			throw new PlaceRequestException("INVALID_REQUEST", "위도는 -90에서 90 사이여야 합니다.", List.of("lat"));
		}
		if (lng < -180.0 || lng > 180.0) {
			throw new PlaceRequestException("INVALID_REQUEST", "경도는 -180에서 180 사이여야 합니다.", List.of("lng"));
		}
	}

	private void validateLimit(int limit) {
		if (limit < 1 || limit > 50) {
			throw new PlaceRequestException("INVALID_REQUEST", "limit은 1에서 50 사이여야 합니다.", List.of("limit"));
		}
	}

	private record ScoredPlace(Place place, double distanceM) {
	}

	private record StepResult(List<ScoredPlace> matches, boolean truncated) {
	}
}
