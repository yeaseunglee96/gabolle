package com.gabolle.backend.place.api;

import java.util.List;
import java.util.UUID;

/**
 * 추천 후보 목록.
 *
 * @param belowMinimum 최소 개수를 못 채웠다. 조건을 자동으로 풀어 억지로 채우지 않았다는 뜻이다 —
 *        알레르기 조건을 슬쩍 풀어 만든 목록은 빈 목록보다 나쁘다
 * @param notApplied 요청은 받았지만 적용하지 못한 필터와 그 이유. 조용히 무시하면 호출자는
 *        걸러진 줄 알고 쓴다
 * @param scanTruncated 경계상자 후보가 상한을 넘어 잘렸다. 결과가 반경 안 전부가 아닐 수 있다
 * @param datasetVersions 후보들이 어느 수집분에서 왔는가. 추천 결과를 나중에 되짚을 때 쓴다
 */
public record PlaceCandidateResponse(
		List<Candidate> candidates,
		int generatedCount,
		int minimumRequired,
		boolean belowMinimum,
		List<String> appliedFilters,
		List<NotApplied> notApplied,
		boolean scanTruncated,
		List<String> datasetVersions) {

	public record Candidate(
			UUID placeId,
			String nameKo,
			String category,
			Double lat,
			Double lng,
			long distanceM,
			List<PlaceFeatureView> features) {
	}

	public record NotApplied(String filter, String reason) {
	}
}
