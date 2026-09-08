package com.gabolle.backend.recommendation.presentation.dto;

/**
 * REC-01 요청 본문 — S15P21E201-192.
 *
 * <p>🔴 API 명세서 초안(v1.2)이 그렸던 {@code constraints[]}·{@code context} 는 여기 없다.
 * 그 둘을 실제로 받으려면 {@code RecommendationCommand} 가 그 값을 담을 자리부터 생겨야
 * 하는데 아직 없다 — 지금은 취향·제약이 여행 생성 시점에 이미 굳힌 스냅샷으로만 들어간다.
 * 요청마다 오버라이드하게 만드는 것은 이 티켓 범위가 아니다.
 *
 * @param preferenceSnapshotVersion 어느 판의 취향으로 계산할까. {@code null} 이면 최신 판
 * @param topK 응답에 담을 최대 개수. {@code null} 이면 서버 기본값
 */
public record CreateRecommendationJobRequest(Integer preferenceSnapshotVersion, Integer topK) {
}
