package com.gabolle.backend.story.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.story.application.ImageUploadService;
import com.gabolle.backend.story.domain.UploadedImage;
import com.gabolle.backend.story.storage.StoragePort;

/**
 * {@link UploadController} 전용 오류 번역기. {@code assignableTypes}·{@code @Order} 로 범위를
 * 이 컨트롤러 하나로 좁힌다 — {@code ItineraryExceptionHandler} 와 같은 이유(다른 도메인의
 * 예외를 가로채지 않기 위해서)다.
 */
@RestControllerAdvice(assignableTypes = UploadController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadExceptionHandler {

	@ExceptionHandler(ImageUploadService.UnsupportedImageException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnsupported(ImageUploadService.UnsupportedImageException e) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(ApiResponse.failure(
						new ApiError("IMAGE_UNSUPPORTED_TYPE", "JPEG·PNG·WebP 만 올릴 수 있습니다.", List.of()),
						requestId()));
	}

	/** 서비스 자체 검사({@code ImageTooLargeException})와 서블릿 컨테이너의 크기 제한 둘 다 같은 응답이다. */
	@ExceptionHandler({ ImageUploadService.ImageTooLargeException.class, MaxUploadSizeExceededException.class })
	public ResponseEntity<ApiResponse<Void>> handleTooLarge(Exception e) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(ApiResponse.failure(
						new ApiError("IMAGE_TOO_LARGE", "사진 한 장은 3MB 를 넘을 수 없습니다.",
								List.of("maxBytes=" + UploadedImage.MAX_BYTES)),
						requestId()));
	}

	@ExceptionHandler(ImageUploadService.EmptyImageException.class)
	public ResponseEntity<ApiResponse<Void>> handleEmpty(ImageUploadService.EmptyImageException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(new ApiError("IMAGE_EMPTY", e.getMessage(), List.of()), requestId()));
	}

	@ExceptionHandler(StoragePort.StorageException.class)
	public ResponseEntity<ApiResponse<Void>> handleStorageUnavailable(StoragePort.StorageException e) {
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(ApiResponse.failure(
						new ApiError("STORAGE_UNAVAILABLE", "사진 저장소에 연결할 수 없습니다. 잠시 뒤 다시 시도해 주세요.", List.of()),
						requestId()));
	}

	/** 저장 키 검증({@code LocalFileStorage})이 던진다 — 없는 키 조회의 경로 조작 시도 등. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidKey(IllegalArgumentException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(new ApiError("IMAGE_KEY_INVALID", e.getMessage(), List.of()), requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
