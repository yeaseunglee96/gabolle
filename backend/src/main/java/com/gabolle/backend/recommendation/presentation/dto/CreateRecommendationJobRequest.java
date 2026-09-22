package com.gabolle.backend.recommendation.presentation.dto;

/**
 * 추천 Job 생성 요청 본문. 취향·제약은 여행 생성 시점에 굳힌 스냅샷으로만 들어가고 요청마다
 * 덮어쓸 수 없다.
 *
 * @param preferenceSnapshotVersion 어느 판의 취향으로 계산할까. {@code null} 이면 최신 판
 * @param topK 응답에 담을 최대 개수. {@code null} 이면 서버 기본값
 */
public record CreateRecommendationJobRequest(Integer preferenceSnapshotVersion, Integer topK) {
}
