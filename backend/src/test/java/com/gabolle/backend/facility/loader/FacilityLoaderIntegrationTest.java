package com.gabolle.backend.facility.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.facility.domain.Facility;
import com.gabolle.backend.facility.domain.FacilityKind;
import com.gabolle.backend.facility.repository.FacilityRepository;
import com.gabolle.backend.facility.support.FacilityPostgresIntegrationTest;

/**
 * 편의시설이 실제 DB 에 들어가는지 (S15P21E201-2052). 가짜 DB 가 아니라 진짜 PostgreSQL 이라 마이그레이션이 만든 허용 목록(CHECK)·유일
 * 제약·JSONB 가 그대로 적용된다. 핵심은 <b>멱등</b>이다 — 같은 파일을 다시 돌려도 행이 안 늘고, 값이 같으면 DB 에 아무 일도 하지 않는다.
 */
class FacilityLoaderIntegrationTest extends FacilityPostgresIntegrationTest {

	private static final String DATASET = "facility-loader-test";

	/** 이 시험이 넣는 번호의 머리 — 실제 번호와 안 겹친다. */
	private static final String ID = "ZZ2052-L-";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private FacilityLoader loader;

	@Autowired
	private FacilityRepository repository;

	@BeforeEach
	void cleanBefore() {
		deleteWhatThisTestWrote();
	}

	@AfterEach
	void cleanAfter() {
		deleteWhatThisTestWrote();
	}

	private void deleteWhatThisTestWrote() {
		this.jdbcTemplate.update("DELETE FROM facility WHERE source_id LIKE ?", ID + "%");
	}

	private static Map<String, Object> mapOf(Object... keyAndValue) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (int i = 0; i < keyAndValue.length; i += 2) {
			map.put((String) keyAndValue[i], keyAndValue[i + 1]);
		}
		return map;
	}

	private static FacilityRow toilet(String suffix, String name, boolean accessible) {
		return new FacilityRow(FacilityKind.TOILET, ID + suffix, name, "부산광역시 시험구 " + suffix, 33.2001, 126.5001,
				mapOf("type", "PUBLIC", "openHours", "상시", "alwaysOpen", true, "accessible", accessible, "diaper", false,
						"diaperPlace", null, "emergencyBell", false, "cctv", false, "tel", null));
	}

	private static FacilityRow parking(String suffix) {
		Map<String, Object> hours = new LinkedHashMap<>();
		hours.put("Monday", mapOf("open", "09:00", "close", "26:00"));
		hours.put("Sunday", null);
		Map<String, Object> fee = mapOf("baseMinutes", 30, "baseWon", 300, "addMinutes", 10, "addWon", null, "dayMaxWon",
				2400, "freeMinutes", 0);
		return new FacilityRow(FacilityKind.PARKING, ID + suffix, "시험주차장", null, 33.2002, 126.5002,
				mapOf("operator", "PUBLIC", "free", false, "spaces", 12, "fee", fee, "hours", hours, "note", null));
	}

	private long count() {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM facility WHERE source_id LIKE ?", Long.class, ID + "%");
	}

	private OffsetDateTime collectedAt(String suffix, FacilityKind kind) {
		return this.jdbcTemplate.queryForObject(
				"SELECT collected_at FROM facility WHERE kind = ? AND source_id = ?", OffsetDateTime.class, kind.name(),
				ID + suffix);
	}

	// ── 멱등 ────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 두 번 돌려도 행이 안 늘고, 값이 같으면 DB 를 건드리지 않는다 — 수집 시각도 그대로다")
	void secondRunChangesNothing() {
		List<FacilityRow> rows = List.of(toilet("a", "가화장실", true), toilet("b", "나화장실", false), toilet("c", "다화장실", false));
		OffsetDateTime firstTime = OffsetDateTime.parse("2026-10-14T00:00:00Z");

		FacilityLoader.Result first = this.loader.saveChunk(rows, DATASET, firstTime);
		assertThat(first).isEqualTo(new FacilityLoader.Result(3, 0, 0));
		assertThat(count()).isEqualTo(3);

		FacilityLoader.Result second = this.loader.saveChunk(rows, DATASET, firstTime.plusHours(5));
		assertThat(second).isEqualTo(new FacilityLoader.Result(0, 0, 3));
		assertThat(count()).isEqualTo(3);
		// 갱신이 한 줄도 안 나갔으므로 수집 시각이 처음 그대로다
		assertThat(collectedAt("a", FacilityKind.TOILET).toInstant()).isEqualTo(firstTime.toInstant());
	}

	@Test
	@DisplayName("JSONB 로 돌아온 값이 키 순서가 달라도 «같은 값»으로 알아본다 — 요금표·시간표가 든 주차장도 두 번째는 그대로")
	void jsonbKeyOrderDoesNotMakeRowsLookChanged() {
		List<FacilityRow> rows = List.of(parking("p"));
		OffsetDateTime time = OffsetDateTime.parse("2026-10-14T00:00:00Z");
		this.loader.saveChunk(rows, DATASET, time);

		FacilityLoader.Result again = this.loader.saveChunk(rows, DATASET, time.plusDays(1));

		assertThat(again).isEqualTo(new FacilityLoader.Result(0, 0, 1));
	}

	@Test
	@DisplayName("값이 바뀐 행만 고친다 — 이름과 안쪽 값, 수집 시각이 새로 찍히고 행 수는 그대로")
	void changedRowsAreUpdatedInPlace() {
		OffsetDateTime time = OffsetDateTime.parse("2026-10-14T00:00:00Z");
		this.loader.saveChunk(List.of(toilet("a", "가화장실", false), toilet("b", "나화장실", false)), DATASET, time);

		FacilityLoader.Result second = this.loader.saveChunk(
				List.of(toilet("a", "가화장실(새 이름)", true), toilet("b", "나화장실", false)), DATASET, time.plusDays(1));

		assertThat(second).isEqualTo(new FacilityLoader.Result(0, 1, 1));
		assertThat(count()).isEqualTo(2);
		Map<String, Object> changed = this.jdbcTemplate.queryForMap(
				"SELECT name, (detail ->> 'accessible')::boolean AS accessible, collected_at FROM facility WHERE source_id = ?",
				ID + "a");
		assertThat(changed.get("name")).isEqualTo("가화장실(새 이름)");
		assertThat(changed.get("accessible")).isEqualTo(true);
		assertThat(collectedAt("a", FacilityKind.TOILET).toInstant()).isEqualTo(time.plusDays(1).toInstant());
		assertThat(collectedAt("b", FacilityKind.TOILET).toInstant()).isEqualTo(time.toInstant());
	}

	@Test
	@DisplayName("값이 같아도 수집분 이름이 바뀌면 고친다 — 어느 수집분의 행인지가 따라가야 되돌릴 수 있다")
	void newDatasetVersionIsRecorded() {
		OffsetDateTime time = OffsetDateTime.parse("2026-10-14T00:00:00Z");
		List<FacilityRow> rows = List.of(toilet("a", "가화장실", true));
		this.loader.saveChunk(rows, DATASET, time);

		FacilityLoader.Result next = this.loader.saveChunk(rows, DATASET + "-2", time.plusDays(1));

		assertThat(next).isEqualTo(new FacilityLoader.Result(0, 1, 0));
		assertThat(this.jdbcTemplate.queryForObject("SELECT dataset_version FROM facility WHERE source_id = ?", String.class,
				ID + "a")).isEqualTo(DATASET + "-2");
	}

	@Test
	@DisplayName("🔴 새 파일에서 사라진 행은 지우지 않고 그대로 둔다")
	void rowsMissingFromTheNewFileAreKept() {
		OffsetDateTime time = OffsetDateTime.parse("2026-10-14T00:00:00Z");
		this.loader.saveChunk(List.of(toilet("a", "가화장실", true), toilet("b", "나화장실", false)), DATASET, time);

		this.loader.saveChunk(List.of(toilet("a", "가화장실", true)), DATASET + "-2", time.plusDays(1));

		assertThat(count()).isEqualTo(2);
		assertThat(this.jdbcTemplate.queryForObject("SELECT dataset_version FROM facility WHERE source_id = ?", String.class,
				ID + "b")).isEqualTo(DATASET);
	}

	@Test
	@DisplayName("같은 원천 번호라도 종류가 다르면 다른 행이다 — 유일한 것은 (종류, 원천 번호) 쌍")
	void sameSourceIdInTwoKindsAreTwoRows() {
		OffsetDateTime time = OffsetDateTime.parse("2026-10-14T00:00:00Z");
		this.loader.saveChunk(List.of(toilet("same", "화장실", true)), DATASET, time);
		FacilityRow locker = new FacilityRow(FacilityKind.LOCKER, ID + "same", "시험역", null, 33.2003, 126.5003,
				mapOf("station", "시험", "small", 1));

		this.loader.saveChunk(List.of(locker), DATASET, time);

		assertThat(count()).isEqualTo(2);
	}

	@Test
	@DisplayName("한 덩어리에 종류가 섞이면 거절한다 — 덩어리는 종류별로 나뉘어 온다")
	void mixedKindsInOneChunkAreRejected() {
		FacilityRow locker = new FacilityRow(FacilityKind.LOCKER, ID + "x", "시험역", null, 33.2, 126.5, mapOf("station", "시험"));

		assertThatThrownBy(() -> this.loader.saveChunk(List.of(toilet("a", "가", true), locker), DATASET,
				OffsetDateTime.now())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("섞였다");
		assertThat(count()).isZero();
	}

	@Test
	@DisplayName("빈 덩어리는 아무 일도 하지 않는다")
	void emptyChunk() {
		assertThat(this.loader.saveChunk(List.of(), DATASET, OffsetDateTime.now())).isEqualTo(FacilityLoader.Result.empty());
	}

	// ── 저장 모양 ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("행 번호는 (종류, 원천 번호)에서 언제나 같다 — 지웠다 다시 넣어도 앱이 들고 있는 번호가 그대로")
	void idIsDeterministic() {
		this.loader.saveChunk(List.of(toilet("a", "가화장실", true)), DATASET, OffsetDateTime.now());

		UUID stored = this.jdbcTemplate.queryForObject("SELECT facility_id FROM facility WHERE source_id = ?", UUID.class,
				ID + "a");

		assertThat(stored).isEqualTo(Facility.idOf(FacilityKind.TOILET, ID + "a"));
		assertThat(Facility.idOf(FacilityKind.TOILET, "x")).isNotEqualTo(Facility.idOf(FacilityKind.LOCKER, "x"));
	}

	@Test
	@DisplayName("🔴 detail 은 JSONB 객체로 들어간다 — 모르는 값은 JSON null 로 남고(칸이 사라지지 않고), 시간표의 모르는 날도 null")
	void detailIsJsonbWithExplicitNulls() {
		this.loader.saveChunk(List.of(parking("p")), DATASET, OffsetDateTime.now());

		Map<String, Object> stored = this.jdbcTemplate.queryForMap("""
				SELECT jsonb_typeof(detail)                               AS detail_type,
				       detail ->> 'operator'                              AS operator,
				       jsonb_typeof(detail -> 'note')                     AS note_type,
				       jsonb_typeof(detail -> 'fee' -> 'addWon')          AS add_won_type,
				       (detail -> 'fee' ->> 'baseWon')::int               AS base_won,
				       jsonb_typeof(detail -> 'hours' -> 'Sunday')        AS sunday_type,
				       detail -> 'hours' -> 'Monday' ->> 'close'          AS monday_close
				  FROM facility WHERE source_id = ?
				""", ID + "p");
		assertThat(stored.get("detail_type")).isEqualTo("object");
		assertThat(stored.get("operator")).isEqualTo("PUBLIC");
		assertThat(stored.get("note_type")).isEqualTo("null");
		assertThat(stored.get("add_won_type")).isEqualTo("null");
		assertThat(stored.get("base_won")).isEqualTo(300);
		assertThat(stored.get("sunday_type")).isEqualTo("null");
		assertThat(stored.get("monday_close")).isEqualTo("26:00");
	}

	@Test
	@DisplayName("저장소로 다시 읽으면 넣은 값 그대로다 — 종류·좌표·수집분")
	void roundTripThroughTheRepository() {
		OffsetDateTime time = OffsetDateTime.parse("2026-10-14T00:00:00Z");
		this.loader.saveChunk(List.of(parking("p")), DATASET, time);

		Facility read = this.repository.findByKindAndSourceIdIn(FacilityKind.PARKING, List.of(ID + "p")).get(0);

		assertThat(read.getKind()).isEqualTo(FacilityKind.PARKING);
		assertThat(read.getName()).isEqualTo("시험주차장");
		assertThat(read.getAddress()).isNull();
		assertThat(read.getLat()).isEqualTo(33.2002);
		assertThat(read.getLng()).isEqualTo(126.5002);
		assertThat(read.getDatasetVersion()).isEqualTo(DATASET);
		assertThat(read.getDetail()).containsEntry("operator", "PUBLIC").containsEntry("spaces", 12);
	}

	// ── DB 제약 ─────────────────────────────────────────────────────────────

	private void insertRaw(String kind, String sourceId, double lat, String detailJson) {
		this.jdbcTemplate.update("""
				INSERT INTO facility (facility_id, kind, source_id, name, lat, lng, detail, dataset_version, collected_at)
				VALUES (?, ?, ?, '시험', ?, 126.5, ?::jsonb, ?, now())
				""", UUID.randomUUID(), kind, sourceId, lat, detailJson, DATASET);
	}

	@Test
	@DisplayName("🔴 DB 가 막는다 — 모르는 종류 · 같은 (종류, 원천 번호) · 범위 밖 위도 · 객체가 아닌 detail")
	void databaseConstraints() {
		insertRaw("TOILET", ID + "ok", 33.2, "{}");

		assertThatThrownBy(() -> insertRaw("PLAYGROUND", ID + "k", 33.2, "{}")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertRaw("TOILET", ID + "ok", 33.2, "{}")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertRaw("TOILET", ID + "lat", 91.0, "{}")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertRaw("TOILET", ID + "arr", 33.2, "[]")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertRaw("TOILET", ID + "str", 33.2, "\"글자\"")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("근처 조회가 타는 색인과 유일 제약이 있다")
	void indexesExist() {
		List<String> indexes = this.jdbcTemplate.queryForList(
				"SELECT indexname FROM pg_indexes WHERE tablename = 'facility' ORDER BY indexname", String.class);

		assertThat(indexes).contains("ix_facility_kind_coords", "uq_facility_kind_source", "pk_facility");
	}
}
