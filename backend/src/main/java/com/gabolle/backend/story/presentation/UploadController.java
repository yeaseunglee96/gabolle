package com.gabolle.backend.story.presentation;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.story.application.ImageUploadService;
import com.gabolle.backend.story.domain.UploadedImage;
import com.gabolle.backend.story.presentation.dto.UploadResponse;
import com.gabolle.backend.story.storage.StoragePort;

/**
 * 사진 업로드·서빙.
 *
 * <p>업로드는 로그인이 필요하다. 서빙({@code GET .../images/{*key}})은 사진이 화면에 바로 걸리는 자리라
 * 인증을 요구하지 않는다 — {@code SecurityConfig} 가 이 경로를 열어 둔다.
 */
@RestController
@Profile({ "db", "dev" })
@RequestMapping("/api/v1/uploads")
public class UploadController {

	private final ImageUploadService imageUploadService;

	private final StoragePort storagePort;

	public UploadController(ImageUploadService imageUploadService, StoragePort storagePort) {
		this.imageUploadService = imageUploadService;
		this.storagePort = storagePort;
	}

	@PostMapping(value = "/story-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<UploadResponse>> upload(@RequestPart("file") MultipartFile file,
			Authentication authentication) throws IOException {
		UUID uploaderUserId = AuthenticatedUsers.requireId(authentication);
		UploadedImage uploadedImage = this.imageUploadService.upload(uploaderUserId, file.getBytes());

		UploadResponse response = new UploadResponse(
				uploadedImage.getUploadedImageId(),
				uploadedImage.getImageUrl(),
				uploadedImage.getContentType(),
				uploadedImage.getByteSize());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(response, "req_" + UUID.randomUUID()));
	}

	@GetMapping("/images/{*key}")
	public ResponseEntity<?> getImage(@PathVariable String key) {
		// {*key} 는 앞의 "/" 를 포함해 나머지 경로를 통째로 캡처한다 — 저장 키에는 그 "/" 가 없다.
		String storageKey = key.startsWith("/") ? key.substring(1) : key;

		return this.storagePort.get(storageKey)
				.<ResponseEntity<?>>map(stored -> ResponseEntity.ok()
						.contentType(MediaType.parseMediaType(stored.contentType()))
						.header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
						.body(stored.bytes()))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(ApiResponse.failure(
								new ApiError("IMAGE_NOT_FOUND", "사진이 없습니다.", List.of()),
								"req_" + UUID.randomUUID())));
	}
}
