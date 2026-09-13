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
 * 표식 기준 갈래 목록 (-473).
 *
 * <h2>🔴 갈래 목록을 자바에 두지 않는 이유</h2>
 *
 * 완료 기준이 "장소에 표식을 새로 붙이면 코드를 고치지 않아도 그 장소가 나온다" 다. 갈래
 * 자체를 여기서 나열하면 그 순간 정본이 둘이 된다. 그래서 {@link UserPlaceCodeMapRepository}
 * 가 유일한 정본이고, 이 서비스는 그 결과를 읽어 건수를 붙이는 것만 한다 — 마이그레이션이
 * 취향 차원을 하나 더 넣으면 이 클래스는 한 글자도 안 바뀐 채로 응답에 그 차원이 나타난다.
 *
 * <h2>🔴 취향(PREFERENCE)만 읽는 이유</h2>
 *
 * 대조표에는 제약(CONSTRAINT, 알레르기·식단·이동)도 있지만 그것은 후보를 걸러내는 하드
 * 필터이지 사용자가 "이 갈래로 둘러보고 싶다" 고 고르는 목록이 아니다. -473 이 보여 준 응답
 * 예시도 취향 쪽(CATEGORY → INTEREST_TAG)이다.
 *
 * <h2>🔴 질의가 둘뿐인 이유</h2>
 *
 * 갈래마다 건수를 따로 세면 취향 여덟 차원에 질의 여덟 번이 나가고, 아홉 번째 차원이 생기면
 * 아홉 번이 된다 — 완료 기준을 지키려고 만든 구조가 성능 함정을 새로 파는 셈이다. 그래서
 * 대조표를 한 번, 건수를 한 번, 합쳐서 두 번만 부른다.
 */
@Service
@Profile({"db", "dev"})
public class PlaceFacetService {

	/** 탐색 아코디언 항목의 userInputCode. 취향 차원이 아니라는 뜻이다 (S15P21E201-904). */
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
		// 🔴 탐색 아코디언은 대조표에서 파생하지 않는다 (S15P21E201-904). 전에는 취향
		//    CATEGORY 줄이 INTEREST_TAG 를 가리켜서 그 줄에 얹혀 나왔는데, 그 바람에 온보딩
		//    여섯 낱말과 탐색 여덟 낱말이 한 서랍에 섞였다. 이제 CATEGORY 는 CATEGORY_TAG 를
		//    가리키므로, 탐색 갈래는 자기 사전(InterestTagCode)에서 직접 만든다.
		if (!featureTypes.contains(InterestTagCode.FEATURE_TYPE)) {
			featureTypes.add(InterestTagCode.FEATURE_TYPE);
		}

		// 🔴 여기서 evidenceStatus <> UNKNOWN 까지만 걸러진 행을 받는다. "확인된 부재"
		// (VERIFIED + 값 false) 를 걸러내는 것은 toFacetItem 의 indicatesPresence() 몫이다 —
		// PlaceFeatureRepository.findByFeatureTypeIn javadoc 참고.
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
	 * placeCount 계산 근거는 {@link PlaceFacetResponse.FacetItem} javadoc 에 적었다 — "featureKey 가
	 * 없는 행의 건수 + 키별 건수의 합" 을 그대로 코드로 옮긴다. 건수를 셀 때 {@code placeId} 로
	 * distinct 하는 이유는 DB 쪽 {@code COUNT(DISTINCT placeId)} 와 같은 뜻을 자바에서 재현하기
	 * 위해서다.
	 *
	 * <p>🔴 여기서 {@link PlaceFeature#indicatesPresence()} 가 거짓인 행(확인된 부재, 그리고 이미
	 * 리포지토리 질의에서 빠진 UNKNOWN)은 건너뛴다. 이 필터가 없으면 "휠체어 접근이 안 되는 것으로
	 * 확인된" 장소가 접근성 갈래 건수에 들어간다.
	 *
	 * <p>keys 를 만드는 규칙은 갈래마다 갈린다({@link #interestTagKeys}, {@link #plainKeys}) —
	 * INTEREST_TAG(로컬 8갈래, -473)만 여덟 개를 항상 채워야 하고 다른 표식 종류는 지금처럼 데이터가
	 * 있는 키만 준다. total 은 그 keys 의 건수 합으로 다시 구한다 — INTEREST_TAG 는 0건짜리 키도
	 * 들어 있어 더해도 값이 그대로고, 다른 갈래는 원래 있던 키만 더해지므로 이전 합산과 같다.
	 */
	private FacetItem toFacetItem(UserPlaceCodeMap codeMap, List<PlaceFeature> features) {
		Map<String, Set<UUID>> placeIdsByKey = placeIdsByKey(features);

		List<FacetKeyCount> keys = InterestTagCode.FEATURE_TYPE.equals(codeMap.getPlaceFeatureType())
				? interestTagKeys(placeIdsByKey)
				: plainKeys(placeIdsByKey);
		long total = keys.stream().mapToLong(FacetKeyCount::placeCount).sum();

		return new FacetItem(codeMap.getUserInputCode(), codeMap.getPlaceFeatureType(), codeMap.getMatchKind(),
				total, keys);
	}

	/**
	 * 장소 표식을 키별로 모은다. 확인된 부재(indicatesPresence 가 거짓)는 여기서 빠진다.
	 */
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
	 * 탐색 아코디언 갈래 (S15P21E201-904).
	 *
	 * <p>🔴 이 항목은 <b>취향 차원이 아니다.</b> 그래서 대조표에 짝이 없고, userInputCode 로
	 * {@code EXPLORE} 를 쓴다 — 취향 여덟 차원 중 하나를 빌려 쓰면 그 차원과 이 화면이 다시
	 * 엮이고, 그게 이 티켓이 푼 문제였다. 앱은 이 값을 안 보고 {@code keys} 의 여덟 낱말만
	 * 골라 쓴다.
	 */
	private FacetItem exploreFacetItem(List<PlaceFeature> features) {
		List<FacetKeyCount> keys = interestTagKeys(placeIdsByKey(features));
		long total = keys.stream().mapToLong(FacetKeyCount::placeCount).sum();
		return new FacetItem(EXPLORE_INPUT_CODE, InterestTagCode.FEATURE_TYPE, MatchKind.TAG_OVERLAP,
				total, keys);
	}

	/**
	 * 로컬 8갈래(-473)의 keys. {@link InterestTagCode#displayOrder()} 순으로 여덟 개를 먼저 채우고
	 * (데이터가 없으면 0건), 온톨로지가 아직 확정되지 않아({@code InterestTagCode} 클래스 주석) 이
	 * 여덟 개 밖의 값이 이미 적재돼 있을 수 있으니 그런 값은 뒤에 그대로 붙인다 — 그러지 않으면
	 * 기존에 쌓인 데이터가 조회에서 조용히 사라진다.
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

	/** 그 밖의 갈래는 지금처럼 데이터가 있는 키만, 이름 순으로 돌려준다. */
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
