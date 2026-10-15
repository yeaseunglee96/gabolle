package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 현지인 설문 추천 47곳을 운영 DB 에 다는 SQL 파일(backend/scripts/local-picks-20261015/local_picks_20261015.sql)을 **진짜 PostgreSQL 에서 그대로
 * 돌려 본다** (S15P21E201-2059). 운영에서 돌릴 파일이 한 번도 안 돌아 본 채 나가면 첫 실행이 곧 시험이 된다 — 그래서 같은 파일을 두 번 돌려
 * 결과와 멱등(두 번 돌려도 같다)을 본다.
 *
 * <p>이 시험 DB 에는 설문 장소가 없다. 입력 파일의 번호 그대로 가짜 장소를 만들어 넣고(이름 앞에 시험 표시), 일부러 상황을 만든다 — 장소가
 * 없는 줄 · 추천에서 빠진 줄 · 이미 맛집 표식과 다른 구·동이 달린 줄 · 이미 맛집이 달린 카페 · 맛집과 카페가 둘 다 달린 카페. 운영 SQL 의 「맛집에서
 * 카페로 옮기기」는 모든 카페 갈래 장소를 보므로, 설문과 상관없는 장소 몇 곳도 함께 만들어 옮겨지는 것과 남는 것을 가른다.
 */
class LocalPicksSqlIntegrationTest extends PlacePostgresIntegrationTest {

	private static final Path SQL = Path.of("scripts/local-picks-20261015/local_picks_20261015.sql");

	private static final Path DATA = Path.of("scripts/local-picks-20261015/survey-local-picks-20261015.ndjson");

	private static final String VERSION = "survey-local-picks-20261015";

	/** 이 시험이 만든 가짜 장소의 {@code source_type} — 지울 때 이것으로 걸고, 다른 시험의 행은 안 건드린다. */
	private static final String FAKE_SOURCE = "ZZ2059SQL";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private List<Pick> picks;

	private Pick missing;

	private Pick hidden;

	/** 이미 맛집 표식·다른 구·동·인증 목록이 달린 음식 장소. */
	private Pick foodWithMarks;

	/** 이미 맛집 표식이 달린 카페 갈래 장소 — 설문에 든 카페가 공공 인증도 받은 경우. */
	private Pick cafeWithRestaurantMark;

	/** 맛집·카페 표식이 둘 다 달린 카페 갈래 장소. */
	private Pick cafeWithBothMarks;

	/** 추천 인원을 모르는 줄(recommenders 가 null). */
	private Pick unknownRecommenders;

	private UUID outsiderCafe;

	private UUID outsiderCafeMarkId;

	private UUID outsiderFood;

	private UUID outsiderUntagged;

	private record Pick(UUID placeId, String name, String category, String gu, String dong, Integer recommenders) {
	}

	@BeforeEach
	void setUp() throws IOException {
		cleanUp();
		this.picks = readPicks();
		assertThat(this.picks).hasSize(47);

		// 상황을 만들 줄을 고른다 — 골라낸 줄은 서로 겹치지 않는다.
		this.missing = firstOf("FOOD", null, 0);
		this.hidden = firstOf("FOOD", null, 1);
		this.foodWithMarks = firstOf("FOOD", null, 2);
		this.cafeWithRestaurantMark = firstOf("CAFE_HEALING", null, 0);
		this.cafeWithBothMarks = firstOf("CAFE_HEALING", null, 1);
		this.unknownRecommenders = this.picks.stream().filter(pick -> pick.recommenders() == null).findFirst().orElseThrow();
		assertThat(List.of(this.missing, this.hidden, this.foodWithMarks, this.cafeWithRestaurantMark, this.cafeWithBothMarks,
				this.unknownRecommenders)).doesNotHaveDuplicates();

		for (Pick pick : this.picks) {
			if (pick == this.missing) {
				continue;
			}
			insertPlace(pick.placeId(), pick.name(), pick.category(), pick == this.hidden ? "HIDDEN" : "CURATED");
		}

		// 이미 달려 있는 것들 — 맛집 적재기가 단 모양(source_type BUSAN_CERTIFIED_FOOD)으로 심는다.
		insertFeature(UUID.randomUUID(), this.foodWithMarks.placeId(), "INTEREST_TAG", "LOCAL_RESTAURANT", "true", "VERIFIED", "BUSAN_CERTIFIED_FOOD", "certified-restaurants-20261013");
		insertFeature(UUID.randomUUID(), this.foodWithMarks.placeId(), "ADMIN_AREA_TAG", "ZZ원래구/ZZ원래동", "{\"gu\":\"ZZ원래구\",\"dong\":\"ZZ원래동\"}", "ESTIMATED", "BUSAN_CERTIFIED_FOOD", "certified-restaurants-20261013");
		insertFeature(UUID.randomUUID(), this.foodWithMarks.placeId(), "CERTIFICATION_TAG", "MODEL_RESTAURANT", "{\"source\":\"모범음식점\"}", "VERIFIED", "BUSAN_CERTIFIED_FOOD", "certified-restaurants-20261013");
		insertFeature(UUID.randomUUID(), this.cafeWithRestaurantMark.placeId(), "INTEREST_TAG", "LOCAL_RESTAURANT", "true", "VERIFIED", "BUSAN_CERTIFIED_FOOD", "certified-restaurants-20261013");
		insertFeature(UUID.randomUUID(), this.cafeWithBothMarks.placeId(), "INTEREST_TAG", "LOCAL_RESTAURANT", "true", "VERIFIED", "BUSAN_CERTIFIED_FOOD", "certified-restaurants-20261013");
		insertFeature(UUID.randomUUID(), this.cafeWithBothMarks.placeId(), "INTEREST_TAG", "LOCAL_CAFE", "true", "VERIFIED", "BUSAN_LOCAL_CAFE", "cafes-20261015");

		// 설문과 상관없는 장소 — 옮겨질 카페, 그대로 둘 음식점, 표식이 없는 카페.
		this.outsiderCafe = UUID.randomUUID();
		insertPlace(this.outsiderCafe, "설문밖카페", "CAFE_HEALING", "CURATED");
		this.outsiderCafeMarkId = UUID.randomUUID();
		insertFeature(this.outsiderCafeMarkId, this.outsiderCafe, "INTEREST_TAG", "LOCAL_RESTAURANT", "true", "VERIFIED", "BUSAN_CERTIFIED_FOOD", "certified-restaurants-20261013");
		this.outsiderFood = UUID.randomUUID();
		insertPlace(this.outsiderFood, "설문밖식당", "FOOD", "CURATED");
		insertFeature(UUID.randomUUID(), this.outsiderFood, "INTEREST_TAG", "LOCAL_RESTAURANT", "true", "VERIFIED", "BUSAN_CERTIFIED_FOOD", "certified-restaurants-20261013");
		this.outsiderUntagged = UUID.randomUUID();
		insertPlace(this.outsiderUntagged, "설문밖표식없는카페", "CAFE_HEALING", "CURATED");
	}

	@AfterEach
	void tearDown() {
		cleanUp();
	}

	@Test
	@DisplayName("🔴 파일을 그대로 돌리면 달 수 있는 곳(DB 에 있고 추천에 쓰는 곳)에만, 갈래(맛집/카페)·구·동·현지인 추천이 달린다 — 없는 곳·빠진 곳은 건드리지 않는다")
	void marksTheEligiblePicksAndLeavesTheOthersAlone() {
		runSql();

		List<Pick> eligible = this.picks.stream().filter(pick -> pick != this.missing && pick != this.hidden).toList();
		assertThat(eligible).hasSize(45);

		for (Pick pick : eligible) {
			String expectedKey = pick.category().equals("FOOD") ? "LOCAL_RESTAURANT" : "LOCAL_CAFE";
			String otherKey = pick.category().equals("FOOD") ? "LOCAL_CAFE" : "LOCAL_RESTAURANT";
			assertThat(count(pick.placeId(), "INTEREST_TAG", expectedKey)).as(pick.name() + " 갈래 표식").isEqualTo(1);
			assertThat(count(pick.placeId(), "INTEREST_TAG", otherKey)).as(pick.name() + " 반대쪽 갈래는 없다").isZero();
			assertThat(count(pick.placeId(), "CERTIFICATION_TAG", "LOCAL_SURVEY")).as(pick.name() + " 현지인 추천").isEqualTo(1);
			assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM place_feature WHERE place_id = ? AND feature_type = 'ADMIN_AREA_TAG'", Long.class,
					pick.placeId())).as(pick.name() + " 구·동은 하나").isEqualTo(1L);
		}

		// 현지인 추천의 값 — 추천한 사람 수는 아는 곳에만 있다(모르는 곳은 칸을 뺀다, 0 으로 채우지 않는다).
		Pick sample = eligible.stream().filter(pick -> pick.recommenders() != null && pick.recommenders() >= 2).findFirst().orElseThrow();
		Map<String, Object> survey = this.jdbcTemplate.queryForMap("""
				SELECT value ->> 'source' AS source, (value ->> 'recommenders')::int AS recommenders, evidence_status, source_type, source_id, source_version
				  FROM place_feature WHERE place_id = ? AND feature_type = 'CERTIFICATION_TAG' AND feature_key = 'LOCAL_SURVEY'
				""", sample.placeId());
		assertThat(survey.get("source")).isEqualTo("현지인 추천");
		assertThat(survey.get("recommenders")).isEqualTo(sample.recommenders());
		assertThat(survey.get("evidence_status")).isEqualTo("VERIFIED");
		assertThat(survey.get("source_type")).isEqualTo("LOCAL_SURVEY");
		assertThat(survey.get("source_id")).isEqualTo(sample.placeId().toString());
		assertThat(survey.get("source_version")).isEqualTo(VERSION);
		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT (value ->> 'recommenders') IS NOT NULL FROM place_feature
				 WHERE place_id = ? AND feature_type = 'CERTIFICATION_TAG' AND feature_key = 'LOCAL_SURVEY'
				""", Boolean.class, this.unknownRecommenders.placeId())).as("인원을 모르는 줄은 recommenders 칸이 없다").isFalse();

		// 구·동 — 값은 부분으로, 증거는 계산한 값이라 추정이다. 이미 구·동이 있던 곳은 파일이 달라도 먼저 것을 둔다.
		Pick plain = eligible.stream().filter(pick -> pick != this.foodWithMarks).findFirst().orElseThrow();
		assertThat(this.jdbcTemplate.queryForMap("""
				SELECT feature_key, value ->> 'gu' AS gu, value ->> 'dong' AS dong, evidence_status FROM place_feature
				 WHERE place_id = ? AND feature_type = 'ADMIN_AREA_TAG'
				""", plain.placeId())).containsEntry("feature_key", plain.gu() + "/" + plain.dong()).containsEntry("gu", plain.gu())
				.containsEntry("dong", plain.dong()).containsEntry("evidence_status", "ESTIMATED");
		assertThat(keys(this.foodWithMarks.placeId(), "ADMIN_AREA_TAG")).containsExactly("ZZ원래구/ZZ원래동");

		// 이미 맛집·인증 목록이 달려 있던 곳 — 중복 없이 현지인 추천만 더해진다.
		assertThat(keys(this.foodWithMarks.placeId(), "INTEREST_TAG")).containsExactly("LOCAL_RESTAURANT");
		assertThat(keys(this.foodWithMarks.placeId(), "CERTIFICATION_TAG")).containsExactlyInAnyOrder("MODEL_RESTAURANT", "LOCAL_SURVEY");

		// DB 에 없는 곳은 장소도 표식도 안 생기고, 추천에서 빠진 곳(숨김)에는 표식이 안 달린다 — 에러 없이 건너뛴다.
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM place WHERE place_id = ?", Long.class, this.missing.placeId())).isZero();
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM place_feature WHERE place_id IN (?, ?)", Long.class, this.missing.placeId(),
				this.hidden.placeId())).isZero();

		// 이 수집분이 단 현지인 추천은 달 수 있는 곳 수와 같다.
		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM place_feature p JOIN place pl ON pl.place_id = p.place_id
				 WHERE p.source_type = 'LOCAL_SURVEY' AND p.source_version = ? AND p.feature_key = 'LOCAL_SURVEY' AND pl.source_type = ?
				""", Long.class, VERSION, FAKE_SOURCE)).isEqualTo(45L);
	}

	@Test
	@DisplayName("🔴 맛집에 든 카페는 카페로 옮겨진다 — 열쇠만 바뀌고 표식 id·출처·수집분은 그대로이며, 카페 표식이 이미 있으면 맛집 표식만 지워지고, 음식점은 그대로다")
	void movesCafesOutOfRestaurantsAndKeepsRestaurants() {
		Map<String, Object> before = this.jdbcTemplate.queryForMap(
				"SELECT source_type, source_version, evidence_status, created_at FROM place_feature WHERE place_feature_id = ?", this.outsiderCafeMarkId);

		runSql();

		// 설문 밖의 카페 — 같은 행의 열쇠만 LOCAL_CAFE 로 바뀌었다.
		assertThat(keys(this.outsiderCafe, "INTEREST_TAG")).containsExactly("LOCAL_CAFE");
		Map<String, Object> after = this.jdbcTemplate.queryForMap(
				"SELECT feature_key, source_type, source_version, evidence_status, created_at FROM place_feature WHERE place_feature_id = ?",
				this.outsiderCafeMarkId);
		assertThat(after.get("feature_key")).isEqualTo("LOCAL_CAFE");
		assertThat(after.get("source_type")).isEqualTo(before.get("source_type")).isEqualTo("BUSAN_CERTIFIED_FOOD");
		assertThat(after.get("source_version")).isEqualTo(before.get("source_version"));
		assertThat(after.get("evidence_status")).isEqualTo(before.get("evidence_status"));
		assertThat(after.get("created_at")).isEqualTo(before.get("created_at"));

		// 설문에 든 카페 가운데 맛집이 달려 있던 곳 — 옮겨지고, 설문 단계가 카페 표식을 또 달지 않는다(하나뿐).
		assertThat(keys(this.cafeWithRestaurantMark.placeId(), "INTEREST_TAG")).containsExactly("LOCAL_CAFE");
		// 맛집·카페가 둘 다 있던 카페 — 맛집 표식만 지워진다(같은 표식이 둘이면 유일 색인에 걸린다).
		assertThat(keys(this.cafeWithBothMarks.placeId(), "INTEREST_TAG")).containsExactly("LOCAL_CAFE");

		// 장소 갈래가 음식인 곳은 그대로 맛집이고, 카페 갈래여도 맛집 표식이 없던 곳은 아무것도 안 달린다(설문 밖).
		assertThat(keys(this.outsiderFood, "INTEREST_TAG")).containsExactly("LOCAL_RESTAURANT");
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM place_feature WHERE place_id = ?", Long.class, this.outsiderUntagged)).isZero();
	}

	@Test
	@DisplayName("🔴 두 번 돌려도 같다 — 두 번째 실행은 한 행도 더하거나 바꾸거나 지우지 않는다")
	void runningTwiceChangesNothingTheSecondTime() {
		runSql();
		List<String> afterFirst = snapshot();
		assertThat(afterFirst).isNotEmpty();

		runSql();

		assertThat(snapshot()).isEqualTo(afterFirst);
	}

	@Test
	@DisplayName("🔴 표식 id 는 자바 UUID.nameUUIDFromBytes 와 같은 값이다 — 한글이 든 구/동 열쇠도 UTF-8 로 같다. 적재기와 같은 식이라 다음 적재가 같은 id 를 만든다")
	void markIdsMatchTheJavaNameBasedUuids() {
		runSql();

		Pick pick = this.picks.stream().filter(candidate -> candidate != this.missing && candidate != this.hidden
				&& candidate != this.foodWithMarks && candidate.category().equals("FOOD")).findFirst().orElseThrow();
		UUID placeId = pick.placeId();
		assertThat(idOf(placeId, "CERTIFICATION_TAG", "LOCAL_SURVEY"))
				.isEqualTo(nameUuid("gabolle:place_feature:LOCAL_SURVEY:" + placeId + ":CERTIFICATION_TAG:LOCAL_SURVEY"));
		assertThat(idOf(placeId, "INTEREST_TAG", "LOCAL_RESTAURANT"))
				.isEqualTo(nameUuid("gabolle:place_feature:LOCAL_SURVEY:" + placeId + ":INTEREST_TAG:LOCAL_RESTAURANT"));
		String areaKey = pick.gu() + "/" + pick.dong();
		assertThat(idOf(placeId, "ADMIN_AREA_TAG", areaKey))
				.isEqualTo(nameUuid("gabolle:place_feature:LOCAL_SURVEY:" + placeId + ":ADMIN_AREA_TAG:" + areaKey));
	}

	@Test
	@DisplayName("🔴 사전(place_feature_code)에 새 낱말이 없으면 아무것도 바꾸지 않고 멈춘다 — 배포가 먼저 나가야 한다")
	void stopsBeforeDeploymentIfTheDictionaryLacksTheNewWords() {
		// 사전의 두 낱말을 잠깐 뺀다. 이 시험이 끝나면 되돌린다 — 다른 시험이 같은 DB 를 쓴다.
		List<Map<String, Object>> removed = this.jdbcTemplate.queryForList("""
				SELECT feature_type, feature_key, label_ko, note FROM place_feature_code
				 WHERE (feature_type, feature_key) IN (('INTEREST_TAG', 'LOCAL_CAFE'), ('CERTIFICATION_TAG', 'LOCAL_SURVEY'))
				""");
		assertThat(removed).hasSize(2);
		this.jdbcTemplate.update("DELETE FROM place_feature_code WHERE (feature_type, feature_key) IN (('INTEREST_TAG', 'LOCAL_CAFE'), ('CERTIFICATION_TAG', 'LOCAL_SURVEY'))");
		try {
			assertThatThrownBy(this::runSql).hasMessageContaining("배포");
			// 한 트랜잭션이라 멈추면 아무 표식도 안 달렸다. (맛집에서 카페로 옮기는 단계도 첫 확인 뒤라 돌지 않았다.)
			assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM place_feature WHERE source_type = 'LOCAL_SURVEY' AND source_version = ?", Long.class,
					VERSION)).isZero();
			assertThat(keys(this.outsiderCafe, "INTEREST_TAG")).containsExactly("LOCAL_RESTAURANT");
		}
		finally {
			for (Map<String, Object> row : removed) {
				this.jdbcTemplate.update("INSERT INTO place_feature_code (feature_type, feature_key, label_ko, note) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
						row.get("feature_type"), row.get("feature_key"), row.get("label_ko"), row.get("note"));
			}
		}
	}

	// ── 도우미 ────────────────────────────────────────────────────────────

	/**
	 * 운영에서 돌릴 파일 그대로. 여러 문장·함수·DO 블록이 든 한 덩어리를 한 번에 보낸다. 파일이 `BEGIN` 으로 시작하므로 도중에 멈추면 이 연결은
	 * 「실패한 트랜잭션」으로 남는다 — 연결 풀에 그대로 돌려보내면 다음 시험이 「트랜잭션이 중단돼 명령을 무시한다」로 죽으므로 `ROLLBACK` 을 직접 보낸다
	 * (psql 은 `ON_ERROR_STOP` 으로 접속째 끝나서 이 걱정이 없다).
	 */
	private void runSql() {
		String sql;
		try {
			sql = Files.readString(SQL, StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new AssertionError(exception);
		}
		this.jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
			try (Statement statement = connection.createStatement()) {
				// 중괄호가 든 주석(JSON 모양 설명)을 JDBC 의 {fn …} 꼴로 읽지 않게 한다.
				statement.setEscapeProcessing(false);
				try {
					statement.execute(sql);
				}
				catch (SQLException exception) {
					try {
						statement.execute("ROLLBACK");
					}
					catch (SQLException ignored) {
						// 이미 끝난 트랜잭션이면 할 일이 없다.
					}
					throw exception;
				}
			}
			return null;
		});
	}

	private List<Pick> readPicks() throws IOException {
		JsonMapper mapper = JsonMapper.builder().build();
		List<Pick> read = new ArrayList<>();
		for (String line : Files.readAllLines(DATA, StandardCharsets.UTF_8)) {
			if (line.isBlank()) {
				continue;
			}
			JsonNode node = mapper.readTree(line);
			read.add(new Pick(UUID.fromString(node.path("placeId").asString()), node.path("name").asString(), node.path("category").asString(),
					node.path("gu").asString(), node.path("dong").asString(),
					node.path("recommenders").isNull() ? null : node.path("recommenders").asInt()));
		}
		return read;
	}

	/** 갈래가 같은 줄 가운데 {@code skip} 번째 — 이미 쓴 줄은 피한다. */
	private Pick firstOf(String category, Pick avoid, int skip) {
		return this.picks.stream().filter(pick -> pick.category().equals(category) && pick != avoid && pick.recommenders() != null)
				.skip(skip).findFirst().orElseThrow();
	}

	private void insertPlace(UUID placeId, String name, String category, String curationStatus) {
		this.jdbcTemplate.update("""
				INSERT INTO place (place_id, name_ko, category, address, lat, lng, created_at, source_type, source_id, collected_at, dataset_version, curation_status)
				VALUES (?, ?, ?, '부산광역시 시험로 1', 35.1, 129.1, now(), ?, ?, now(), 'zz2059-sql-test', ?)
				""", placeId, "ZZ2059-" + name, category, FAKE_SOURCE, placeId.toString(), curationStatus);
	}

	private void insertFeature(UUID id, UUID placeId, String type, String key, String valueJson, String evidence, String sourceType, String version) {
		this.jdbcTemplate.update("""
				INSERT INTO place_feature (place_feature_id, place_id, feature_type, feature_key, value, evidence_status, source_type, source_id, source_version, created_at)
				VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, 'zz2059', ?, now())
				""", id, placeId, type, key, valueJson, evidence, sourceType, version);
	}

	private long count(UUID placeId, String type, String key) {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM place_feature WHERE place_id = ? AND feature_type = ? AND feature_key = ?", Long.class,
				placeId, type, key);
	}

	private List<String> keys(UUID placeId, String type) {
		return this.jdbcTemplate.queryForList("SELECT feature_key FROM place_feature WHERE place_id = ? AND feature_type = ? ORDER BY feature_key", String.class,
				placeId, type);
	}

	private UUID idOf(UUID placeId, String type, String key) {
		return this.jdbcTemplate.queryForObject("SELECT place_feature_id FROM place_feature WHERE place_id = ? AND feature_type = ? AND feature_key = ?",
				UUID.class, placeId, type, key);
	}

	/** 이 시험이 만든 장소들의 표식 전부를 한 줄씩 — 두 번째 실행 뒤와 비교한다. */
	private List<String> snapshot() {
		return this.jdbcTemplate.queryForList("""
				SELECT f.place_feature_id || '|' || f.place_id || '|' || f.feature_type || '|' || f.feature_key || '|' || coalesce(f.value::text, '') || '|'
				       || f.evidence_status || '|' || coalesce(f.source_type, '') || '|' || coalesce(f.source_version, '') || '|' || f.created_at
				  FROM place_feature f JOIN place p ON p.place_id = f.place_id
				 WHERE p.source_type = ?
				 ORDER BY 1
				""", String.class, FAKE_SOURCE);
	}

	private static UUID nameUuid(String seed) {
		return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
	}

	private void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE place_id IN (SELECT place_id FROM place WHERE source_type = ?)", FAKE_SOURCE);
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = ?", FAKE_SOURCE);
	}
}
