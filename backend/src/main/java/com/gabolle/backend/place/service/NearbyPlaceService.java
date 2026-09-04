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
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;

/**
 * 근처 장소 거리순 조회 (S15P21E201-469).
 *
 * <p>🔴 API 응답 DTO({@link NearbyPlaceItem}·{@link NearbyPlaceResponse})를 이 서비스가 직접 만든다.
 * 보통은 서비스가 내부 결과 타입을 반환하고 controller 가 API DTO 로 옮기지만, 이 티켓은 만들 파일
 * 목록이 고정돼 있어 그 중간 타입을 더 둘 자리가 없다. 응답 모양이 단순해서 지금은 손해가 크지
 * 않지만, 나중에 이 서비스를 다른 API 가 재사용하게 되면 그때 내부 타입을 분리한다.
 *
 * <h2>🔴 정렬은 거리만으로 한다</h2>
 *
 * 완료 기준이 "정렬에 거리 외의 값이 개입하지 않는다" 다. 인기 점수나 평점을 비교자에 넣는 순간
 * 그 기준이 깨지고, 그 실수는 "결과가 가끔 이상하게 섞인다" 는 애매한 증상으로만 드러나 나중에
 * 잡기 어렵다. 그래서 {@link #findNearby} 의 비교자는 {@code distanceM} 하나뿐이고, 거리가 같을
 * 때만 {@code placeId} 로 갈라 결과를 재현 가능하게 한다. 인기순 같은 다른 정렬이 필요해지면 이
 * 서비스를 고치지 말고 새 API 를 만든다 — 그래야 "근처순은 항상 거리순" 이라는 계약이 유지된다.
 *
 * <h2>반경 사다리 — 조용히 넓히지 않는다</h2>
 *
 * 경계상자로 후보를 좁힌 뒤 자바에서 실제 거리를 재는 이유는 {@link GeoDistance} 를 보라. 첫
 * 반경에서 {@link PlaceProperties#getNearbyMinimumCount()} 를 못 채우면 사다리의 다음 반경으로
 * 넓히는데, 그 사실을 숨기면 사용자는 "근처" 라고 믿는 결과가 실제로는 수 km 밖일 수 있다. 그래서
 * 넓혔을 때 {@code radiusExpanded}·{@code effectiveRadiusM}·{@code expansionSteps} 를 응답에
 * 그대로 싣는다.
 *
 * <h2>🔴 purpose 판별을 설정으로 뺀 이유</h2>
 *
 * "기념품샵" 을 {@code place.category} 로 볼지 {@code place_feature} 표식으로 볼지 아직 팀이
 * 정하지 않았다. 여기서 자바 코드로 하나를 못박으면 나중에 다른 쪽으로 정해질 때 배포를 다시
 * 해야 한다. {@link PlaceProperties} 로 빼 두면 그때는 설정값만 바뀐다.
 *
 * <h2>🔴 purpose 가 선택값인 이유</h2>
 *
 * {@link PlaceProperties#getPurposes()} 의 기본값은 빈 맵이고 {@code application*.properties} 에도
 * 아직 아무 목적이 없다. {@code purpose} 를 필수로 두면 무엇을 보내도 항상 {@link PlaceRequestException}
 * ({@code UNKNOWN_PURPOSE})이 나서, 목적이 하나라도 설정되기 전까지는 이 엔드포인트가 어떤 입력으로도
 * 200 을 낼 수 없었다. 그래서 {@code purpose} 가 없으면 목적 필터를 아예 적용하지 않고 반경 안
 * 장소를 거리순으로 돌려준다 — {@link NearbyPlaceResponse#purposeApplied()} 로 그 사실을 알린다.
 * {@code purpose} 를 <b>보냈는데</b> 설정에 없으면 그때는 지금처럼 {@code UNKNOWN_PURPOSE} 로
 * 거부한다 — 오타를 "필터 없음" 으로 조용히 넘기지 않기 위해서다.
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

	public NearbyPlaceResponse findNearby(double lat, double lng, String purpose, int limit) {
		validateCoordinates(lat, lng);
		validateLimit(limit);
		boolean purposeApplied = purpose != null && !purpose.isBlank();
		// 🔴 purpose 가 없으면 spec 이 null 이고, 그 아래(scanRadius·fetchCandidates·
		// applyCategoryFilter)는 전부 null 을 "필터 없음" 으로 다룬다.
		PurposeSpec spec = purposeApplied ? resolvePurpose(purpose) : null;

		List<Integer> ladder = sortedLadder();
		int requestedRadiusM = ladder.get(0);
		int effectiveRadiusM = requestedRadiusM;
		int expansionSteps = 0;
		boolean scanTruncated = false;
		List<ScoredPlace> matches = List.of();

		for (int step = 0; step < ladder.size(); step++) {
			int radiusMeters = ladder.get(step);
			StepResult result = scanRadius(lat, lng, radiusMeters, spec);
			scanTruncated = scanTruncated || result.truncated();
			matches = result.matches();
			effectiveRadiusM = radiusMeters;
			expansionSteps = step;

			boolean lastStep = step == ladder.size() - 1;
			if (matches.size() >= this.properties.getNearbyMinimumCount() || lastStep) {
				break;
			}
		}

		List<NearbyPlaceItem> items = matches.stream()
				// 🔴 거리, 그리고 거리가 같을 때만 placeId. 다른 칼럼은 절대 여기 들어오면 안 된다 —
				// 완료 기준 "정렬에 거리 외의 값이 개입하지 않는다" 를 코드로 고정하는 자리.
				.sorted(Comparator.comparingDouble(ScoredPlace::distanceM)
						.thenComparing(scored -> scored.place().getPlaceId()))
				.limit(limit)
				.map(scored -> NearbyPlaceItem.from(scored.place(), Math.round(scored.distanceM())))
				.toList();

		return new NearbyPlaceResponse(items, requestedRadiusM, effectiveRadiusM,
				expansionSteps > 0, expansionSteps, scanTruncated, limit, purposeApplied);
	}

	private StepResult scanRadius(double lat, double lng, int radiusMeters, PurposeSpec spec) {
		GeoDistance.BoundingBox box = GeoDistance.boundingBox(lat, lng, radiusMeters);
		int scanLimit = this.properties.getNearbyMaxScanned();
		List<Place> candidates = fetchCandidates(box, spec, scanLimit);

		// 🔴 limit+1 로 받아서 "더 있었는가" 만 본다. 자른 나머지는 쿼리에 ORDER BY 가 없어
		// 순서가 정해져 있지 않으므로, 조용히 자르는 대신 사실을 scanTruncated 로 알린다 —
		// 그러지 않으면 반경 안의 진짜 최단거리 장소가 빠져도 아무도 모른다.
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
	 * {@code featureType} 이 있으면 표식으로 좁히고, 없으면 경계상자만으로 후보를 가져온다.
	 *
	 * <p>🔴 {@code spec} 이 {@code null} 이면 목적이 안 온 것이다(purpose 는 선택값 — 클래스
	 * javadoc "purpose 가 선택값인 이유" 참고) — 이때는 표식 필터를 걸 수 없으니 경계상자만으로
	 * 가져온다.
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
	 * 똑같이 적용된다 — "둘 다 있으면 표식으로 조회한 뒤 카테고리로 한 번 더 거른다" 는 요구를
	 * {@link #fetchCandidates} 와 이 메서드의 조합 하나로 만족시킨다.
	 *
	 * <p>{@code spec} 이 {@code null}(목적 없음)이어도 여기서 걸러지지 않는다 — 그대로 반환한다.
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

	private List<Integer> sortedLadder() {
		List<Integer> ladder = this.properties.getNearbyRadiusLadderMeters();
		if (ladder == null || ladder.isEmpty()) {
			return DEFAULT_RADIUS_LADDER_METERS;
		}
		return ladder.stream().sorted().toList();
	}

	/**
	 * 🔴 {@link #findNearby} 가 {@code purpose} 가 비어 있지 않을 때만 이 메서드를 부른다 — 비어
	 * 있으면 그 자체는 오류가 아니라 "필터 없음" 이다(클래스 javadoc "purpose 가 선택값인 이유").
	 * 그래도 방어적으로 null·공백을 다시 확인한다. 여기 도달했다는 것은 값은 있는데 설정에 없다는
	 * 뜻이라 {@code UNKNOWN_PURPOSE} 로 거부한다.
	 */
	private PurposeSpec resolvePurpose(String purpose) {
		if (purpose == null || purpose.isBlank()) {
			throw new PlaceRequestException("UNKNOWN_PURPOSE", "지원하지 않는 목적입니다.", List.of("purpose"));
		}
		// 🔴 대문자로 맞춰 찾는다. "SOUVENIR" 든 "souvenir" 든 같은 목적을 가리켜야 프런트나
		// 문서마다 대소문자가 갈려도 조용히 다른 결과(또는 다른 오류)가 나오지 않는다. 그래서
		// 설정의 purposes 키는 대문자로 적는 것이 이 서비스와의 계약이다.
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
