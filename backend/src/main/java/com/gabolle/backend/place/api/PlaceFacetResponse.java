package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;
import java.util.List;

import com.gabolle.backend.place.domain.MatchKind;

/**
 * 표식 기준 갈래 목록 (-473). 목록 자체는 {@code UserPlaceCodeMapRepository} 가 정본이고
 * 이 응답은 그 결과에 건수를 붙인 것뿐이다 — {@code PlaceFacetService} 참고.
 */
public record PlaceFacetResponse(List<FacetItem> facets, OffsetDateTime generatedAt) {

	/**
	 * @param userInputCode 취향 차원 코드. 예: {@code CATEGORY}.
	 * @param placeFeatureType 짝이 되는 장소 피처 종류. 예: {@code INTEREST_TAG}.
	 * @param placeCount 🔴 이 갈래의 <b>정확한</b> DISTINCT 장소 수가 아니다. {@code featureKey}
	 *     가 없는 행의 건수와 키별 건수의 단순 합이다 — 같은 장소가 태그 둘을 가지면 두 번
	 *     세인다. 점수형 피처처럼 키가 아예 없는 경우에는 이 한계가 없다(행이 하나뿐이라
	 *     합할 것도 겹칠 것도 없다). 정확한 DISTINCT 장소 수가 필요해지면 질의를 하나 더
	 *     만들어야 한다.
	 * @param keys 태그형이면 태그별 건수, 점수형·참거짓형이면 빈 배열.
	 */
	public record FacetItem(String userInputCode, String placeFeatureType, MatchKind matchKind,
			long placeCount, List<FacetKeyCount> keys) {
	}

	public record FacetKeyCount(String featureKey, long placeCount) {
	}
}
