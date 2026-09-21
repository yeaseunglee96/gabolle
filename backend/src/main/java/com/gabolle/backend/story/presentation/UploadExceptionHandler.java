package com.gabolle.backend.story.presentation;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * {@link UploadController} 전용 오류 번역기. 범위를 이 컨트롤러 하나로 좁혀 다른 도메인의 예외를
 * 가로채지 않게 한다.
 */
@RestControllerAdvice(assignableTypes = UploadController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(UploadExceptionHandler.class);

	@ExceptionHandler(ImageUploadService.UnsupportedImageException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnsupported(ImageUploadService.UnsupportedImageException e) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(ApiResponse.failure(
						new ApiError("IMAGE_UNSUPPORTED_TYPE", "JPEG·PNG·WebP 만 올릴 수 있습니다.", List.of()),
						requestId()));
	}

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

	/**
	 * 저장 실패는 반드시 로그에 남긴다 — {@code @ExceptionHandler} 가 잡으면 스프링은 아무것도 찍지 않아
	 * 운영에서 503 이 나가도 로그가 비어 있다. 사용자에게 나가는 문구에는 안쪽 사정을 넣지 않는다.
	 */
	@ExceptionHandler(StoragePort.StorageException.class)
	public ResponseEntity<ApiResponse<Void>> handleStorageUnavailable(StoragePort.StorageException e) {
		log.error("사진 저장소 호출이 실패했다 — 사용자에게는 503 으로 나간다. 원인: {}", causeChain(e), e);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(ApiResponse.failure(
						new ApiError("STORAGE_UNAVAILABLE", "사진 저장소에 연결할 수 없습니다. 잠시 뒤 다시 시도해 주세요.", List.of()),
						requestId()));
	}

	/**
	 * 원인 사슬을 한 줄로 — 저장소 오류는 「못 붙었다」·「권한이 없다」·「버킷이 없다」가 전부 다른 일인데
	 * 스택만으로는 맨 아래까지 읽어야 안다.
	 */
	private static String causeChain(Throwable failure) {
		StringBuilder chain = new StringBuilder();
		for (Throwable current = failure; current != null && chain.length() < 500; current = current.getCause()) {
			if (!chain.isEmpty()) {
				chain.append(" ← ");
			}
			chain.append(current.getClass().getSimpleName());
			if (current.getMessage() != null) {
				chain.append('(').append(current.getMessage()).append(')');
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return chain.toString();
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
