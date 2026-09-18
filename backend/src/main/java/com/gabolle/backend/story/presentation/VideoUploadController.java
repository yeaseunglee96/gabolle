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
 * 동영상 업로드 — S15P21E201-1275.
 *
 * <h2>🔴 왜 사진 창구({@link UploadController})를 재사용하지 않나</h2>
 *
 * 사진 창구는 <b>3MB · JPEG/PNG/WebP</b> 라는 계약을 가지고 있고 앱·시험·문서가 전부 그것을
 * 믿는다. 거기에 MP4 를 얹으면 <b>그 계약이 조용히 넓어지고</b> {@code story-image} 라는 이름이
 * 거짓이 된다. 형식 판별기도 갈라 둔다({@code ImageSniffer} · {@code VideoSniffer}) — 목록이
 * 하나면 한쪽에 더한 종류가 <b>다른 쪽에도 열린다.</b>
 *
 * <h2>서빙하는 자리가 여기 없다</h2>
 *
 * 사진 창구에는 {@code GET /uploads/images/&#123;*key&#125;} 가 있지만 동영상에는 <b>일부러 두지 않았다.</b>
 * 운영은 {@code provider=s3} 라 공개 주소가 nginx→MinIO 로 바로 가고 스프링을 안 지난다. 동영상
 * 재생은 <b>반드시 그 길이어야 한다</b> — 스프링을 지나면 파일이 통째로 힙에 올라가고, 구간
 * 요청(Range)을 지원하지 않아 <b>되감기·건너뛰기가 안 된다.</b>
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
	 * @param durationSec 앱이 잰 길이(초). 🔴 <b>서버는 확인하지 않는다</b> — 동영상 파일을 열지
	 * 않기 때문이다. 화면이 재생 시간을 미리 보여주는 데 쓴다. 안 보내도 된다
	 */
	@PostMapping(value = "/story-video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<VideoUploadResponse>> upload(@RequestPart("file") MultipartFile file,
			@RequestParam(value = "durationSec", required = false) Integer durationSec,
			Authentication authentication) throws IOException {
		UUID uploaderUserId = AuthenticatedUsers.requireId(authentication);

		// 🔴 getBytes() 를 쓰지 않는다. 그러면 파일이 통째로 힙에 올라간다 — 사진 3MB 는 괜찮지만
		//    동영상은 한 건이 힙의 상당 부분을 먹고 동시 업로드 몇 건에 서버가 죽는다.
		//    getSize() 는 스프링이 받아서 실제로 센 값이다(클라이언트가 주장한 값이 아니다).
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
