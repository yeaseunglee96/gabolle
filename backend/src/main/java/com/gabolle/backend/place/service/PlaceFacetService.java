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

		List<String> featureTypes = codeMaps.stream()
				.map(UserPlaceCodeMap::getPlaceFeatureType)
				.distinct()
				.toList();

		// 🔴 여기서 evidenceStatus <> UNKNOWN 까지만 걸러진 행을 받는다. "확인된 부재"
		// (VERIFIED + 값 false) 를 걸러내는 것은 toFacetItem 의 indicatesPresence() 몫이다 —
		// PlaceFeatureRepository.findByFeatureTypeIn javadoc 참고.
		Map<String, List<PlaceFeature>> featuresByType = groupByFeatureType(
				this.placeFeatureRepository.findByFeatureTypeIn(featureTypes));

		List<FacetItem> items = codeMaps.stream()
				.map(codeMap -> toFacetItem(codeMap,
						featuresByType.getOrDefault(codeMap.getPlaceFeatureType(), List.of())))
				.toList();

		return new PlaceFacetResponse(items, OffsetDateTime.now(ZoneOffset.UTC));
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
	 */
	private FacetItem toFacetItem(UserPlaceCodeMap codeMap, List<PlaceFeature> features) {
		Map<String, Set<UUID>> placeIdsByKey = new LinkedHashMap<>();
		for (PlaceFeature feature : features) {
			if (!feature.indicatesPresence()) {
				continue;
			}
			placeIdsByKey.computeIfAbsent(feature.getFeatureKey(), key -> new LinkedHashSet<>())
					.add(feature.getPlaceId());
		}

		List<FacetKeyCount> keys = new ArrayList<>();
		long total = 0;
		for (Map.Entry<String, Set<UUID>> entry : placeIdsByKey.entrySet()) {
			long count = entry.getValue().size();
			total += count;
			if (entry.getKey() != null) {
				keys.add(new FacetKeyCount(entry.getKey(), count));
			}
		}
		keys.sort(Comparator.comparing(FacetKeyCount::featureKey));
		return new FacetItem(codeMap.getUserInputCode(), codeMap.getPlaceFeatureType(), codeMap.getMatchKind(),
				total, keys);
	}
}
