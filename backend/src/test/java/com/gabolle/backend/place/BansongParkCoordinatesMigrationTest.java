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
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.recommendation.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 반송공원 좌표를 고치는 마이그레이션이 틀린 값만 고치는가 (S15P21E201-1614).
 *
 * <p>🔴 시험 DB 에는 반송공원이 없어 Flyway 가 돈 것만으로는 0건을 고치고 끝난다. 그래서 행을 직접
 * 심고 <b>같은 파일의 SQL 을 읽어</b> 돌린다 — SQL 을 여기 다시 적으면 파일을 고쳐도 이 시험이 옛 SQL 을
 * 계속 통과시킨다({@code EventPayloadPlaceIdMigrationTest} 와 같은 방식).
 */
class BansongParkCoordinatesMigrationTest extends PostgresIntegrationTest {

	private static final Path MIGRATION =
			Path.of("src/main/resources/db/migration/V20260925030000__bansong_park_coordinates.sql");

	private static final String CONTENT_ID = "2907087";

	@Autowired
	private JdbcTemplate jdbc;

	private final List<UUID> seeded = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		// 같은 DB 를 다른 시험 클래스도 쓴다. 심은 것만 지운다.
		this.seeded.forEach((placeId) -> this.jdbc.update("DELETE FROM place WHERE place_id = ?", placeId));
		this.seeded.clear();
	}

	@Test
	@DisplayName("🔴 남중국해에 찍힌 반송공원을 오픈스트리트맵 좌표로 옮긴다")
	void theWrongCoordinatesAreFixed() {
		UUID placeId = seed(CONTENT_ID, 19.69442748, 117.9925662504);

		runMigration();

		Map<String, Object> row = coordinatesOf(placeId);
		assertThat((Double) row.get("lat")).isEqualTo(35.2207704);
		assertThat((Double) row.get("lng")).isEqualTo(129.1619776);
	}

	@Test
	@DisplayName("이미 부산 안에 있으면 건드리지 않는다 — 누가 먼저 고쳤거나 원천이 바로잡힌 때")
	void alreadyFixedCoordinatesAreLeftAlone() {
		UUID placeId = seed(CONTENT_ID, 35.2201, 129.1612);

		runMigration();

		Map<String, Object> row = coordinatesOf(placeId);
		assertThat((Double) row.get("lat")).isEqualTo(35.2201);
		assertThat((Double) row.get("lng")).isEqualTo(129.1612);
	}

	@Test
	@DisplayName("🔴 다른 장소는 좌표가 같이 틀렸어도 안 건드린다 — 이 파일은 반송공원 하나만 안다")
	void otherPlacesAreNotTouched() {
		UUID other = seed("9999999", 19.69442748, 117.9925662504);

		runMigration();

		assertThat((Double) coordinatesOf(other).get("lat")).isEqualTo(19.69442748);
	}

	private UUID seed(String contentId, double lat, double lng) {
		UUID placeId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO place (place_id, name_ko, lat, lng, source_type, source_id, created_at)
				VALUES (?, '반송공원', ?, ?, 'TOURAPI', ?, ?)
				""", placeId, lat, lng, contentId, OffsetDateTime.now());
		this.seeded.add(placeId);
		return placeId;
	}

	private Map<String, Object> coordinatesOf(UUID placeId) {
		return this.jdbc.queryForMap("SELECT lat, lng FROM place WHERE place_id = ?", placeId);
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
