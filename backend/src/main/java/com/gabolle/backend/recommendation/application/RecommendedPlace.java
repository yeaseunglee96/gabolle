package com.gabolle.backend.recommendation.application;

import java.util.List;
import java.util.UUID;

/**
 * 실제로 반환된 추천 한 칸.
 *
 * <p>🔴 {@code finalScore} 를 그대로 화면에 내보낼지는 <b>제품 결정</b>이다. 여기(응용 계층)
 * 에서는 값을 들고 있고, 공개 API 계층이 생길 때 노출 정책을 정한다 — 점수를 공개하면
 * 랭킹을 역산당할 수 있고, 감추면 "왜 이 순서인가" 를 설명할 수 없다.
 *
 * @param placeId 내부 정본 장소 ID
 * @param finalRank 1 부터 시작하는 최종 순위
 * @param finalScore 최종 점수
 * @param reasonCodes 추천 이유 코드
 * @param warningCodes 경고 코드
 */
public record RecommendedPlace(
		UUID placeId,
		int finalRank,
		Double finalScore,
		List<String> reasonCodes,
		List<String> warningCodes,
		String category) {
}
