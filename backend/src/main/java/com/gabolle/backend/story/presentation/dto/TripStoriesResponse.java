package com.gabolle.backend.story.presentation.dto;

import java.util.List;

/**
 * 한 여행에 달린 기록 묶음. 항목은 {@link StoryResponse} 그대로라 앱이 피드·상세와 같은 컴포넌트로 그린다.
 *
 * <p>이어 보기 칸이 없다 — 한 여행의 기록은 수가 정해져 있어, 안 쓰는 이어 보기를 미리 만들면 그 코드가
 * 검증되지 않은 채로 남는다.
 *
 * @param items 기록을 쓴 순서(오래된 것부터). 하나도 없으면 빈 목록이다 — 404 가 아니다
 */
public record TripStoriesResponse(List<StoryResponse> items) {
}
