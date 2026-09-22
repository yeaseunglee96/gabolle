package com.gabolle.backend.recommendation.application;

import java.util.List;
import java.util.UUID;

/**
 * 실제로 반환된 추천 한 칸. {@code finalRank} 는 1 부터 시작한다.
 *
 * {@code finalScore} 를 화면에 내보낼지는 공개 API 계층이 정한다 — 응용 계층은 값을 들고만
 * 있는다.
 */
public record RecommendedPlace(
		UUID placeId,
		int finalRank,
		Double finalScore,
		List<String> reasonCodes,
		List<String> warningCodes,
		String category) {
}
