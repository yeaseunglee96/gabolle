package com.gabolle.backend.place;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.recommendation.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 유아차 대여 안내로 붙은 STROLLER 표식을 지우는 마이그레이션이 그것만 지우는가. 시험 DB 에는 그 행이 없어 행을 직접
 * 심고 같은 파일의 SQL 을 읽어 돌린다({@code BansongParkCoordinatesMigrationTest} 와 같은 방식).
 */
class DropStrollerRentalTagsMigrationTest extends PostgresIntegrationTest {

	private static final Path MIGRATION =
			Path.of("src/main/resources/db/migration/V20260929120200__drop_stroller_rental_tags.sql");

	@Autowired
	private JdbcTemplate jdbc;

	private final List<UUID> seeded = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		this.seeded.forEach((placeId) -> {
			this.jdbc.update("DELETE FROM place_feature WHERE place_id = ?", placeId);
			this.jdbc.update("DELETE FROM place WHERE place_id = ?", placeId);
		});
		this.seeded.clear();
	}

	@Test
	@DisplayName("🔴 관광공사 무장애 자료의 STROLLER 는 지우고, 같은 곳의 WHEELCHAIR 와 다른 출처의 STROLLER 는 남긴다")
	void onlyTourApiStrollerTagsAreDeleted() {
		UUID place = seedPlace();
		seedTag(place, "STROLLER", "TOURAPI");
		seedTag(place, "WHEELCHAIR", "TOURAPI");
		UUID other = seedPlace();
		seedTag(other, "STROLLER", "BF_FACILITY");

		runMigration();

		assertThat(tags(place)).containsExactly("WHEELCHAIR");
		assertThat(tags(other)).containsExactly("STROLLER");
	}

	private UUID seedPlace() {
		UUID placeId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, '유아차 시험 장소', ?)", placeId,
				OffsetDateTime.now());
		this.seeded.add(placeId);
		return placeId;
	}

	private void seedTag(UUID placeId, String key, String sourceType) {
		this.jdbc.update("""
				INSERT INTO place_feature (place_feature_id, place_id, feature_type, feature_key, value,
				    evidence_status, source_type, source_id, observed_at, source_version, created_at)
				VALUES (?, ?, 'ACCESSIBILITY_TAG', ?, CAST('true' AS jsonb), 'VERIFIED', ?, ?, NULL, 'fixture', ?)
				""", UUID.randomUUID(), placeId, key, sourceType, placeId.toString(), OffsetDateTime.now());
	}

	private List<String> tags(UUID placeId) {
		return this.jdbc.queryForList("SELECT feature_key FROM place_feature WHERE place_id = ? "
				+ "AND feature_type = 'ACCESSIBILITY_TAG' ORDER BY feature_key", String.class, placeId);
	}

	private void runMigration() {
		String text;
		try {
			text = Files.readString(MIGRATION, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("마이그레이션 파일을 못 읽었다 — 이름이 바뀌었나: " + MIGRATION, ex);
		}
		String withoutComments = text.lines()
				.map((line) -> line.replaceFirst("--.*$", ""))
				.reduce("", (a, b) -> a + "\n" + b);
		List<String> statements = Arrays.stream(withoutComments.split(";"))
				.map(String::trim)
				.filter((s) -> !s.isEmpty())
				.toList();
		assertThat(statements).as("마이그레이션에서 실행할 문장을 하나도 못 찾았다").isNotEmpty();
		statements.forEach(this.jdbc::execute);
	}
}
