package com.gabolle.backend.story.presentation;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.story.application.VideoUploadService;
import com.gabolle.backend.story.domain.UploadedVideo;
import com.gabolle.backend.story.presentation.dto.VideoUploadResponse;

/**
 * 동영상 업로드. 사진 창구({@link UploadController})와 나눠 둔다 — 그쪽은 3MB · JPEG/PNG/WebP
 * 라는 계약이고 앱·시험·문서가 그것을 믿는다. 형식 판별기도 갈라 둬서({@code ImageSniffer} ·
 * {@code VideoSniffer}) 한쪽에 더한 종류가 다른 쪽에 열리지 않게 한다.
 *
 * <p>내려주는 자리는 일부러 두지 않았다. 동영상 재생은 nginx→MinIO 로 바로 가야 한다 —
 * 스프링을 지나면 파일이 통째로 힙에 올라가고 구간 요청(Range)을 지원하지 않아 되감기·
 * 건너뛰기가 안 된다.
 */
@RestController
@Profile({ "db", "dev" })
@RequestMapping("/api/v1/uploads")
public class VideoUploadController {

	private final VideoUploadService videoUploadService;

	public VideoUploadController(VideoUploadService videoUploadService) {
		this.videoUploadService = videoUploadService;
	}

	/**
	 * @param durationSec 앱이 잰 길이(초). 서버는 동영상 파일을 열지 않으므로 확인하지 않는다.
	 * 안 보내도 된다
	 */
	@PostMapping(value = "/story-video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<VideoUploadResponse>> upload(@RequestPart("file") MultipartFile file,
			@RequestParam(value = "durationSec", required = false) Integer durationSec,
			Authentication authentication) throws IOException {
		UUID uploaderUserId = AuthenticatedUsers.requireId(authentication);

		// getBytes() 를 쓰지 않는다 — 파일이 통째로 힙에 올라가 동시 업로드 몇 건에 서버가 죽는다.
		// getSize() 는 스프링이 받아서 실제로 센 값이고 클라이언트가 주장한 값이 아니다.
		try (InputStream in = file.getInputStream()) {
			UploadedVideo uploaded = this.videoUploadService.upload(uploaderUserId, in, file.getSize(), durationSec);

			VideoUploadResponse response = new VideoUploadResponse(
					uploaded.getUploadedVideoId(),
					uploaded.getVideoUrl(),
					uploaded.getContentType(),
					uploaded.getByteSize(),
					uploaded.getDurationSec());
			return ResponseEntity.status(HttpStatus.CREATED)
					.body(ApiResponse.success(response, "req_" + UUID.randomUUID()));
		}
	}
}
