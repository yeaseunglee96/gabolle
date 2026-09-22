package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.presentation.StoryController;
import com.gabolle.backend.story.presentation.StoryExceptionHandler;
import com.gabolle.backend.story.presentation.UserSocialController;
import com.gabolle.testslice.StorySliceApplication;

/** 기록 작성·조회·수정·삭제를 실제 PostgreSQL 에서 본다. */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		// 운영도 validate 로 뜬다. 엔티티와 표가 어긋나면 운영 기동이 실패하므로 그 검사를 여기서 먼저 한다.
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class StoryCrudIntegrationTest {

	@TempDir
	static Path storageRoot;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
		registry.add("gabolle.storage.root", () -> storageRoot.toString());
		registry.add("gabolle.storage.public-base-path", () -> StoryFixture.IMAGE_BASE);
	}

	@Autowired
	private StoryController storyController;

	@Autowired
	private UserSocialController userSocialController;

	@Autowired
	private StoryExceptionHandler handler;

	@Autowired
	private JdbcTemplate jdbc;

	private final ObjectMapper json = new ObjectMapper();

	private MockMvc mockMvc;

	private UUID author;
	private UUID other;
	private UUID placeId;
	private UUID tripId;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.standaloneSetup(this.storyController, this.userSocialController)
				.setControllerAdvice(this.handler).build();
		this.author = StoryFixture.insertUser(this.jdbc, "작성자");
		this.other = StoryFixture.insertUser(this.jdbc, "다른 사람");
		this.placeId = StoryFixture.insertPlace(this.jdbc, "해운대해수욕장", "부산광역시 해운대구 우동 1015");
		// 9/10 ~ 9/12 여행. 공개 기본값은 9/13 00:00 KST = 9/12 15:00 UTC.
		this.tripId = StoryFixture.insertTrip(this.jdbc, this.author, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12),
				"Asia/Seoul");
	}

	private JsonNode create(UUID as, String body) throws Exception {
		MvcResult result = this.mockMvc.perform(post("/api/v1/stories").principal(StoryFixture.as(as))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andReturn();
		return this.json.readTree(result.getResponse().getContentAsString()).get("data");
	}

	private String body(Map<String, Object> fields) throws Exception {
		return this.json.writeValueAsString(fields);
	}

	@Test
	@DisplayName("🔴 사진 주소·글·장소·여행으로 기록을 만들면 DB 에 좌표는 없고 지역과 사진 주소만 있으며 공개 시각은 여행 종료 다음 날이다")
	void createStoresRegionUrlsAndDefaultPublishAt() throws Exception {
		String url1 = StoryFixture.insertUploadedImage(this.jdbc, this.author, StoryFixture.key("a"));
		String url2 = StoryFixture.insertUploadedImage(this.jdbc, this.author, StoryFixture.key("b"));

		// 앱이 좌표를 실어 보내도 서버에는 받을 칸이 없다 — 그대로 버려진다.
		JsonNode created = create(this.author, body(Map.of(
				"body", "해운대 첫날. 바다가 잔잔했다.",
				"imageUrls", List.of(url1, url2),
				"placeId", this.placeId.toString(),
				"tripId", this.tripId.toString(),
				"lat", 35.1587, "lng", 129.1604)));

		assertThat(created.get("publishAt").asText()).isEqualTo("2026-09-12T15:00:00Z");
		assertThat(created.get("region").asText()).isEqualTo("부산광역시 해운대구");
		assertThat(created.get("visibility").asText()).isEqualTo("PUBLIC");
		assertThat(created.get("place").get("name").asText()).isEqualTo("해운대해수욕장");
		assertThat(created.get("images")).hasSize(2);
		assertThat(created.get("images").get(0).get("url").asText()).isEqualTo(url1);
		assertThat(created.get("images").get(1).get("position").asInt()).isEqualTo(2);
		assertThat(created.get("mine").asBoolean()).isTrue();

		// DB: story 표에 좌표 칸이 없고, 사진 표 어디에도 바이트 칸이 없다.
		List<String> storyColumns = this.jdbc.queryForList(
				"SELECT column_name FROM information_schema.columns WHERE table_name = 'story'", String.class);
		assertThat(storyColumns).doesNotContain("lat", "lng", "latitude", "longitude");
		Integer byteColumns = this.jdbc.queryForObject(
				"SELECT count(*) FROM information_schema.columns WHERE table_name IN ('story','story_image','uploaded_image') "
						+ "AND data_type = 'bytea'", Integer.class);
		assertThat(byteColumns).isZero();
		String storedUrl = this.jdbc.queryForObject(
				"SELECT u.image_url FROM story_image si JOIN uploaded_image u ON u.uploaded_image_id = si.uploaded_image_id "
						+ "WHERE si.story_id = ?::uuid AND si.position = 1", String.class, created.get("id").asText());
		assertThat(storedUrl).isEqualTo(url1);
	}

	@Test
	@DisplayName("여행이 없으면 공개 시각 기본값은 지금이고, 지역을 직접 주면 그 값이 남는다")
	void createWithoutTripPublishesNow() throws Exception {
		Instant before = Instant.now();
		JsonNode created = create(this.author, body(Map.of("body", "여행 없는 기록", "region", "남포동")));
		Instant publishAt = Instant.parse(created.get("publishAt").asText());
		assertThat(publishAt).isBetween(before.minusSeconds(5), Instant.now().plusSeconds(5));
		assertThat(created.get("region").asText()).isEqualTo("남포동");
		assertThat(created.get("published").asBoolean()).isTrue();
	}

	@Test
	@DisplayName("🔴 남이 올린 사진, 4장, 이미 붙은 사진은 400 이고 기록이 생기지 않는다")
	void createRejectsBadImageReferences() throws Exception {
		String othersUrl = StoryFixture.insertUploadedImage(this.jdbc, this.other, StoryFixture.key("other"));
		this.mockMvc.perform(post("/api/v1/stories").principal(StoryFixture.as(this.author))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("body", "훔친 사진", "imageUrls", List.of(othersUrl)))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("STORY_REFERENCE_INVALID"))
				.andExpect(jsonPath("$.error.fields[0]").value("imageUrls"));

		List<String> four = List.of(
				StoryFixture.insertUploadedImage(this.jdbc, this.author, StoryFixture.key("1")),
				StoryFixture.insertUploadedImage(this.jdbc, this.author, StoryFixture.key("2")),
				StoryFixture.insertUploadedImage(this.jdbc, this.author, StoryFixture.key("3")),
				StoryFixture.insertUploadedImage(this.jdbc, this.author, StoryFixture.key("4")));
		this.mockMvc.perform(post("/api/v1/stories").principal(StoryFixture.as(this.author))
						.contentType(MediaType.APPLICATION_JSON).content(body(Map.of("body", "넷", "imageUrls", four))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("STORY_VALIDATION_FAILED"));

		String mine = StoryFixture.insertUploadedImage(this.jdbc, this.author, StoryFixture.key("mine"));
		create(this.author, body(Map.of("body", "첫 기록", "imageUrls", List.of(mine))));
		this.mockMvc.perform(post("/api/v1/stories").principal(StoryFixture.as(this.author))
						.contentType(MediaType.APPLICATION_JSON)
						.content(body(Map.of("body", "같은 사진 또", "imageUrls", List.of(mine)))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("STORY_REFERENCE_INVALID"));

		Integer stories = this.jdbc.queryForObject("SELECT count(*) FROM story WHERE author_user_id = ?", Integer.class,
				this.author);
		assertThat(stories).isEqualTo(1);
	}

	/** 403 이 아니라 404 인 것이 핵심이다. 403 으로 답하면 기록이 있다는 사실을 알려 주는 셈이다. */
	@Test
	@DisplayName("🔴 나만 보기·팔로워 전용·공개 전 기록은 남에게 404 이고, 작성자와 팔로워에게만 보인다")
	void visibilityRulesOnGet() throws Exception {
		Instant published = Instant.now().minus(Duration.ofHours(1));
		UUID privateStory = StoryFixture.insertStory(this.jdbc, this.author, "비공개", "PRIVATE", published);
		UUID followersStory = StoryFixture.insertStory(this.jdbc, this.author, "팔로워만", "FOLLOWERS", published);
		UUID unpublished = StoryFixture.insertStory(this.jdbc, this.author, "공개 전", "PUBLIC", Instant.now().plus(Duration.ofDays(3)));

		this.mockMvc.perform(get("/api/v1/stories/{id}", privateStory).principal(StoryFixture.as(this.other)))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("STORY_NOT_FOUND"));
		this.mockMvc.perform(get("/api/v1/stories/{id}", followersStory).principal(StoryFixture.as(this.other)))
				.andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/v1/stories/{id}", unpublished).principal(StoryFixture.as(this.other)))
				.andExpect(status().isNotFound());

		// 작성자는 셋 다 본다. 공개 전 것은 published=false 로.
		this.mockMvc.perform(get("/api/v1/stories/{id}", privateStory).principal(StoryFixture.as(this.author)))
				.andExpect(status().isOk());
		this.mockMvc.perform(get("/api/v1/stories/{id}", unpublished).principal(StoryFixture.as(this.author)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.published").value(false));

		// 팔로우하면 팔로워 전용은 보이고, 비공개는 여전히 안 보인다.
		StoryFixture.insertFollow(this.jdbc, this.other, this.author);
		this.mockMvc.perform(get("/api/v1/stories/{id}", followersStory).principal(StoryFixture.as(this.other)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.mine").value(false));
		this.mockMvc.perform(get("/api/v1/stories/{id}", privateStory).principal(StoryFixture.as(this.other)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("🔴 내 기록은 고쳐지고 남의 공개 기록 수정은 403, 남의 비공개 기록 수정은 404 다")
	void updateAuthorization() throws Exception {
		Instant published = Instant.now().minus(Duration.ofHours(1));
		UUID publicStory = StoryFixture.insertStory(this.jdbc, this.author, "원문", "PUBLIC", published);
		UUID privateStory = StoryFixture.insertStory(this.jdbc, this.author, "비공개 원문", "PRIVATE", published);

		this.mockMvc.perform(patch("/api/v1/stories/{id}", publicStory).principal(StoryFixture.as(this.author))
						.contentType(MediaType.APPLICATION_JSON).content(body(Map.of("body", "고친 글", "visibility", "FOLLOWERS"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.body").value("고친 글"))
				.andExpect(jsonPath("$.data.visibility").value("FOLLOWERS"));
		assertThat(this.jdbc.queryForObject("SELECT body FROM story WHERE story_id = ?", String.class, publicStory))
				.isEqualTo("고친 글");

		// 이제 FOLLOWERS 라 남에게는 안 보인다 → 404. 다시 PUBLIC 으로 돌려 403 을 본다.
		this.jdbc.update("UPDATE story SET visibility = 'PUBLIC' WHERE story_id = ?", publicStory);
		this.mockMvc.perform(patch("/api/v1/stories/{id}", publicStory).principal(StoryFixture.as(this.other))
						.contentType(MediaType.APPLICATION_JSON).content(body(Map.of("body", "남이 고침"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("STORY_FORBIDDEN"));
		this.mockMvc.perform(patch("/api/v1/stories/{id}", privateStory).principal(StoryFixture.as(this.other))
						.contentType(MediaType.APPLICATION_JSON).content(body(Map.of("body", "남이 고침"))))
				.andExpect(status().isNotFound());
		assertThat(this.jdbc.queryForObject("SELECT body FROM story WHERE story_id = ?", String.class, publicStory))
				.isEqualTo("고친 글");
	}

	/** 기록 행은 남고 deleted_at 만 찍힌다. 지워지는 것은 저장소의 사진 파일 쪽이다. */
	@Test
	@DisplayName("🔴 지우면 204, 이후 404, 피드에서 사라지고, 저장소의 사진 파일도 함께 지워진다")
	void deleteRemovesStoryAndStorageFiles() throws Exception {
		String key = StoryFixture.key("to-delete");
		Path file = storageRoot.resolve(key);
		Files.createDirectories(file.getParent());
		Files.write(file, new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00 });
		String url = StoryFixture.insertUploadedImage(this.jdbc, this.author, key);
		JsonNode created = create(this.author, body(Map.of("body", "지울 기록", "imageUrls", List.of(url))));
		String id = created.get("id").asText();

		// 남이 지우려 하면 403, 기록은 그대로.
		this.mockMvc.perform(delete("/api/v1/stories/{id}", id).principal(StoryFixture.as(this.other)))
				.andExpect(status().isForbidden());
		assertThat(Files.exists(file)).isTrue();

		this.mockMvc.perform(delete("/api/v1/stories/{id}", id).principal(StoryFixture.as(this.author)))
				.andExpect(status().isNoContent());

		this.mockMvc.perform(get("/api/v1/stories/{id}", id).principal(StoryFixture.as(this.author)))
				.andExpect(status().isNotFound());
		MvcResult feed = this.mockMvc.perform(get("/api/v1/stories").principal(StoryFixture.as(this.author)))
				.andExpect(status().isOk()).andReturn();
		assertThat(feed.getResponse().getContentAsString()).doesNotContain(id);

		assertThat(Files.exists(file)).as("저장소의 사진 파일이 지워졌다").isFalse();
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM story WHERE story_id = ?::uuid",
				Boolean.class, id)).isTrue();
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM uploaded_image WHERE storage_key = ?",
				Boolean.class, key)).isTrue();
		Integer queued = this.jdbc.queryForObject("SELECT count(*) FROM storage_cleanup_queue WHERE storage_key = ?",
				Integer.class, key);
		assertThat(queued).as("잘 지웠으면 뒷정리 목록에 남지 않는다").isZero();

		// 두 번 지우면 404 — 이미 없는 기록이다.
		this.mockMvc.perform(delete("/api/v1/stories/{id}", id).principal(StoryFixture.as(this.author)))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("글이 비었거나 500자를 넘으면 400 STORY_VALIDATION_FAILED")
	void bodyValidation() throws Exception {
		this.mockMvc.perform(post("/api/v1/stories").principal(StoryFixture.as(this.author))
						.contentType(MediaType.APPLICATION_JSON).content(body(Map.of("body", "  "))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("STORY_VALIDATION_FAILED"))
				.andExpect(jsonPath("$.error.fields[0]").value("body"));
		this.mockMvc.perform(post("/api/v1/stories").principal(StoryFixture.as(this.author))
						.contentType(MediaType.APPLICATION_JSON).content(body(Map.of("body", "가".repeat(501)))))
				.andExpect(status().isBadRequest());
	}
}
