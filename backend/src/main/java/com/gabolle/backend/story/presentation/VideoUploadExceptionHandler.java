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
import com.gabolle.backend.story.application.VideoUploadService;
import com.gabolle.backend.story.storage.StoragePort;

/**
 * {@link VideoUploadController} 전용 오류 번역기. {@code assignableTypes} 로 컨트롤러 하나에
 * 묶여 있어서 사진 쪽 문구가 동영상 오류에 나가거나 그 반대가 되지 않는다.
 */
@RestControllerAdvice(assignableTypes = VideoUploadController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class VideoUploadExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(VideoUploadExceptionHandler.class);

	@ExceptionHandler(VideoUploadService.UnsupportedVideoException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnsupported(VideoUploadService.UnsupportedVideoException e) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(ApiResponse.failure(
						new ApiError("VIDEO_UNSUPPORTED_TYPE", "MP4 만 올릴 수 있습니다.", List.of()), requestId()));
	}

	/**
	 * 상한을 문구에 박지 않고 응답 세부에 실어 보낸다. 동영상 상한은 설정값이라 배포마다 다를
	 * 수 있다. 서블릿 컨테이너가 먼저 자르는 경우는 상한을 알 수 없어 세부를 비운다.
	 */
	@ExceptionHandler(VideoUploadService.VideoTooLargeException.class)
	public ResponseEntity<ApiResponse<Void>> handleTooLarge(VideoUploadService.VideoTooLargeException e) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(ApiResponse.failure(
						new ApiError("VIDEO_TOO_LARGE", "동영상 크기가 허용치를 넘었습니다.",
								List.of("maxBytes=" + e.maxBytes())),
						requestId()));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleContainerTooLarge(MaxUploadSizeExceededException e) {
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(ApiResponse.failure(
						new ApiError("VIDEO_TOO_LARGE", "동영상 크기가 허용치를 넘었습니다.", List.of()), requestId()));
	}

	@ExceptionHandler(VideoUploadService.EmptyVideoException.class)
	public ResponseEntity<ApiResponse<Void>> handleEmpty(VideoUploadService.EmptyVideoException e) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.failure(new ApiError("VIDEO_EMPTY", e.getMessage(), List.of()), requestId()));
	}

	/**
	 * 반드시 로그에 남긴다. {@code @ExceptionHandler} 가 잡으면 스프링은 아무것도 찍지 않는다.
	 */
	@ExceptionHandler(StoragePort.StorageException.class)
	public ResponseEntity<ApiResponse<Void>> handleStorageUnavailable(StoragePort.StorageException e) {
		log.error("동영상 저장소 호출이 실패했다 — 사용자에게는 503 으로 나간다. 원인: {}", causeChain(e), e);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
				.body(ApiResponse.failure(
						new ApiError("STORAGE_UNAVAILABLE", "동영상 저장소에 연결할 수 없습니다. 잠시 뒤 다시 시도해 주세요.",
								List.of()),
						requestId()));
	}

	/** 사진 쪽과 같은 이유로 한 줄 요약을 남긴다 — 스택만으로는 갈래를 맨 아래까지 읽어야 안다. */
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

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
