package com.gabolle.backend.story.presentation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.gabolle.backend.story.domain.Story;
import com.gabolle.backend.story.domain.StoryVisibility;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 기록 작성 본문 — {@code POST /api/v1/stories}. S15P21E201-207.
 *
 * <p>🔴 좌표 칸이 없다. 앱이 {@code lat}·{@code lng} 를 실어 보내도 여기 없는 칸은 Jackson 이 버린다
 * (알 수 없는 속성 무시) — 서버에 좌표가 닿는 자리가 아예 없다. 위치는 {@code region}(지역 문자열)
 * 하나다. 없으면 연결한 장소의 주소에서 앞 두 마디를 딴다.
 *
 * @param imageUrls 먼저 {@code POST /api/v1/uploads/story-image} 로 올려 받은 주소. 최대 3장. 올린 사람만 붙일 수 있다
 * @param publishAt 공개 시각. 없으면 여행 종료 다음 날 0시(여행 시간대), 여행도 없으면 지금
 * @param visibility 없으면 PUBLIC
 */
public record StoryCreateRequest(
		@NotBlank @Size(max = Story.MAX_BODY_LENGTH) String body,
		@Size(max = Story.MAX_IMAGES) List<String> imageUrls,
		UUID placeId,
		UUID tripId,
		@Size(max = 100) String region,
		StoryVisibility visibility,
		Instant publishAt) {

	public List<String> imageUrlsOrEmpty() {
		return imageUrls == null ? List.of() : imageUrls;
	}

	public StoryVisibility visibilityOrDefault() {
		return visibility == null ? StoryVisibility.PUBLIC : visibility;
	}
}
