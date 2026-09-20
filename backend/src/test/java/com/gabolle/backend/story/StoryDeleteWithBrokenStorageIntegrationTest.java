package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.presentation.StoryController;
import com.gabolle.backend.story.presentation.StoryExceptionHandler;
import com.gabolle.backend.story.storage.StoragePort;
import com.gabolle.testslice.StorySliceApplication;

/**
 * 저장소가 언제나 실패하는 구현을 {@code @Primary} 로 끼운다. 파일 하나 때문에 사용자를 몇 번을 눌러도
 * 안 지워지는 상태에 가두지 않으면서, 못 지운 키를 조용히 잊지도 않아야 한다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@Import(StoryDeleteWithBrokenStorageIntegrationTest.BrokenStorage.class)
@ExtendWith(PostgresAvailableCondition.class)
class StoryDeleteWithBrokenStorageIntegrationTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class BrokenStorage {

		@Bean
		@Primary
		StoragePort brokenStoragePort() {
			return new StoragePort() {
				@Override
				public String put(String key, String contentType, byte[] bytes) {
					throw new StorageException("저장소가 끊겼다(테스트)");
				}

				@Override
				public void delete(String key) {
					throw new StorageException("저장소가 끊겼다(테스트)");
				}

				@Override
				public Optional<StoredObject> get(String key) {
					return Optional.empty();
				}
			};
		}
	}

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
	private StoryExceptionHandler handler;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@DisplayName("🔴 저장소가 끊겨 있어도 기록 삭제는 204 로 끝나고 못 지운 파일 키가 뒷정리 목록에 남는다")
	void deleteSucceedsAndQueuesTheFile() throws Exception {
		var mockMvc = MockMvcBuilders.standaloneSetup(this.storyController).setControllerAdvice(this.handler).build();
		UUID author = StoryFixture.insertUser(this.jdbc, "작성자");
		String key = StoryFixture.key("unreachable");
		StoryFixture.insertUploadedImage(this.jdbc, author, key);
		UUID storyId = StoryFixture.insertStory(this.jdbc, author, "지울 기록", "PUBLIC", Instant.now().minus(Duration.ofHours(1)));
		this.jdbc.update(
				"INSERT INTO story_image (story_image_id, story_id, uploaded_image_id, position, created_at) "
						+ "SELECT ?, ?, uploaded_image_id, 1, now() FROM uploaded_image WHERE storage_key = ?",
				UUID.randomUUID(), storyId, key);

		mockMvc.perform(delete("/api/v1/stories/{id}", storyId).principal(StoryFixture.as(author)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/stories/{id}", storyId).principal(StoryFixture.as(author)))
				.andExpect(status().isNotFound());
		assertThat(this.jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM story WHERE story_id = ?", Boolean.class, storyId))
				.isTrue();
		assertThat(this.jdbc.queryForObject("SELECT reason FROM storage_cleanup_queue WHERE storage_key = ?", String.class, key))
				.isEqualTo("STORY_DELETED");
		assertThat(this.jdbc.queryForObject("SELECT attempts FROM storage_cleanup_queue WHERE storage_key = ?", Integer.class, key))
				.isEqualTo(1);
	}
}
