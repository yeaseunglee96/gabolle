package com.gabolle.backend.story.presentation.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code POST /api/v1/uploads/story-video} 의 성공 응답 본문. 파일이 올라갔다는 것까지만
 * 말하고, 기록에 붙이는 것은 다음 단계다.
 *
 * <p>{@code durationSec} 은 앱이 잰 값을 그대로 돌려주는 것이고 서버가 확인한 값이 아니다.
 * 못 받았으면 칸 자체가 빠진다.
 */
public record VideoUploadResponse(UUID videoId, String videoUrl, String contentType, long byteSize,
		@JsonInclude(JsonInclude.Include.NON_NULL) Integer durationSec) {
}
