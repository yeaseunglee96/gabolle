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
 * {@link VideoUploadController} 전용 오류 번역기 — S15P21E201-1275.
 *
 * <p>{@code UploadExceptionHandler}(사진)와 <b>같은 모양이되 범위가 다르다.</b>
 * {@code assignableTypes} 로 컨트롤러 하나에 묶여 있어서, 사진 쪽 문구가 동영상 오류에 나가거나
 * 그 반대가 되는 일이 없다 — <b>창구를 가른 이유가 여기서도 이어진다.</b>
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
	 * 🔴 상한을 <b>응답에 실어 보낸다.</b> 사진은 문구에 "3MB" 를 박아 뒀지만 동영상 상한은
	 * <b>설정값</b>이라 배포마다 다를 수 있다 — 문구에 박으면 설정을 바꾼 날 문구가 거짓이 된다.
	 *
	 * <p>서비스 자체 검사와 서블릿 컨테이너의 크기 제한이 같은 응답을 낸다. 다만 후자일 때는
	 * 상한을 알 수 없어({@code MaxUploadSizeExceededException} 은 우리 설정값을 모른다) 세부를
	 * 비운다 — 🔴 <b>모르는 값을 지어내지 않는다.</b>
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
	 * 🔴 <b>반드시 로그에 남긴다.</b> 사진 쪽이 2026-09-17 새벽에 저장소 실패를 로그에 한 줄도
	 * 안 남겨서 원인을 못 찾았다({@code UploadExceptionHandler} 주석 참고).
	 * {@code @ExceptionHandler} 가 잡으면 스프링은 아무것도 안 찍는다 — <b>같은 실수를 새 창구에서
	 * 반복하지 않는다.</b>
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
