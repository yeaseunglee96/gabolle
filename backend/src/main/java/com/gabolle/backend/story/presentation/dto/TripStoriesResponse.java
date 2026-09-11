package com.gabolle.backend.story.presentation.dto;

import java.util.List;

/**
 * 한 여행에 달린 기록 묶음 — {@code GET /api/v1/trips/{tripId}/stories} (S15P21E201-829).
 *
 * <p>항목은 {@link StoryResponse} 그대로다. 피드·상세와 같은 모양이라 앱이 기록을 그리는
 * 컴포넌트를 하나만 갖고 세 경로에 쓴다.
 *
 * <p>{@code nextCursor} 같은 이어 보기 칸이 없다. 한 여행의 기록은 사람이 올리는 것이라 수가
 * 정해져 있고, 안 쓰는 이어 보기를 미리 만들면 그 코드가 검증되지 않은 채로 남는다
 * ({@code TripQueryService.MAX_LIST_SIZE} 가 같은 이유로 그렇게 되어 있다).
 *
 * @param items 기록을 쓴 순서(오래된 것부터). 하나도 없으면 빈 목록이다 — 404 가 아니다
 */
public record TripStoriesResponse(List<StoryResponse> items) {
}
