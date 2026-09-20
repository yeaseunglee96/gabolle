package com.gabolle.backend.story;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

/** 성공 경로만 본다. 실패 경로({@code deleteOrEnqueue})는 {@link StorageCleanupServiceTest}. */
@SpringBootTest(classes = StorySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
// classes= 로 앱을 명시하면 중첩 @TestConfiguration 이 자동으로 잡히지 않아 직접 끌어온다.
@Import(StorageCleanupServiceRetryTest.SucceedingStorageConfig.class)
@ExtendWith(PostgresAvailableCondition.class)
class StorageCleanupServiceRetryTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@TestConfiguration
	static class SucceedingStorageConfig {

		@Bean
		@Primary
		StoragePort succeedingStoragePort() {
			return new StoragePort() {

				@Override
				public String put(String key, String contentType, byte[] bytes) {
					return "/x/" + key;
				}

				@Override
				public void delete(String key) {
					// 항상 성공하는 스텁.
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
	void seedPendingEntry() {
		this.jdbcTemplate.update("DELETE FROM storage_cleanup_queue");
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbcTemplate.update(
				"INSERT INTO storage_cleanup_queue (storage_key, reason, first_failed_at, last_attempt_at, attempts) "
						+ "VALUES (?, ?, ?, ?, 1)",
				"story/pending.jpg", StorageCleanupEntry.REASON_STORY_DELETED, now, now);
	}

	@Test
	@DisplayName("저장소가 다시 되면 재시도로 대기열이 비고 지운 개수를 돌려준다")
	void retryDeletesAndClearsQueue() {
		int deletedCount = this.storageCleanupService.retryPending();

		assertThat(deletedCount).isEqualTo(1);
		Integer remaining = this.jdbcTemplate.queryForObject("SELECT count(*) FROM storage_cleanup_queue",
				Integer.class);
		assertThat(remaining).isZero();
	}
}
