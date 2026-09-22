package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.presentation.UploadController;
import com.gabolle.backend.story.presentation.UploadExceptionHandler;
import com.gabolle.testslice.StorySliceApplication;

/**
 * 업로드 → 저장 → 서빙 전체 경로를 실제 PostgreSQL·실제 디스크 위에서 확인한다.
 * 사진 바이트가 표에 들어가지 않는다는 것은 information_schema 로 직접 단정한다. 응답만 보면
 * 서버가 사진을 통째로 넣어도 통과한다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ImageUploadIntegrationTest {

	@TempDir
	static Path storageRoot;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
		registry.add("gabolle.storage.root", () -> storageRoot.toString());
		registry.add("gabolle.storage.public-base-path", () -> "/api/v1/uploads/images");
	}

	@Autowired
	private UploadController uploadController;

	@Autowired
	private UploadExceptionHandler uploadExceptionHandler;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	private UUID userId;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.uploadController)
				.setControllerAdvice(this.uploadExceptionHandler)
				.build();

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.userId = UUID.randomUUID();
		this.jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'PERSONALIZED', 'ACTIVE', ?, ?)",
				this.userId, now, now);
	}

	private Authentication asUser() {
		return new UsernamePasswordAuthenticationToken(this.userId.toString(), null, List.of());
	}

	@Test
	@DisplayName("(a) EXIF 든 JPEG 업로드 → 201, 표에는 주소만, 파일에서 EXIF 가 빠지고 GET 으로 같은 바이트를 받는다")
	void uploadJpegStripsExifAndPersistsUrlOnly() throws Exception {
		byte[] jpeg = jpegWithFakeExif();

		String responseBody = mockMvc.perform(multipart("/api/v1/uploads/story-image")
						.file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg))
						.principal(asUser()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.imageUrl").value(
						matchesPattern("^/api/v1/uploads/images/story/\\d{4}/\\d{2}/.+\\.jpg$")))
				.andExpect(jsonPath("$.data.contentType").value("image/jpeg"))
				.andExpect(jsonPath("$.data.byteSize").isNumber())
				.andReturn().getResponse().getContentAsString();
		String imageUrl = extractImageUrl(responseBody);

		assertThat(uploadedImageCountForUser()).isEqualTo(1);
		assertThat(byteaColumnCount()).isEqualTo(0);

		String storageKey = this.jdbcTemplate.queryForObject(
				"SELECT storage_key FROM uploaded_image WHERE uploader_user_id = ?", String.class, this.userId);
		Path stored = storageRoot.resolve(storageKey);
		assertThat(stored).exists();
		byte[] storedBytes = Files.readAllBytes(stored);
		assertThat(containsMarker(storedBytes, (byte) 0xE1)).isFalse();

		mockMvc.perform(get(imageUrl))
				.andExpect(status().isOk())
				.andExpect(content().contentType("image/jpeg"))
				.andExpect(content().bytes(storedBytes));
	}

	@Test
	@DisplayName("(b) PNG 업로드도 201")
	void uploadPng() throws Exception {
		byte[] png = renderPng();

		mockMvc.perform(multipart("/api/v1/uploads/story-image")
						.file(new MockMultipartFile("file", "photo.png", "image/png", png))
						.principal(asUser()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.contentType").value("image/png"));
	}

	@Test
	@DisplayName("(c) 텍스트를 .jpg 로 위장해 올리면 415, 표에 행이 남지 않는다")
	void rejectsNonImageDisguisedAsJpg() throws Exception {
		mockMvc.perform(multipart("/api/v1/uploads/story-image")
						.file(new MockMultipartFile("file", "photo.jpg", "image/jpeg",
								"hello".getBytes(StandardCharsets.UTF_8)))
						.principal(asUser()))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.error.code").value("IMAGE_UNSUPPORTED_TYPE"));

		assertThat(uploadedImageCountForUser()).isEqualTo(0);
	}

	@Test
	@DisplayName("(d) 3MB 를 넘으면 413")
	void rejectsTooLarge() throws Exception {
		byte[] oversized = new byte[UploadedImageMaxBytesHolder.MAX_BYTES + 1];
		oversized[0] = (byte) 0xFF;
		oversized[1] = (byte) 0xD8;
		oversized[2] = (byte) 0xFF;

		mockMvc.perform(multipart("/api/v1/uploads/story-image")
						.file(new MockMultipartFile("file", "big.jpg", "image/jpeg", oversized))
						.principal(asUser()))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.error.code").value("IMAGE_TOO_LARGE"))
				.andExpect(jsonPath("$.error.fields").value(hasItem("maxBytes=3145728")));
	}

	@Test
	@DisplayName("(e) 빈 파일은 400")
	void rejectsEmpty() throws Exception {
		mockMvc.perform(multipart("/api/v1/uploads/story-image")
						.file(new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]))
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("IMAGE_EMPTY"));
	}

	@Test
	@DisplayName("(f) 없는 키를 조회하면 404")
	void unknownKeyReturns404() throws Exception {
		mockMvc.perform(get("/api/v1/uploads/images/story/2026/09/does-not-exist.jpg"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("IMAGE_NOT_FOUND"));
	}

	@Test
	@DisplayName("(g) 경로 조작이 든 키를 조회하면 400")
	void pathTraversalKeyReturns400() throws Exception {
		mockMvc.perform(get("/api/v1/uploads/images/story/../secret"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("IMAGE_KEY_INVALID"));
	}

	/** 표 전체가 아니라 이 테스트가 만든 사용자 몫만 센다. DB 가 테스트끼리 공유되므로 좁히지 않으면 남의 행이 섞인다. */
	private int uploadedImageCountForUser() {
		Integer count = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM uploaded_image WHERE uploader_user_id = ?", Integer.class, this.userId);
		return count == null ? 0 : count;
	}

	private int byteaColumnCount() {
		Integer count = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM information_schema.columns WHERE table_name = 'uploaded_image' AND data_type = 'bytea'",
				Integer.class);
		return count == null ? 0 : count;
	}

	private byte[] renderPng() throws IOException {
		BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private byte[] jpegWithFakeExif() throws IOException {
		BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "jpg", out);
		byte[] jpeg = out.toByteArray();

		byte[] exifPayload = "Exif  GPSLatitude=37.5,GPSLongitude=127.0".getBytes(StandardCharsets.US_ASCII);
		int segLen = exifPayload.length + 2;
		ByteArrayOutputStream withExif = new ByteArrayOutputStream();
		withExif.write(jpeg, 0, 2);
		withExif.write(0xFF);
		withExif.write(0xE1);
		withExif.write((segLen >> 8) & 0xFF);
		withExif.write(segLen & 0xFF);
		withExif.writeBytes(exifPayload);
		withExif.write(jpeg, 2, jpeg.length - 2);
		return withExif.toByteArray();
	}

	private boolean containsMarker(byte[] data, byte marker) {
		for (int i = 0; i < data.length - 1; i++) {
			if ((data[i] & 0xFF) == 0xFF && data[i + 1] == marker) {
				return true;
			}
		}
		return false;
	}

	private String extractImageUrl(String json) {
		String key = "\"imageUrl\":\"";
		int start = json.indexOf(key);
		if (start < 0) {
			throw new IllegalStateException("응답에 imageUrl 이 없다: " + json);
		}
		start += key.length();
		int end = json.indexOf('"', start);
		return json.substring(start, end);
	}

	private static final class UploadedImageMaxBytesHolder {

		static final int MAX_BYTES = com.gabolle.backend.story.domain.UploadedImage.MAX_BYTES;

		private UploadedImageMaxBytesHolder() {
		}
	}
}
