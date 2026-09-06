package com.gabolle.backend.story.presentation.dto;

import java.util.UUID;

/** {@code POST /api/v1/uploads/story-image} 의 성공 응답 본문. */
public record UploadResponse(UUID imageId, String imageUrl, String contentType, int byteSize) {
}
