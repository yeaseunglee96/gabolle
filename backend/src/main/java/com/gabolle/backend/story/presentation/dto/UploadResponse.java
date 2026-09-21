package com.gabolle.backend.story.presentation.dto;

import java.util.UUID;

public record UploadResponse(UUID imageId, String imageUrl, String contentType, int byteSize) {
}
