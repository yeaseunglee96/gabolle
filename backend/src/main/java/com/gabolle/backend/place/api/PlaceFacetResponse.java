package com.gabolle.backend.place.api;

import java.time.OffsetDateTime;
import java.util.List;

import com.gabolle.backend.place.domain.MatchKind;

/**
 * 표식 기준 갈래 목록. 목록 자체는 {@code UserPlaceCodeMapRepository} 가 정본이고 이 응답은 그
 * 결과에 건수를 붙인 것뿐이다 — {@code PlaceFacetService} 참고.
 */
public record PlaceFacetResponse(List<FacetItem> facets, OffsetDateTime generatedAt) {

	/**
	 * @param userInputCode 취향 차원 코드. 예: {@code CATEGORY}.
	 * @param placeFeatureType 짝이 되는 장소 피처 종류. 예: {@code INTEREST_TAG}.
	 * @param placeCount 이 갈래의 정확한 DISTINCT 장소 수가 아니다. {@code featureKey} 가 없는
	 *     행의 건수와 키별 건수의 단순 합이라 같은 장소가 태그 둘을 가지면 두 번 세인다. 점수형
	 *     피처처럼 키가 아예 없는 경우에는 이 한계가 없다. 정확한 DISTINCT 장소 수가 필요해지면
	 *     질의를 하나 더 만들어야 한다.
	 * @param keys 태그형이면 태그별 건수, 점수형·참거짓형이면 빈 배열. {@code placeFeatureType} 이
	 *     {@code INTEREST_TAG} 인 항목은 {@link com.gabolle.backend.place.domain.InterestTagCode}
	 *     여덟 개가 데이터 유무와 상관없이 항상 순서대로 들어 있다 — 아코디언이 접힌 줄도 그려야
	 *     해서다. 자세한 이유는 {@code PlaceFacetService} 참고.
	 */
	public record FacetItem(String userInputCode, String placeFeatureType, MatchKind matchKind,
			long placeCount, List<FacetKeyCount> keys) {
	}

	/**
	 * @param labelKo 화면에 보여줄 한국어 이름. {@code INTEREST_TAG} 갈래만 채워진다
	 *     ({@link com.gabolle.backend.place.domain.InterestTagCode#labelKo()}) — 다른 표식 종류는
	 *     화면 쪽 이름표가 따로 있어 여기서 지어내지 않는다. {@code null} 이면 이 갈래에 이름이 없다는
	 *     뜻이지 값이 비었다는 뜻이 아니다.
	 */
	public record FacetKeyCount(String featureKey, long placeCount, String labelKo) {
	}
}
