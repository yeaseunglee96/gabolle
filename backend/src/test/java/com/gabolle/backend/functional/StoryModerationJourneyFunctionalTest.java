package com.gabolle.backend.functional;

import static org.assertj.core.api.Assertions.assertThat;

import com.gabolle.backend.auth.api.AuthUserResponse;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.functional.support.AuthedClient;
import com.gabolle.backend.functional.support.FunctionalJourneyTest;
import com.gabolle.backend.moderation.ModerationFixture;
import com.gabolle.backend.moderation.presentation.dto.ModerationActionResponse;
import com.gabolle.backend.moderation.presentation.dto.ModerationQueueResponse;
import com.gabolle.backend.moderation.presentation.dto.StoryReportRequest;
import com.gabolle.backend.story.presentation.dto.StoryCreateRequest;
import com.gabolle.backend.story.presentation.dto.StoryFeedResponse;
import com.gabolle.backend.story.presentation.dto.StoryResponse;
import com.gabolle.backend.story.presentation.dto.UploadResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 사진·스토리·피드·신고·모더레이션 여정 — S15P21E201-782.
 *
 * <p>사진 업로드(ImageUploadService) → 스토리 작성(StoryService) → 피드 노출(StoryFeedService) →
 * 신고(StoryReportService) → 운영자 처리(ModerationQueueService)까지 다섯 도메인 클래스가 순서대로
 * 엮이는데, 이 전체를 실제 HTTP로 잇는 테스트가 이전엔 없었다.
 *
 * <p>🔴 실측(2026-09-10) — 티켓이 말한 "피드 노출"은 {@link com.gabolle.backend.feed.presentation.FeedController}
 * (S15P21E201-632)가 아니라 {@code StoryController.feed()}({@link com.gabolle.backend.story.application.StoryFeedService}가
 * 뒤에 있다)로 확인한다. {@code FeedController}는 클래스 자체 javadoc이 밝히듯 아직 채우는 배치가 없어
 * <b>모든 사용자에게 {@code NOT_BUILT_YET}만 답하는 자리표시자</b>다 — 지금 이 시점엔 그 경로로 노출을
 * 확인할 방법이 없다. {@code GET /api/v1/stories}는 실제로 도는 경로라 여기로 노출·비노출을 잰다.
 *
 * <p>운영자 승격은 가입 경로로 못 만드는 값이라({@link ModerationFixture} javadoc) 로그인 뒤 DB에서
 * 직접 올린다 — {@code HmacJwtAuthenticationFilter}가 role을 JWT 클레임이 아니라 매 요청 DB에서
 * 다시 읽으므로(S15P21E201-686), 이미 로그인해 둔 토큰 그대로 다음 요청부터 ADMIN 권한이 먹는다.
 */
class StoryModerationJourneyFunctionalTest extends FunctionalJourneyTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("업로드→작성→피드 노출→신고→모더레이션 처리(삭제)까지 실제 HTTP로 이어지고, 처리 후 피드에서 사라진다")
	void storyModerationJourneyEndToEnd() throws IOException {
		AuthedClient author = loginAsNewUser("story-author");

		// 1) 사진 업로드 — 인증 필요(S15P21E201-216).
		MultiValueMap<String, Object> uploadParts = new LinkedMultiValueMap<>();
		org.springframework.http.HttpHeaders fileHeaders = new org.springframework.http.HttpHeaders();
		fileHeaders.setContentType(MediaType.IMAGE_JPEG);
		uploadParts.add("file", new HttpEntity<>(new NamedByteArrayResource(renderJpeg(), "photo.jpg"), fileHeaders));

		ResponseEntity<ApiResponse<UploadResponse>> uploadResponse = author.postMultipart(
				"/api/v1/uploads/story-image", uploadParts,
				new ParameterizedTypeReference<ApiResponse<UploadResponse>>() {
				});
		assertThat(uploadResponse.getStatusCode()).as("응답 본문: %s", uploadResponse.getBody())
				.isEqualTo(HttpStatus.CREATED);
		String imageUrl = uploadResponse.getBody().data().imageUrl();
		assertThat(imageUrl).isNotBlank();

		// 2) 스토리 작성 — 방금 올린 사진 주소를 그대로 싣는다. visibility는 안 보내 PUBLIC 기본값을 쓴다.
		StoryCreateRequest storyRequest = new StoryCreateRequest("부산 여행 첫째 날 기록", List.of(imageUrl), null, null,
				"부산 남구", null, null);
		ResponseEntity<ApiResponse<StoryResponse>> storyResponse = author.post("/api/v1/stories", storyRequest,
				new ParameterizedTypeReference<ApiResponse<StoryResponse>>() {
				});
		assertThat(storyResponse.getStatusCode()).as("응답 본문: %s", storyResponse.getBody())
				.isEqualTo(HttpStatus.CREATED);
		String storyId = storyResponse.getBody().data().id();
		assertThat(storyId).isNotBlank();

		// 3) 피드 노출 — 다른 사람(viewer)의 전체 피드(ALL = 공개 기록 전부 + 내 기록)에 보인다.
		AuthedClient viewer = loginAsNewUser("story-viewer");
		assertThat(storyIdsInAllFeed(viewer)).as("방금 쓴 기록이 피드에 안 보인다").contains(storyId);

		// 4) 신고 — 다른 사람(reporter)이 신고한다.
		AuthedClient reporter = loginAsNewUser("story-reporter");
		ResponseEntity<Void> reportResponse = reporter.post("/api/v1/stories/" + storyId + "/reports",
				new StoryReportRequest("OFFENSIVE", null), Void.class);
		assertThat(reportResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		// 5) 운영자 승격 + 검토 목록 조회 — 방금 신고한 기록이 큐에 있다.
		AuthedClient admin = loginAsNewUser("story-admin");
		ResponseEntity<ApiResponse<AuthUserResponse>> adminMe = admin.get("/api/v1/auth/me",
				new ParameterizedTypeReference<ApiResponse<AuthUserResponse>>() {
				});
		UUID adminUserId = adminMe.getBody().data().userId();
		ModerationFixture.promoteToAdmin(this.jdbcTemplate, adminUserId);

		ResponseEntity<ApiResponse<ModerationQueueResponse>> queueResponse = admin.get(
				"/api/v1/admin/story-reports", new ParameterizedTypeReference<ApiResponse<ModerationQueueResponse>>() {
				});
		assertThat(queueResponse.getStatusCode()).as("응답 본문: %s", queueResponse.getBody()).isEqualTo(HttpStatus.OK);
		assertThat(queueResponse.getBody().data().items()).extracting(item -> item.storyId().toString())
				.as("방금 신고한 기록이 검토 큐에 없다")
				.contains(storyId);

		// 6) 삭제로 처리 — 204/200 대신 실제 처리 결과(ApiResponse)를 받는 POST다.
		ResponseEntity<ApiResponse<ModerationActionResponse>> removeResponse = admin.post(
				"/api/v1/admin/story-reports/" + storyId + "/remove", null,
				new ParameterizedTypeReference<ApiResponse<ModerationActionResponse>>() {
				});
		assertThat(removeResponse.getStatusCode()).as("응답 본문: %s", removeResponse.getBody()).isEqualTo(HttpStatus.OK);

		// 7) 처리 후 피드에서 사라진다 — 이 티켓의 완료 기준.
		assertThat(storyIdsInAllFeed(viewer)).as("삭제 처리했는데 아직 피드에 남아 있다").doesNotContain(storyId);
	}

	private List<String> storyIdsInAllFeed(AuthedClient client) {
		ResponseEntity<ApiResponse<StoryFeedResponse>> feed = client.get("/api/v1/stories?scope=ALL",
				new ParameterizedTypeReference<ApiResponse<StoryFeedResponse>>() {
				});
		assertThat(feed.getStatusCode()).as("응답 본문: %s", feed.getBody()).isEqualTo(HttpStatus.OK);
		return feed.getBody().data().items().stream().map(StoryResponse::id).toList();
	}

	/** 서버가 실제 이미지 바이트인지 디코드해서 확인하므로, 확장자만 바꾼 텍스트로는 안 통한다. */
	private byte[] renderJpeg() throws IOException {
		BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		return out.toByteArray();
	}

	/** {@link org.springframework.core.io.ByteArrayResource}는 파일명이 없으면 멀티파트 경계가 비어 서버가 거부한다. */
	private static final class NamedByteArrayResource extends org.springframework.core.io.ByteArrayResource {

		private final String filename;

		NamedByteArrayResource(byte[] bytes, String filename) {
			super(bytes);
			this.filename = filename;
		}

		@Override
		public String getFilename() {
			return this.filename;
		}
	}
}
