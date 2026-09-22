package com.gabolle.backend.event;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.recommendation.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이미 쌓인 {@code place_id} 를 {@code placeId} 로 옮기는 마이그레이션이 실제로 옮기는가
 * (S15P21E201-1481).
 *
 * <p>🔴 <b>Flyway 가 돌았다는 사실만으로는 아무것도 증명되지 않는다.</b> 테스트 DB 는 비어
 * 있어서 그 마이그레이션의 두 {@code UPDATE} 가 0건을 고치고 끝난다. 실서버에는 옮길 행이
 * 있으므로, 여기서는 행을 직접 심고 <b>같은 파일의 SQL 을 읽어</b> 돌린다.
 *
 * <p>SQL 을 여기에 다시 적지 않고 파일에서 읽는 이유는, 두 벌이 되면 마이그레이션을 고칠 때
 * 이 테스트가 옛 SQL 을 계속 통과시키기 때문이다 — 그러면 검사가 아니라 장식이다.
 */
class EventPayloadPlaceIdMigrationTest extends PostgresIntegrationTest {

	private static final Path MIGRATION =
			Path.of("src/main/resources/db/migration/V20260922093000__event_payload_place_id.sql");

	/** 뷰가 UUID 모양일 때만 {@code place_id} 를 채우므로, 옮김이 뷰까지 닿는지 보려면 진짜 UUID 여야 한다. */
	private static final String PLACE = "11111111-2222-3333-4444-555555555555";

	private static final String OTHER_PLACE = "99999999-8888-7777-6666-555555555555";

	@Autowired
	private JdbcTemplate jdbc;

	private UUID oldOnly;
	private UUID bothSame;
	private UUID bothDifferent;
	private UUID alreadyCanonical;

	/**
	 * 노출 이벤트. 품질 게이트 뷰는 {@code RECOMMENDATION_IMPRESSION} 만 보므로, 뷰까지 닿는지
	 * 보려면 이 종류여야 한다. 🔴 앱은 이 이벤트를 <b>아직 안 보낸다</b>(S15P21E201-544) —
	 * 그래서 지금 새고 있는 것은 아니고, 544 가 나가는 날 새기 시작한다.
	 */
	private UUID impressionWithOldKey;

	@BeforeEach
	void seed() {
		this.oldOnly = insert("place_like", "user", "{\"place_id\": \"" + PLACE + "\", \"surface\": \"home\"}");
		this.bothSame = insert("place_like", "user",
				"{\"place_id\": \"" + PLACE + "\", \"placeId\": \"" + PLACE + "\"}");
		this.bothDifferent = insert("place_like", "user",
				"{\"place_id\": \"" + PLACE + "\", \"placeId\": \"" + OTHER_PLACE + "\"}");
		this.alreadyCanonical = insert("place_like", "user", "{\"placeId\": \"" + PLACE + "\"}");
		this.impressionWithOldKey = insert("recommendation_impression", "recommendation",
				"{\"place_id\": \"" + PLACE + "\", \"finalRank\": 3}");
	}

	@AfterEach
	void cleanUp() {
		// 같은 DB 를 다른 테스트 클래스도 쓴다. 심은 것만 정확히 지운다.
		for (UUID id : List.of(this.oldOnly, this.bothSame, this.bothDifferent, this.alreadyCanonical,
				this.impressionWithOldKey)) {
			this.jdbc.update("DELETE FROM event_outbox WHERE event_id = ?", id);
		}
	}

	@Test
	@DisplayName("🔴 place_id 만 있던 행이 placeId 로 옮겨진다 — 옛 이름은 사라진다")
	void movesRowsThatOnlyHadTheOldKey() {
		runMigration();

		assertThat(payloadKey(this.oldOnly, "placeId")).isEqualTo(PLACE);
		assertThat(payloadKey(this.oldOnly, "place_id")).isNull();
		assertThat(payloadKey(this.oldOnly, "surface"))
				.as("옮기는 것은 키 하나뿐이다. 나머지 칸을 잃으면 안 된다")
				.isEqualTo("home");
	}

	@Test
	@DisplayName("🔴 노출 이벤트가 옛 키로 오면 품질 게이트 뷰가 장소를 못 찾는다 — 옮기면 찾는다")
	void theQualityGateViewResolvesThePlaceAfterwards() {
		assertThat(exposurePlaceId(this.impressionWithOldKey))
				.as("옮기기 전에는 뷰의 장소 칸이 비어 있다. 오류가 아니라 빈 칸이라 아무 데서도 안 드러난다")
				.isNull();

		runMigration();

		assertThat(exposurePlaceId(this.impressionWithOldKey)).isEqualTo(PLACE);
	}

	@Test
	@DisplayName("둘 다 있고 값이 같으면 옛 이름만 사라진다")
	void dropsTheAliasWhenBothAgree() {
		runMigration();

		assertThat(payloadKey(this.bothSame, "placeId")).isEqualTo(PLACE);
		assertThat(payloadKey(this.bothSame, "place_id")).isNull();
	}

	@Test
	@DisplayName("🔴 둘 다 있고 값이 «다르면» 안 건드린다 — 어느 쪽이 참인지 이 자리에서 못 정한다")
	void leavesDisagreeingRowsAlone() {
		runMigration();

		assertThat(payloadKey(this.bothDifferent, "placeId")).isEqualTo(OTHER_PLACE);
		assertThat(payloadKey(this.bothDifferent, "place_id"))
				.as("남아 있는 옛 키가 «이 행은 이상하다» 는 표시다. 지우면 그 사실이 사라진다")
				.isEqualTo(PLACE);
	}

	@Test
	@DisplayName("이미 맞는 행은 그대로다")
	void leavesCanonicalRowsAlone() {
		runMigration();

		assertThat(payloadKey(this.alreadyCanonical, "placeId")).isEqualTo(PLACE);
	}

	@Test
	@DisplayName("두 번 돌려도 결과가 같다 — 마이그레이션은 다시 적용될 수 있어야 한다")
	void isIdempotent() {
		runMigration();
		String afterFirst = payload(this.oldOnly);

		runMigration();

		assertThat(payload(this.oldOnly)).isEqualTo(afterFirst);
		assertThat(payloadKey(this.oldOnly, "placeId")).isEqualTo(PLACE);
	}

	// ── 거들기 ──────────────────────────────────────────────────────────────

	/** 마이그레이션 파일의 SQL 을 그대로 돌린다. 주석을 걷고 {@code ;} 로 끊는다. */
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

		assertThat(statements)
				.as("마이그레이션에서 실행할 문장을 하나도 못 찾았다 — 파일이 비었거나 끊는 방식이 낡았다")
				.isNotEmpty();

		statements.forEach(this.jdbc::execute);
	}

	private UUID insert(String eventType, String aggregateType, String payloadJson) {
		UUID eventId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		this.jdbc.update("""
				INSERT INTO event_outbox (event_id, event_type, event_version, aggregate_type,
				                          aggregate_id, partition_key, payload, occurred_at, received_at)
				VALUES (?, ?, 1, ?, ?, ?, CAST(? AS jsonb), ?, ?)
				""", eventId, eventType, aggregateType, eventId, eventId.toString(), payloadJson, now, now);
		return eventId;
	}

	private String payload(UUID eventId) {
		return this.jdbc.queryForObject(
				"SELECT payload::text FROM event_outbox WHERE event_id = ?", String.class, eventId);
	}

	private String payloadKey(UUID eventId, String key) {
		return this.jdbc.queryForObject(
				"SELECT payload ->> ? FROM event_outbox WHERE event_id = ?", String.class, key, eventId);
	}

	private String exposurePlaceId(UUID eventId) {
		return this.jdbc.queryForObject(
				"SELECT place_id::text FROM recommendation_exposure WHERE event_id = ?", String.class, eventId);
	}
}
