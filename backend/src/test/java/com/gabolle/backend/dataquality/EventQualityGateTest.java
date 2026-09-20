package com.gabolle.backend.dataquality;

import java.time.Clock;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.common.privacy.SensitivePayloadGuard;
import com.gabolle.backend.recommendation.support.PersonalizationFixture;
import com.gabolle.backend.recommendation.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 품질 게이트가 정상 fixture 는 통과시키고 오염 fixture 는 막는가. 여기서 던지는 예외가
 * Gradle 의 0 아닌 종료 코드가 된다.
 *
 * <p>오염을 한 번에 하나씩 넣는다. 두 개를 같이 넣으면 게이트가 막았다는 사실만 알 수 있고
 * 어느 규칙이 잡았는지는 모른다 — 규칙 하나가 죽어 있어도 초록이 된다.
 *
 * <p>{@code dataquality} 패키지는 테스트 슬라이스의 스캔 범위 밖이라 게이트를 직접 들인다 —
 * 슬라이스 파일은 여러 갈래가 함께 쓰는 파일이라 고치지 않는다.
 */
@Import(EventQualityGate.class)
class EventQualityGateTest extends PostgresIntegrationTest {

	private static final String DATASET = "dataset-2026-09-03";

	/** 설정된 schema 를 실제로 보는지 확인할 때 쓰는, 비어 있지만 완전한 schema. */
	private static final String PROBE_SCHEMA = "probe";

	@Autowired
	private EventQualityGate gate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SensitivePayloadGuard guard;

	@Autowired
	private Clock clock;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private PersonalizationFixture.Ids references;

	private UUID requestId;

	@BeforeEach
	void clean() {
		this.jdbcTemplate.update("DELETE FROM event_quality_report");
		this.jdbcTemplate.update("DELETE FROM event_outbox");
		this.jdbcTemplate.update("DELETE FROM recommendation_candidate");
		this.jdbcTemplate.update("DELETE FROM recommendation_job");
		this.references = PersonalizationFixture.insert(this.jdbcTemplate);
		this.requestId = UUID.randomUUID();
		insertJob(this.requestId);
	}

	// ── 정상 ──────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("정상 fixture — 후보와 노출이 requestId + placeId 로 이어지고 게이트가 통과한다")
	void cleanFixturePasses() {
		UUID placeId = UUID.randomUUID();
		insertCandidate(placeId, "PASS", 1, true);
		insertImpression(placeId, 1, fullVersions());

		EventQualityReport report = this.gate.gate(DATASET);

		assertThat(report.passed()).isTrue();
		assertThat(report.violations()).isEmpty();
		assertThat(report.impressionsTotal()).isEqualTo(1);
		assertThat(report.orphanImpressions()).isZero();
		// 노출이 하나뿐이면 중복률은 0 이어야 한다 — 빈 분모를 1.0 으로 두던 버그가 여기서 잡혔다.
		assertThat(report.duplicateRate()).isZero();
		assertThat(report.schemaValidRate()).isEqualTo(1.0);
	}

	@Test
	@DisplayName("판정 결과가 일자·datasetVersion 별로 남고, 다시 재면 덮어쓴다")
	void reportIsStoredPerDateAndDataset() {
		UUID placeId = UUID.randomUUID();
		insertCandidate(placeId, "PASS", 1, true);
		insertImpression(placeId, 1, fullVersions());

		this.gate.gate(DATASET);
		this.gate.gate(DATASET);

		Integer rows = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM event_quality_report WHERE dataset_version = ?", Integer.class, DATASET);
		assertThat(rows).isEqualTo(1);

		Boolean passed = this.jdbcTemplate.queryForObject(
				"SELECT passed FROM event_quality_report WHERE dataset_version = ?", Boolean.class, DATASET);
		assertThat(passed).isTrue();
	}

	// ── 오염 — 하나씩 ─────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 존재하지 않는 후보의 노출이 오면 실패한다 — 축이 끊긴 상태의 가장 직접적인 증거")
	void orphanImpressionFails() {
		insertCandidate(UUID.randomUUID(), "PASS", 1, true);
		insertImpression(UUID.randomUUID(), 1, fullVersions()); // 후보에 없는 장소

		assertThatThrownBy(() -> this.gate.gate(DATASET))
				.isInstanceOf(EventQualityGateFailedException.class)
				.hasMessageContaining("존재하지 않는 후보의 노출");

		assertThat(this.gate.measure(DATASET).orphanImpressions()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 노출 순위와 후보 순위가 다르면 실패한다")
	void rankMismatchFails() {
		UUID placeId = UUID.randomUUID();
		insertCandidate(placeId, "PASS", 1, true);
		insertImpression(placeId, 7, fullVersions()); // 후보는 1위인데 화면은 7위라고 보냈다

		assertThatThrownBy(() -> this.gate.gate(DATASET))
				.isInstanceOf(EventQualityGateFailedException.class)
				.hasMessageContaining("순위 불일치");
	}

	@Test
	@DisplayName("🔴 requestId 가 없는 노출은 조용히 통과하지 않는다 (완료 기준)")
	void missingRequestIdFails() {
		UUID placeId = UUID.randomUUID();
		insertCandidate(placeId, "PASS", 1, true);
		insertImpressionWithoutRequestAxis(placeId, 1);

		assertThatThrownBy(() -> this.gate.gate(DATASET))
				.isInstanceOf(EventQualityGateFailedException.class)
				.hasMessageContaining("requestId 없는 노출");
	}

	@Test
	@DisplayName("🔴 버전이 빠진 노출은 조용히 통과하지 않는다 (FR-REC-12, 완료 기준)")
	void missingVersionFails() {
		UUID placeId = UUID.randomUUID();
		insertCandidate(placeId, "PASS", 1, true);
		// datasetVersion 이 없다. fallbackMode 도 없으므로 MODEL 로 보고 전부 요구한다.
		insertImpression(placeId, 1, """
				"policyVersion": "p1", "ontologyVersion": "o1",
				"modelVersion": "m1", "featureVersion": "f1"
				""");

		assertThatThrownBy(() -> this.gate.gate(DATASET))
				.isInstanceOf(EventQualityGateFailedException.class)
				.hasMessageContaining("버전 누락");
	}

	@Test
	@DisplayName("🔴 fallbackMode=BASELINE 은 모델 버전이 없어도 통과한다 — 그건 결함이 아니라 사실이다")
	void baselineWithoutModelVersionPasses() {
		UUID placeId = UUID.randomUUID();
		insertCandidate(placeId, "PASS", 1, true);
		insertImpression(placeId, 1, """
				"fallbackMode": "BASELINE", "datasetVersion": "d1", "policyVersion": "p1"
				""");

		EventQualityReport report = this.gate.gate(DATASET);

		assertThat(report.passed()).isTrue();
		assertThat(report.versionMissing()).isZero();
	}

	@Test
	@DisplayName("🔴 하드 제약을 위반한(FAIL) 후보가 노출되면 실패한다 — 데이터 문제가 아니라 안전 문제")
	void exposedFailVerdictFails() {
		UUID placeId = UUID.randomUUID();
		// DB CHECK 때문에 FAIL 후보는 returned=true 가 될 수 없다. 그런데 노출 이벤트는
		// 클라이언트가 만들어 보내므로 그 CHECK 를 지나지 않는다 — 그 틈을 재현한다.
		insertCandidate(placeId, "FAIL", null, false);
		insertImpression(placeId, 1, fullVersions());

		assertThatThrownBy(() -> this.gate.gate(DATASET))
				.isInstanceOf(EventQualityGateFailedException.class)
				.hasMessageContaining("하드 제약 위반");
	}

	@Test
	@DisplayName("🔴 payload 에 정밀 좌표가 들어오면 실패한다 (DR-13)")
	void preciseCoordinateInPayloadFails() {
		UUID placeId = UUID.randomUUID();
		insertCandidate(placeId, "PASS", 1, true);
		insertImpression(placeId, 1, fullVersions() + """
				, "currentLat": 35.1587, "currentLng": 129.1604
				""");

		assertThatThrownBy(() -> this.gate.gate(DATASET))
				.isInstanceOf(EventQualityGateFailedException.class)
				.hasMessageContaining("개인정보");
	}

	@Test
	@DisplayName("같은 (요청, 장소)가 서로 다른 eventId 로 두 번 오면 중복률에 잡힌다")
	void logicalDuplicateShowsInDuplicateRate() {
		UUID placeId = UUID.randomUUID();
		insertCandidate(placeId, "PASS", 1, true);
		insertImpression(placeId, 1, fullVersions());
		insertImpression(placeId, 1, fullVersions()); // eventId 는 다르고 (요청, 장소)는 같다

		EventQualityReport report = this.gate.measure(DATASET);

		assertThat(report.impressionsTotal()).isEqualTo(2);
		assertThat(report.duplicateRate()).isEqualTo(0.5);
	}

	@Test
	@DisplayName("같은 eventId 를 두 번 적으려 하면 DB 가 막는다 — 멱등 키가 PK 다 (완료 기준)")
	void sameEventIdIsRejectedByDatabase() {
		UUID placeId = UUID.randomUUID();
		insertCandidate(placeId, "PASS", 1, true);
		UUID eventId = UUID.randomUUID();

		insertImpressionWithId(eventId, placeId, 1, fullVersions());

		assertThatThrownBy(() -> insertImpressionWithId(eventId, placeId, 1, fullVersions()))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 위반이 있는데 passed=true 인 리포트는 DB 가 거부한다 — 게이트가 실수해도 리포트는 거짓말을 못 한다")
	void databaseRefusesPassedReportWithViolations() {
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO event_quality_report (
				    report_id, report_date, dataset_version,
				    events_total, impressions_total, candidates_total,
				    schema_valid_rate, duplicate_rate, request_id_missing_rate,
				    candidate_feature_missing_rate,
				    orphan_impressions, rank_mismatches, version_missing, pii_violations,
				    fail_verdict_exposed, passed, created_at)
				VALUES (?, current_date, 'lying-report', 1, 1, 1, 1.0, 0, 0, 0,
				        3, 0, 0, 0, 0, TRUE, now())
				""", UUID.randomUUID()))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	// ── schema 분리 ────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 설정된 schema 의 표를 읽는다 — 운영에서 gabolle 대신 public 을 보던 결함")
	void honorsConfiguredSchema() {
		UUID placeId = UUID.randomUUID();
		insertCandidate(placeId, "PASS", 1, true);
		insertImpression(placeId, 1, fullVersions());

		// schema 설정이 없으면 연결의 기본 search_path 를 그대로 쓴다. 그 기본값
		// `"$user", public` 은 public 이라는 뜻이 아니다 — 접속 사용자와 같은 이름의 schema 가
		// 있으면 그쪽이 먼저다. 어느 쪽이든 표가 있는 곳을 본다.
		assertThat(this.gate.measure(DATASET).eventsTotal()).isEqualTo(1);

		// probe schema 를 진짜 마이그레이션으로 통째로 만든다. 표는 다 있고 행은 없다.
		// search_path 가 실제로 바뀌면 이 빈 표들을 읽어 0 이 나오고, 안 바뀌면 원래 schema 를
		// 읽어 1 이 나온다. 그 차이가 이 테스트의 전부다.
		//
		// 표 하나만 손으로 만들면 게이트가 함께 읽는 나머지가 폴백 경로 public 으로 풀려,
		// "나머지 표가 전부 public 에 있다" 는 적어 두지 않은 전제 위에 서게 된다. 같은 DB 에
		// gabolle schema 가 생기면 그 전제가 깨진다.
		//
		// 먼저 지운다. 시험용 DB 는 실행 사이에 살아남고, 앞선 실행이 남긴 probe 가 있으면
		// Flyway 가 「이력표 없는 안 빈 schema」라고 거부한다.
		this.jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + PROBE_SCHEMA + " CASCADE");

		Flyway.configure()
			.dataSource(this.jdbcTemplate.getDataSource())
			.schemas(PROBE_SCHEMA)
			.defaultSchema(PROBE_SCHEMA)
			.locations("classpath:db/migration")
			.load()
			.migrate();

		EventQualityGate scoped = new EventQualityGate(this.jdbcTemplate, this.guard, this.clock, PROBE_SCHEMA);

		// SET LOCAL 은 트랜잭션 안에서만 듣는다. 직접 만든 객체는 프록시를 안 거쳐 트랜잭션이
		// 없으므로 여기서 손으로 하나 열어 준다.
		long eventsInProbe = new TransactionTemplate(this.transactionManager)
				.execute((status) -> scoped.measure("probe-" + DATASET).eventsTotal());

		assertThat(eventsInProbe).isZero();
	}

	@Test
	@DisplayName("schema 이름에 쓸 수 없는 값이 설정되면 조용히 넘기지 않고 던진다")
	void rejectsUnsafeSchemaName() {
		EventQualityGate unsafe = new EventQualityGate(this.jdbcTemplate, this.guard, this.clock,
				"gabolle; DROP SCHEMA public CASCADE");

		assertThatThrownBy(() -> unsafe.measure(DATASET))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("schema 이름으로 쓸 수 없는 값");
	}

	// ── 넣는 도구들 ───────────────────────────────────────────────────────────

	private String fullVersions() {
		return """
				"fallbackMode": "MODEL", "modelVersion": "m1", "featureVersion": "f1",
				"ontologyVersion": "o1", "datasetVersion": "d1", "policyVersion": "p1"
				""";
	}

	private void insertJob(UUID requestId) {
		// ck_recommendation_job_result_present — SUCCEEDED 인 ITINERARY_GENERATION Job 은
		// itinerary_id·itinerary_version 이 있어야 한다. 최소 일정 하나를 함께 만든다.
		UUID itineraryId = insertItinerary(this.references.tripId(), this.references.userId());

		this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, trip_id, trip_version, job_type, job_status,
				    preference_snapshot_id, constraint_snapshot_id, itinerary_id, itinerary_version,
				    model_version, feature_version, ontology_version, policy_version, dataset_version,
				    service_version, deployment_environment, created_at)
				VALUES (?, ?, ?, ?, ?, 'ITINERARY_GENERATION', 'SUCCEEDED', ?, ?, ?, 1,
				        'm1', 'f1', 'o1', 'p1', 'd1', 's1', 'test', now())
				""", UUID.randomUUID(), requestId, this.references.userId(), this.references.tripId(),
				this.references.tripVersion(), this.references.preferenceSnapshotId(),
				this.references.constraintSnapshotId(), itineraryId);
	}

	/** {@code recommendation_job.itinerary_id} 의 FK 대상 — 판 1의 최소 일정 하나. */
	private UUID insertItinerary(UUID tripId, UUID createdBy) {
		UUID itineraryId = UUID.randomUUID();
		this.jdbcTemplate.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, now())",
				itineraryId, tripId);
		this.jdbcTemplate.update("""
				INSERT INTO itinerary_versions
				    (itinerary_version_id, itinerary_id, version, operation, created_by, request_id, created_at)
				VALUES (?, ?, 1, 'CREATE', ?, ?, now())
				""", UUID.randomUUID(), itineraryId, createdBy, "req_" + UUID.randomUUID());
		return itineraryId;
	}

	private void insertCandidate(UUID placeId, String verdict, Integer finalRank, boolean returned) {
		this.jdbcTemplate.update("""
				INSERT INTO recommendation_candidate (
				    candidate_id, request_id, place_id, candidate_source, candidate_stage,
				    eligible, constraint_verdict, feature_values,
				    final_score, final_rank, returned, created_at)
				VALUES (?, ?, ?, 'ONTOLOGY_SEED', ?, ?, ?, '{"walkMinutes": 12}'::jsonb, ?, ?, ?, now())
				""", UUID.randomUUID(), this.requestId, placeId, returned ? "RETURNED" : "HARD_FILTERED",
				"PASS".equals(verdict), verdict, returned ? 0.9 : null, finalRank, returned);
	}

	private void insertImpression(UUID placeId, int finalRank, String versionsJson) {
		insertImpressionWithId(UUID.randomUUID(), placeId, finalRank, versionsJson);
	}

	private void insertImpressionWithId(UUID eventId, UUID placeId, int finalRank, String versionsJson) {
		String payload = "{\"placeId\": \"" + placeId + "\", \"finalRank\": " + finalRank
				+ ", \"sourceScreen\": \"S-09\", " + versionsJson.strip() + "}";
		this.jdbcTemplate.update("""
				INSERT INTO event_outbox (
				    event_id, event_type, event_version, aggregate_type, aggregate_id,
				    partition_key, payload, occurred_at, received_at, producer, user_id, trip_id)
				VALUES (?, 'RECOMMENDATION_IMPRESSION', 1, 'RECOMMENDATION', ?, ?, CAST(? AS jsonb),
				        now(), now(), 'CLIENT', ?, ?)
				""", eventId, this.requestId, this.requestId.toString(), payload, this.references.userId(),
				this.references.tripId());
	}

	/** 요청 축이 없는 노출 — aggregate_type 이 추천이 아니고 request_id 도 비었다. */
	private void insertImpressionWithoutRequestAxis(UUID placeId, int finalRank) {
		String payload = "{\"placeId\": \"" + placeId + "\", \"finalRank\": " + finalRank + ", "
				+ fullVersions().strip() + "}";
		this.jdbcTemplate.update("""
				INSERT INTO event_outbox (
				    event_id, event_type, event_version, aggregate_type, aggregate_id,
				    partition_key, payload, occurred_at, received_at, producer)
				VALUES (?, 'RECOMMENDATION_IMPRESSION', 1, 'PLACE', ?, 'p', CAST(? AS jsonb),
				        now(), now(), 'CLIENT')
				""", UUID.randomUUID(), placeId, payload);
	}
}
