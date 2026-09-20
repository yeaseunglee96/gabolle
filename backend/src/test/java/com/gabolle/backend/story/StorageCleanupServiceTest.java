package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.application.StorageCleanupService;
import com.gabolle.backend.story.domain.StorageCleanupEntry;
import com.gabolle.backend.story.storage.StoragePort;
import com.gabolle.testslice.StorySliceApplication;

/** 실패 경로만 본다. 성공 경로({@code retryPending})는 저장소 스텁이 달라 {@link StorageCleanupServiceRetryTest} 로 나눴다. */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
// classes= 로 앱을 명시하면 중첩 @TestConfiguration 이 자동으로 잡히지 않아 직접 끌어온다.
@Import(StorageCleanupServiceTest.FailingStorageConfig.class)
@ExtendWith(PostgresAvailableCondition.class)
class StorageCleanupServiceTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@TestConfiguration
	static class FailingStorageConfig {

		@Bean
		@Primary
		StoragePort failingStoragePort() {
			return new StoragePort() {

				@Override
				public String put(String key, String contentType, byte[] bytes) {
					throw new StorageException("업로드 스텁은 쓰지 않는다");
				}

				@Override
				public void delete(String key) {
					throw new StorageException("항상 실패하는 저장소 스텁");
				}

				@Override
				public Optional<StoredObject> get(String key) {
					return Optional.empty();
				}
			};
		}
	}

	@Autowired
	private StorageCleanupService storageCleanupService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanQueue() {
		this.jdbcTemplate.update("DELETE FROM storage_cleanup_queue");
	}

	@Test
	@DisplayName("저장소 삭제가 실패해도 예외를 내지 않고 대기열에 남긴다 — 두 번째 실패는 attempts 를 올린다")
	void enqueuesOnFailureWithoutThrowing() {
		assertThatCode(
				() -> this.storageCleanupService.deleteOrEnqueue("story/x.jpg", StorageCleanupEntry.REASON_STORY_DELETED))
				.doesNotThrowAnyException();

		Integer countAfterFirst = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM storage_cleanup_queue WHERE storage_key = ?", Integer.class, "story/x.jpg");
        assertThat(countAfterFirst).isEqualTo(1);

		this.storageCleanupService.deleteOrEnqueue("story/x.jpg", StorageCleanupEntry.REASON_STORY_DELETED);

		Integer attempts = this.jdbcTemplate.queryForObject(
				"SELECT attempts FROM storage_cleanup_queue WHERE storage_key = ?", Integer.class, "story/x.jpg");
		assertThat(attempts).isEqualTo(2);
	}
}
