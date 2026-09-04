package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.place.api.PlaceFacetResponse;
import com.gabolle.backend.place.api.PlaceFacetResponse.FacetItem;
import com.gabolle.backend.place.api.PlaceFacetResponse.FacetKeyCount;
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

		Map<String, List<Object[]>> countsByFeatureType = groupByFeatureType(
				this.placeFeatureRepository.countPlacesByFeature(featureTypes));

		List<FacetItem> items = codeMaps.stream()
				.map(codeMap -> toFacetItem(codeMap,
						countsByFeatureType.getOrDefault(codeMap.getPlaceFeatureType(), List.of())))
				.toList();

		return new PlaceFacetResponse(items, OffsetDateTime.now(ZoneOffset.UTC));
	}

	private Map<String, List<Object[]>> groupByFeatureType(List<Object[]> rows) {
		Map<String, List<Object[]>> grouped = new LinkedHashMap<>();
		for (Object[] row : rows) {
			String featureType = (String) row[0];
			grouped.computeIfAbsent(featureType, key -> new ArrayList<>()).add(row);
		}
		return grouped;
	}

	/**
	 * placeCount 계산 근거는 {@link PlaceFacetResponse.FacetItem} javadoc 에 적었다 — 여기서는
	 * "featureKey 가 null 인 행의 건수 + 키별 건수의 합" 을 그대로 코드로 옮긴다.
	 */
	private FacetItem toFacetItem(UserPlaceCodeMap codeMap, List<Object[]> rows) {
		List<FacetKeyCount> keys = new ArrayList<>();
		long total = 0;
		for (Object[] row : rows) {
			String featureKey = (String) row[1];
			long count = (Long) row[2];
			total += count;
			if (featureKey != null) {
				keys.add(new FacetKeyCount(featureKey, count));
			}
		}
		return new FacetItem(codeMap.getUserInputCode(), codeMap.getPlaceFeatureType(), codeMap.getMatchKind(),
				total, keys);
	}
}
