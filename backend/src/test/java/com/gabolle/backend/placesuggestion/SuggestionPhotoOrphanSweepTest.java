package com.gabolle.backend.placesuggestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.story.StoryFixture;
import com.gabolle.backend.story.repository.UploadedImageRepository;
import com.gabolle.testslice.StorySliceApplication;

/**
 * 🔴 장소 제안에 붙인 사진을 고아 청소가 지우지 않는다 — S15P21E201-2031. 검토는 며칠 걸릴 수 있는데 청소는 하루 유예뿐이다.
 */
@SpringBootTest(classes = StorySliceApplication.class, properties = { "spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=true" })
@ExtendWith(PostgresAvailableCondition.class)
class SuggestionPhotoOrphanSweepTest {

	@TempDir
	static Path storageRoot;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
		registry.add("gabolle.storage.root", () -> storageRoot.toString());
		registry.add("gabolle.storage.public-base-path", () -> StoryFixture.IMAGE_BASE);
	}

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private UploadedImageRepository repository;

	@Test
	@DisplayName("제안에 붙은 사진·승인돼 장소에 붙은 사진은 고아가 아니고, 아무 데도 안 붙은 사진은 고아다")
	void suggestionPhotoIsNotAnOrphan() {
		UUID user = StoryFixture.insertUser(this.jdbc, "제안한 사람");
		OffsetDateTime old = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5);
		String attached = insert(user, old);
		String onPlace = insert(user, old);
		String loose = insert(user, old);
		this.jdbc.update("""
				INSERT INTO place_suggestion (place_suggestion_id, suggester_user_id, name, category, lat, lng, reason,
				    photo_url, created_at) VALUES (?, ?, '사진 가게', 'FOOD', 35.15, 129.11, '좋아요', ?, now())""",
				UUID.randomUUID(), user, attached);
		this.jdbc.update("INSERT INTO place (place_id, name_ko, lat, lng, created_at, photo_url) VALUES (?, '승인된 곳', 35.15, 129.11, now(), ?)",
				UUID.randomUUID(), onPlace);

		var orphans = this.repository.findOrphansOlderThan(Instant.now(), PageRequest.of(0, 10_000)).stream()
				.map(i -> i.getImageUrl()).toList();
		assertThat(orphans).contains(loose).doesNotContain(attached, onPlace);
	}

	private String insert(UUID user, OffsetDateTime at) {
		String key = StoryFixture.key("suggest");
		String url = StoryFixture.IMAGE_BASE + "/" + key;
		this.jdbc.update("INSERT INTO uploaded_image (uploaded_image_id, uploader_user_id, storage_key, image_url, content_type, byte_size, created_at) "
				+ "VALUES (?, ?, ?, ?, 'image/jpeg', 10, ?)", UUID.randomUUID(), user, key, url, at);
		return url;
	}
}
