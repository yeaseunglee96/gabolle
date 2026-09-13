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
import com.gabolle.backend.place.domain.Place;
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
 * <p>required 가 여럿이어도 질의를 늘리지 않는 이유가 여기 있다. 첫 번째 것만 DB 에서 <b>좁히고</b>
 * 판정은 전부 자바에서 한다. DB 에서 전부 거르면 조건 수만큼 EXISTS 가 붙어 질의문이 조건에 따라
 * 달라지고, 그러면 "질의 두 번" 이라는 계약을 테스트로 지키기 어려워진다.
 *
 * <p>🔴 DB 필터를 판정으로 믿지 않는 데는 더 중요한 이유가 있다. JPQL 은 {@code evidence_status}
 * 까지만 볼 수 있어서 <b>확인된 부재</b>(값이 {@code false} 인 행)를 못 거른다. 그래서 DB 는 후보를
 * 줄이는 데만 쓰고 있고·없음의 판정은 {@code PlaceFeature.indicatesPresence()} 가 한다. 경계상자로
 * 좁히고 자바에서 실제 거리를 재는 것과 같은 구조다.
 *
 * <h2>🔴 못 거른 조건을 숨기지 않는다</h2>
 *
 * 영업시간은 이제 <b>일부 장소에만</b> 있다(S15P21E201-852 가 관광공사 자료에서 268곳을 넣었고
 * 상가정보 2,355곳에는 없다). 그래서 문 닫은 곳은 실제로 빼내면서도, 후보 하나라도 영업시간을
 * 모르는 채 남았으면 <b>이 조건을 적용했다고 말하지 않는다</b> — {@code notApplied} 에
 * {@code NOT_COLLECTED} 로 적는다. 절반만 걸러진 목록을 "영업 중인 곳" 이라고 부르면 호출자는
 * 그것을 믿고 쓴다.
 *
 * <p>🔴 여기서는 영업시간 문({@code OpeningHoursFilterPort})을 부르지 않는다. 후보가 수백인
 * 자리에서 장소마다 물으면 질의가 장소 수만큼 나가고, 그것이 이 클래스가 지키기로 한
 * 완료 기준("질의 개수가 장소 수에 비례하지 않는다")을 정면으로 깬다. 아래에서 이미 한 번에
 * 읽어 둔 피처를 {@link OpeningHoursValue} 로 직접 판정한다.
 *
 * <h2>🔴 자르기가 두 번 일어난다 — 둘 다 "거리" 가 아니다</h2>
 *
 * <ol>
 * <li><b>경계상자 상한</b>({@code gabolle.place.candidate-max-scanned}) — DB 에서 읽어 올 행 수.
 *     여기서 잘리면 {@code scanTruncated} 로 알린다. 잘리는 순서는 {@code place_id} 순이다
 *     (JPQL 에 삼각함수가 없어 거리로는 못 자른다)</li>
 * <li><b>{@code limit}</b> — 거리순으로 정렬한 뒤 앞에서부터 이만큼만 돌려준다</li>
 * </ol>
 *
 * <p>🔴 <b>{@code limit} 은 "가까운 순 N 곳" 이지 "좋은 순 N 곳" 이 아니다</b>
 * (S15P21E201-724). 이 서비스는 점수를 모르기 때문에 그럴 수밖에 없다. 그래서 <b>부르는 쪽이
 * 채점을 한다면 여기에 작은 {@code limit} 을 주면 안 된다</b> — 잘려 나간 장소는 아무리 좋아도
 * 점수를 매길 기회조차 없고, 그 사실은 어디에도 안 남는다. 추천 엔진이 실제로 그랬다
 * ({@code BaselineCandidateTranslator} 가 200 을 주고 있었다). 지금은 엔진이 반경 안 후보를
 * 전부 받아 <b>채점한 뒤에</b> 자른다.
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

	private static final Logger log = LoggerFactory.getLogger(PlaceCandidateQueryService.class);

	private final PlaceRepository placeRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	private final ObjectMapper objectMapper;

	/**
	 * 경계상자에서 읽어 올 최대 행 수({@code gabolle.place.candidate-max-scanned}).
	 *
	 * <p>🔴 상수 {@code MAX_SCANNED = 2_000} 이었다 (S15P21E201-724). 부산 반경 5km 안에는
	 * 음식점만 평균 9,422곳이 있어 그 상한이 <b>항상</b> 먼저 걸렸고, 그러면 자바가 거리를
	 * 재기 전에 이미 대부분이 사라진 뒤였다. 상수라서 운영에서 올릴 방법도 없었다.
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

		// ── 질의 1. 경계상자, 그리고 required 가 있으면 그중 첫 번째까지 DB 에서 건다.
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
		// 갈래가 비어 있는 장소는 요청이 갈래를 좁혔는지와 무관하게 언제나 뺀다 — S15P21E201-899.
		//
		// 적재가 갈래를 비우는 것은 "앱의 여섯 낱말 중 이것을 가리키는 것이 없다" 는 뜻이다
		// (TourApiCategory 가 레포츠·숙박을 그렇게 둔다). 그 뜻대로라면 어떤 취향으로도 안
		// 골라져야 하는데, 갈래 검사가 categories 가 빈 요청에서 통째로 건너뛰어져서 오히려
		// 전부 골라지고 있었다 — 취향을 건너뛴 사용자의 일정에 호텔과 레지던스가 관광지처럼
		// 들어갔다(운영 실측 2026-09-13, 반경 15km 후보 2,355곳 중 78곳).
		//
		// 숙소 지정과 필수 방문지 지정은 PlaceRepository.findByCategoryIn 을 따로 쓰므로
		// 여기서 빼도 그쪽은 그대로다.
		applied.add("NON_EMPTY_CATEGORY");

		Map<UUID, Long> distances = new HashMap<>();
		List<Place> withinRadius = new ArrayList<>();
		for (Place place : scanned) {
			String placeCategory = place.getCategory();
			if (placeCategory == null || placeCategory.isBlank()) {
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

		// ── 질의 2. 남은 후보의 피처를 한 번에. 🔴 장소마다 읽지 않는다.
		Map<UUID, List<PlaceFeature>> featuresByPlace = loadFeatures(withinRadius);

		// 🔴 required 를 DB 에서 한 번 걸렀어도 자바에서 전부 다시 본다. DB 필터는
		//    evidence_status <> UNKNOWN 까지만 볼 수 있어서, 확인된 부재(값이 false)를 못 거른다.
		//    좁히는 것은 DB, 판정은 자바 — 반경 계산과 같은 구조다.
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
					// 🔴 빼지 않는다. 모른다고 후보에서 지우면 영업시간을 아직 안 넣은
					//    2,355곳이 통째로 사라진다 — 조건을 건 사용자에게는 그것이 "그 시각에
					//    여는 곳이 없다" 로 보인다.
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

		// 🔴 걸렀다고 말할 수 있는 것과 못 하는 것을 가른다. 하나라도 모르는 채 남았으면
		//    이 조건은 "적용했다" 고 말하지 않는다.
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
	 * 이미 읽어 둔 피처에서 영업시간 하나를 찾아 판정한다.
	 *
	 * <p>행이 없으면 모른다다 — 이것이 지금 대부분의 장소에서 나오는 답이고, 그것이 정상
	 * 상태다({@code OpeningHoursLoader} 가 관광공사 268곳에만 넣었다).
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
	 * 요구한 표식을 전부 가졌는가.
	 *
	 * <p>🔴 여기서는 <b>확실히 있는 것만</b> 통과시킨다. {@code UNKNOWN} 도, 확인된 부재
	 * ({@code VERIFIED} + 값 {@code false})도 "있다" 가 아니다.
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
	 * 빼야 할 표식을 가졌는가.
	 *
	 * <p>🔴 요구 쪽과 판정 방향이 <b>다르다.</b> 여기는 알레르기 같은 안전 제약이 지나가는 길이라
	 * "모른다" 를 통과시키면 안 된다. 확인된 부재만 안전으로 보고, 그 밖에는 전부 뺀다.
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
	 * 🔴 값이 있는데 파싱에 실패한 행은 옮기지 않고 버린다 — S15P21E201-749.
	 *
	 * <p>{@code readValue} 는 파싱 실패와 "값이 원래 없음" 을 똑같이 {@code null} 로 돌려준다.
	 * 그 {@code null} 을 그대로 {@link PlaceFeatureView} 에 실으면, {@code BaselineCandidateScorer
	 * .bucketFor} 가 "이 행이 존재하고 값이 null" 과 "이 행 자체가 없음" 을 구분하지 못한 채
	 * 안전 판정(알레르기·식단·이동 접근성)을 내린다. 안전 제약은 항목마다 판정 방향이
	 * 반대라서({@code FeaturePresence} 참고), 어느 쪽이든 파싱 실패가 조용히 특정 결과로
	 * 굳으면 최소 한쪽 방향에서는 실제로 안전하지 않은 장소가 조건을 통과할 수 있다.
	 *
	 * <p>행을 아예 빼면 {@code bucketFor} 가 그 표식을 "행이 없음" 으로 읽어 {@code UNVERIFIED}
	 * 가 되고, 방향에 무관하게 안전한 기본값(모르는 것은 있는 것으로 취급)으로 떨어진다.
	 * {@code EditorialPickBaselineProvider.toViews}(S15P21E201-555, MR !297)가 같은 문제를
	 * 먼저 이 방식으로 고쳤다 — 여기도 같은 방향으로 맞춘다.
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
