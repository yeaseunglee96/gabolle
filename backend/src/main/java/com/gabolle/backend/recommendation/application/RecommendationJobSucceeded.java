package com.gabolle.backend.recommendation.application;

import java.util.UUID;

/**
 * 추천 작업이 일정을 만들며 성공했다 — 결과가 커밋된 뒤, 화면에 「끝났다」를 알린 뒤에 나간다
 * ({@link RecommendationJobWorker}). 추천 쪽은 이것을 누가 받는지 모른다.
 *
 * <p>받는 곳: 코스 2·3안을 미리 짜 두는 일정 쪽(S15P21E201-1604).
 */
public record RecommendationJobSucceeded(UUID requestId) {
}
