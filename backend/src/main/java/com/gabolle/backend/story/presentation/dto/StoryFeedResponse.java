package com.gabolle.backend.story.presentation.dto;

import java.util.List;

/** 피드 한 묶음. {@code nextCursor} 가 {@code null} 이면 더 없다. 있으면 다음 요청의 {@code cursor} 로 그대로 보낸다. */
public record StoryFeedResponse(List<StoryResponse> items, String nextCursor) {
}
