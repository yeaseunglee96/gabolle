package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.place.api.PlaceFacetResponse;
import com.gabolle.backend.place.api.PlaceFacetResponse.FacetItem;
import com.gabolle.backend.place.api.PlaceFacetResponse.FacetKeyCount;
import com.gabolle.backend.place.domain.InterestTagCode;
import com.gabolle.backend.place.domain.MatchKind;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.domain.UserInputKind;
import com.gabolle.backend.place.domain.UserPlaceCodeMap;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.UserPlaceCodeMapRepository;

/**
 * 표식 기준 갈래 목록.
 *
 * <p>갈래 자체는 {@link UserPlaceCodeMapRepository} 가 유일한 정본이고 여기서 나열하지 않는다.
 * 그래야 마이그레이션이 취향 차원을 더할 때 이 클래스를 안 고쳐도 응답에 나타난다.
 *
 * <p>취향({@code PREFERENCE})만 읽는다. 대조표의 제약({@code CONSTRAINT})은 후보를 걸러내는
 * 하드 필터이지 사용자가 골라 둘러보는 목록이 아니다.
 *
 * <p>질의는 대조표 한 번, 건수 한 번으로 둘뿐이다. 갈래마다 세면 차원 수만큼 질의가 나간다.
 */
@Service
@Profile({"db", "dev"})
public class PlaceFacetService {

	/** 탐색 아코디언 항목의 userInputCode. 취향 차원이 아니라는 뜻이다. */
	private static final String EXPLORE_INPUT_CODE = "EXPLORE";

	private final UserPlaceCodeMapRepository codeMapRepository;

	private final PlaceFeatureRepository placeFeatureRepository;

	public PlaceFacetService(UserPlaceCodeMapRepository codeMapRepository,
			PlaceFeatureRepository placeFeatureRepository) {
		this.codeMapRepository = codeMapRepository;
		this.placeFeatureRepository = placeFeatureRepository;
	}

	public PlaceFacetResponse facets() {
		List<UserPlaceCodeMap> codeMaps =
				this.codeMapRepository.findByIdUserInputKindOrderByIdUserInputCodeAsc(UserInputKind.PREFERENCE);

		List<String> featureTypes = new ArrayList<>(codeMaps.stream()
				.map(UserPlaceCodeMap::getPlaceFeatureType)
				.distinct()
				.toList());
		// 탐색 아코디언은 대조표에서 파생하지 않고 자기 사전(InterestTagCode)에서 직접 만든다.
		if (!featureTypes.contains(InterestTagCode.FEATURE_TYPE)) {
			featureTypes.add(InterestTagCode.FEATURE_TYPE);
		}

		// 여기서 오는 행은 evidenceStatus <> UNKNOWN 까지만 걸러져 있다. "확인된 부재"
		// (VERIFIED + 값 false) 를 걸러내는 것은 indicatesPresence() 몫이다.
		Map<String, List<PlaceFeature>> featuresByType = groupByFeatureType(
				this.placeFeatureRepository.findByFeatureTypeIn(featureTypes));

		List<FacetItem> items = new ArrayList<>(codeMaps.stream()
				.map(codeMap -> toFacetItem(codeMap,
						featuresByType.getOrDefault(codeMap.getPlaceFeatureType(), List.of())))
				.toList());
		// 대조표에 INTEREST_TAG 를 가리키는 줄이 하나도 없을 때만 더한다 — 있으면 두 번 나간다.
		boolean mapHasExplore = codeMaps.stream()
				.anyMatch(codeMap -> InterestTagCode.FEATURE_TYPE.equals(codeMap.getPlaceFeatureType()));
		if (!mapHasExplore) {
			items.add(exploreFacetItem(
					featuresByType.getOrDefault(InterestTagCode.FEATURE_TYPE, List.of())));
		}

		return new PlaceFacetResponse(List.copyOf(items), OffsetDateTime.now(ZoneOffset.UTC));
	}

	private Map<String, List<PlaceFeature>> groupByFeatureType(List<PlaceFeature> rows) {
		Map<String, List<PlaceFeature>> grouped = new LinkedHashMap<>();
		for (PlaceFeature feature : rows) {
			grouped.computeIfAbsent(feature.getFeatureType(), key -> new ArrayList<>()).add(feature);
		}
		return grouped;
	}

	/**
	 * placeCount 는 "featureKey 가 없는 행의 건수 + 키별 건수의 합" 이다 — 점수형처럼
	 * {@code featureKey} 가 언제나 {@code null} 인 갈래는 앞 절반이 전부라, 빠뜨리면 자료가 몇
	 * 행이든 합계가 0 이 된다. 건수를 {@code placeId} 로 distinct 하는 것은 DB 쪽
	 * {@code COUNT(DISTINCT placeId)} 와 뜻을 맞추기 위해서다.
	 *
	 * <p>{@link PlaceFeature#indicatesPresence()} 가 거짓인 행은 건너뛴다. 이 필터가 없으면
	 * "휠체어 접근이 안 되는 것으로 확인된" 장소가 접근성 갈래 건수에 들어간다.
	 *
	 * <p>keys 규칙은 갈래마다 다르다 — {@link #interestTagKeys} 는 여덟 개를 항상 채우고
	 * {@link #plainKeys} 는 자료가 있는 키만 준다.
	 */
	private FacetItem toFacetItem(UserPlaceCodeMap codeMap, List<PlaceFeature> features) {
		Map<String, Set<UUID>> placeIdsByKey = placeIdsByKey(features);

		List<FacetKeyCount> keys = InterestTagCode.FEATURE_TYPE.equals(codeMap.getPlaceFeatureType())
				? interestTagKeys(placeIdsByKey)
				: plainKeys(placeIdsByKey);
		long total = keys.stream().mapToLong(FacetKeyCount::placeCount).sum() + keylessCount(placeIdsByKey);

		return new FacetItem(codeMap.getUserInputCode(), codeMap.getPlaceFeatureType(), codeMap.getMatchKind(),
				total, keys);
	}

	/** 확인된 부재({@code indicatesPresence} 가 거짓)는 여기서 빠진다. */
	private Map<String, Set<UUID>> placeIdsByKey(List<PlaceFeature> features) {
		Map<String, Set<UUID>> placeIdsByKey = new LinkedHashMap<>();
		for (PlaceFeature feature : features) {
			if (!feature.indicatesPresence()) {
				continue;
			}
			placeIdsByKey.computeIfAbsent(feature.getFeatureKey(), key -> new LinkedHashSet<>())
					.add(feature.getPlaceId());
		}
		return placeIdsByKey;
	}

	/**
	 * 탐색 아코디언 갈래. 취향 차원이 아니라 대조표에 짝이 없고 userInputCode 로
	 * {@code EXPLORE} 를 쓴다 — 취향 차원 하나를 빌려 쓰면 그 차원과 이 화면이 다시 엮인다.
	 * 앱은 이 값을 안 보고 {@code keys} 만 쓴다.
	 */
	private FacetItem exploreFacetItem(List<PlaceFeature> features) {
		Map<String, Set<UUID>> placeIdsByKey = placeIdsByKey(features);
		List<FacetKeyCount> keys = interestTagKeys(placeIdsByKey);
		long total = keys.stream().mapToLong(FacetKeyCount::placeCount).sum() + keylessCount(placeIdsByKey);
		return new FacetItem(EXPLORE_INPUT_CODE, InterestTagCode.FEATURE_TYPE, MatchKind.TAG_OVERLAP,
				total, keys);
	}

	/**
	 * {@link InterestTagCode#displayOrder()} 순으로 여덟 개를 먼저 채우고(자료가 없으면 0건),
	 * 그 밖의 값이 이미 적재돼 있으면 뒤에 붙인다 — 안 붙이면 쌓인 자료가 조회에서 조용히
	 * 사라진다.
	 */
	private List<FacetKeyCount> interestTagKeys(Map<String, Set<UUID>> placeIdsByKey) {
		List<FacetKeyCount> keys = new ArrayList<>();
		Set<String> knownKeys = new LinkedHashSet<>();
		for (InterestTagCode tag : InterestTagCode.displayOrder()) {
			knownKeys.add(tag.name());
			long count = placeIdsByKey.getOrDefault(tag.name(), Set.of()).size();
			keys.add(new FacetKeyCount(tag.name(), count, tag.labelKo()));
		}

		List<FacetKeyCount> extras = new ArrayList<>();
		for (Map.Entry<String, Set<UUID>> entry : placeIdsByKey.entrySet()) {
			if (entry.getKey() == null || knownKeys.contains(entry.getKey())) {
				continue;
			}
			extras.add(new FacetKeyCount(entry.getKey(), entry.getValue().size(), null));
		}
		extras.sort(Comparator.comparing(FacetKeyCount::featureKey));
		keys.addAll(extras);
		return keys;
	}

	/**
	 * {@code featureKey} 가 없는 묶음의 크기. 점수형·참거짓형은 값 자체가 답이라 이 열쇠가 늘
	 * {@code null} 이고, {@link #plainKeys} 가 그 묶음을 버린다.
	 *
	 * <p>이 값을 {@code keys} 에 넣지 않고 합계에만 더하는 것이 의도다. keys 는 화면이 하위
	 * 갈래로 그리는 목록이라 이름 없는 칸이 섞이면 안 된다.
	 */
	private static long keylessCount(Map<String, Set<UUID>> placeIdsByKey) {
		return placeIdsByKey.getOrDefault(null, Set.of()).size();
	}

	/** 그 밖의 갈래는 자료가 있는 키만, 이름 순으로 돌려준다. */
	private List<FacetKeyCount> plainKeys(Map<String, Set<UUID>> placeIdsByKey) {
		List<FacetKeyCount> keys = new ArrayList<>();
		for (Map.Entry<String, Set<UUID>> entry : placeIdsByKey.entrySet()) {
			if (entry.getKey() != null) {
				keys.add(new FacetKeyCount(entry.getKey(), entry.getValue().size(), null));
			}
		}
		keys.sort(Comparator.comparing(FacetKeyCount::featureKey));
		return keys;
	}
}
