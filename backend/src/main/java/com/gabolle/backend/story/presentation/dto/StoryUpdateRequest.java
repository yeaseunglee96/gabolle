package com.gabolle.backend.story.presentation.dto;

import java.time.Instant;
import java.util.UUID;

import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryVisibility;

import jakarta.validation.constraints.Size;

/**
 * 기록 수정 본문 — {@code PATCH /api/v1/stories/{id}}.
 *
 * <p>없는 칸({@code null})은 「바꾸지 않는다」다. 장소 연결을 끊으려면 {@code clearPlace=true} 를 보낸다.
 * 사진은 이 요청으로 바꾸지 않는다.
 */
public record StoryUpdateRequest(
		@Size(max = Story.MAX_BODY_LENGTH) String body,
		@Size(max = 100) String region,
		StoryVisibility visibility,
		Instant publishAt,
		UUID placeId,
		Boolean clearPlace) {

	public boolean clearPlaceOrFalse() {
		return clearPlace != null && clearPlace;
	}
}
