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
 * 옛 p90 경사 행을 걷어 내는 마이그레이션이 옛 행만 지우는가.
 *
 * <p>🔴 시험 DB 에는 장소가 없어 옛 경사 행이 애초에 안 들어가므로({@code V20260916230000} 은 장소가 있을 때만 넣는다)
 * Flyway 가 돈 것만으로는 0건을 지우고 끝난다. 그래서 행을 직접 심고 <b>같은 파일의 SQL 을 읽어</b> 돌린다
 * ({@code BansongParkCoordinatesMigrationTest} 와 같은 방식).
 */
class DropP90PlaceSlopeMigrationTest extends PostgresIntegrationTest {

	private static final Path MIGRATION =
			Path.of("src/main/resources/db/migration/V20260929120100__drop_p90_place_slope.sql");

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
	@DisplayName("🔴 옛 두 판(p90)의 경사 행은 지운다 — 남아 있으면 p50 적재가 「이미 있어 건너뜀」으로 끝난다")
	void staleP90RowsAreDeleted() {
		UUID sbiz = seedSlope("SBIZ", "2026-09-16-slope-r200-sbiz", "{\"score\": 12.4, \"radiusM\": 200.0}");
		UUID tour = seedSlope("TOURAPI", "2026-09-16-slope-r200", "{\"score\": 16.4, \"radiusM\": 200.0}");

		runMigration();

		assertThat(slopeRows(sbiz)).isZero();
		assertThat(slopeRows(tour)).isZero();
	}

	@Test
	@DisplayName("새 p50 행과 다른 판은 남긴다 — 이 파일이 새 값을 지우면 안 된다")
	void p50RowsAreKept() {
		UUID derived = seedSlope("DERIVED_SLOPE", "2026-09-25-slope-p50-r200", "{\"score\": 3.2, \"stat\": \"p50\"}");
		UUID markedP50 = seedSlope("SBIZ", "2026-09-16-slope-r200-sbiz", "{\"score\": 3.2, \"stat\": \"p50\"}");

		runMigration();

		assertThat(slopeRows(derived)).isEqualTo(1);
		assertThat(slopeRows(markedP50)).as("판 이름이 옛것이어도 p50 이라고 적혀 있으면 남긴다").isEqualTo(1);
	}

	private UUID seedSlope(String sourceType, String sourceVersion, String valueJson) {
		UUID placeId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, '경사 시험 장소', ?)", placeId, now);
		this.seeded.add(placeId);
		this.jdbc.update("""
				INSERT INTO place_feature (place_feature_id, place_id, feature_type, feature_key, value,
				    evidence_status, source_type, source_id, observed_at, source_version, created_at)
				VALUES (?, ?, 'SLOPE_PERCENT', NULL, CAST(? AS jsonb), 'ESTIMATED', ?, ?, NULL, ?, ?)
				""", UUID.randomUUID(), placeId, valueJson, sourceType, placeId.toString(), sourceVersion, now);
		return placeId;
	}

	private int slopeRows(UUID placeId) {
		Integer count = this.jdbc.queryForObject(
				"SELECT count(*) FROM place_feature WHERE place_id = ? AND feature_type = 'SLOPE_PERCENT'",
				Integer.class, placeId);
		return count == null ? 0 : count;
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
