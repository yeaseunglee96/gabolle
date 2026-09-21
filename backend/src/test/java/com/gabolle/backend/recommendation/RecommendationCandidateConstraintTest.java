package com.gabolle.backend.recommendation;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.recommendation.domain.CandidateStage;
import com.gabolle.backend.recommendation.domain.ConstraintVerdict;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.support.PersonalizationFixture;
import com.gabolle.backend.recommendation.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 애플리케이션을 우회해도 DB 가 막는가. 일부러 JPA 가 아니라 원시 SQL 로 넣는다 — 엔티티의
 * 자바 검사를 건너뛰어야 DB 제약 자체를 확인할 수 있고, 자바 검사만 있으면 배치 작업이나
 * 수동 SQL 이 그 옆으로 걸어 들어온다.
 */
class RecommendationCandidateConstraintTest extends PostgresIntegrationTest {

	private static final String INSERT_CANDIDATE = """
			INSERT INTO recommendation_candidate (
			    candidate_id, request_id, place_id, candidate_source, candidate_stage,
			    eligible, constraint_verdict, constraint_confidence,
			    final_score, final_rank, returned, created_at)
			VALUES (?, ?, ?, 'ONTOLOGY_SEED', ?, ?, ?, 1.0, ?, ?, ?, now())
			""";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private UUID requestId;

	private PersonalizationFixture.Ids references;

	@BeforeEach
	void insertJob() {
		this.jdbcTemplate.update("DELETE FROM recommendation_candidate");
		this.jdbcTemplate.update("DELETE FROM recommendation_job");
		// 외래키가 붙어 있어 사용자·여행·스냅샷은 실제 행이어야 한다.
		this.references = PersonalizationFixture.insert(this.jdbcTemplate);
		this.requestId = UUID.randomUUID();
		insertJob(this.requestId);
	}

	private void insertJob(UUID requestId) {
		// ck_recommendation_job_result_present 때문에 SUCCEEDED 인 ITINERARY_GENERATION Job 은
		// itinerary_id·itinerary_version 이 있어야 한다. 최소 일정 하나를 함께 만든다.
		UUID itineraryId = insertItinerary(this.references.tripId(), this.references.userId());

		this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, job_type, job_status,
				    preference_snapshot_id, constraint_snapshot_id, itinerary_id, itinerary_version,
				    model_version, feature_version, ontology_version, policy_version, dataset_version,
				    service_version, deployment_environment, created_at)
				VALUES (?, ?, ?, 'ITINERARY_GENERATION', 'SUCCEEDED', ?, ?, ?, 1,
				        'm', 'f', 'o', 'p', 'd', 's', 'test', now())
				""", UUID.randomUUID(), requestId, this.references.userId(),
				this.references.preferenceSnapshotId(), this.references.constraintSnapshotId(), itineraryId);
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

	@Test
	@DisplayName("🔴 성공한 Job 에 입력 스냅샷이 없으면 DB 가 거부한다")
	void databaseRefusesSucceededJobWithoutInputSnapshots() {
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, job_type, job_status,
				    model_version, feature_version, ontology_version, policy_version, dataset_version,
				    service_version, deployment_environment, created_at)
				VALUES (?, ?, ?, 'ITINERARY_GENERATION', 'SUCCEEDED',
				        'm', 'f', 'o', 'p', 'd', 's', 'test', now())
				""", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);

		// 실패한 Job 은 스냅샷 없이도 남을 수 있다 — 스냅샷을 못 구해 실패했을 수도 있다.
		assertThatCode(() -> this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, job_type, job_status, error_code, created_at)
				VALUES (?, ?, ?, 'ITINERARY_GENERATION', 'FAILED', 'ENGINE_NOT_CONFIGURED', now())
				""", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 FAIL 후보를 returned=true 로 밀어 넣으면 DB 가 거부한다")
	void databaseRefusesReturnedFailingCandidate() {
		assertThatThrownBy(() -> insertCandidate(UUID.randomUUID(), "RETURNED", false,
				ConstraintVerdict.FAIL, 0.9, 1, true))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 FAIL 후보를 랭킹 대상(eligible=true)으로 밀어 넣어도 DB 가 거부한다")
	void databaseRefusesEligibleFailingCandidate() {
		assertThatThrownBy(() -> insertCandidate(UUID.randomUUID(), "RANKED", true,
				ConstraintVerdict.FAIL, 0.9, 1, false))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("순위나 점수 없이 노출됐다고 적을 수 없다")
	void databaseRefusesReturnedCandidateWithoutRankOrScore() {
		assertThatThrownBy(() -> insertCandidate(UUID.randomUUID(), "RETURNED", true,
				ConstraintVerdict.PASS, null, null, true))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("UNIQUE(request_id, place_id) 가 같은 요청의 중복 장소를 막는다")
	void uniqueRequestPlaceBlocksDuplicates() {
		UUID placeId = UUID.randomUUID();
		insertCandidate(placeId, "RETURNED", true, ConstraintVerdict.PASS, 0.9, 1, true);

		assertThatThrownBy(() -> insertCandidate(placeId, "RANKED", true, ConstraintVerdict.PASS, 0.5, 2, false))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("다른 request_id 에서는 같은 장소가 다시 후보가 될 수 있다")
	void samePlaceIsAllowedInAnotherRequest() {
		UUID placeId = UUID.randomUUID();
		insertCandidate(placeId, "RETURNED", true, ConstraintVerdict.PASS, 0.9, 1, true);

		UUID otherRequestId = UUID.randomUUID();
		insertJob(otherRequestId);

		assertThatCode(() -> this.jdbcTemplate.update(INSERT_CANDIDATE, UUID.randomUUID(), otherRequestId,
				placeId, "RETURNED", true, "PASS", 0.9, 1, true)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("성공한 Job 에 버전이 비어 있으면 DB 가 거부한다 — policy_version 포함")
	void databaseRefusesSucceededJobWithoutVersions() {
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, job_type, job_status, created_at)
				VALUES (?, ?, ?, 'ITINERARY_GENERATION', 'SUCCEEDED', now())
				""", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);

		// policy_version 하나만 빠져도 거부된다.
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, job_type, job_status,
				    model_version, feature_version, ontology_version, dataset_version,
				    service_version, deployment_environment, created_at)
				VALUES (?, ?, ?, 'ITINERARY_GENERATION', 'SUCCEEDED',
				        'm', 'f', 'o', 'd', 's', 'test', now())
				""", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("실패한 Job 은 버전 없이 남을 수 있지만 error_code 없이는 남을 수 없다")
	void failedJobNeedsAReasonButNotVersions() {
		assertThatCode(() -> this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, job_type, job_status, error_code, created_at)
				VALUES (?, ?, ?, 'ITINERARY_GENERATION', 'FAILED', 'VERSION_UNRESOLVED', now())
				""", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
				.doesNotThrowAnyException();

		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, job_type, job_status, created_at)
				VALUES (?, ?, ?, 'ITINERARY_GENERATION', 'FAILED', now())
				""", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 계약에 없는 job_status 는 DB 가 거부한다 — FALLBACK 은 상태가 아니다")
	void databaseRefusesStatusesOutsideTheContract() {
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, job_type, job_status, error_code, created_at)
				VALUES (?, ?, ?, 'ITINERARY_GENERATION', 'FALLBACK', 'X', now())
				""", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("resource_type 과 resource_id 는 함께 있거나 함께 비어 있어야 한다")
	void resourceFieldsMustAgree() {
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, job_type, job_status, resource_type, error_code, created_at)
				VALUES (?, ?, ?, 'ITINERARY_GENERATION', 'FAILED', 'TRIP', 'X', now())
				""", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 되살아난 FAIL 후보 fixture 는 엔티티 단계에서도 막힌다")
	void resurrectedFailingCandidateIsRejectedInJava() {
		assertThatThrownBy(() -> RecommendationCandidate.builder()
				.candidateId(UUID.randomUUID())
				.requestId(this.requestId)
				.placeId(UUID.randomUUID())
				.candidateSource("ONTOLOGY_SEED")
				.candidateStage(CandidateStage.RETURNED)
				.eligible(false)
				.constraintVerdict(ConstraintVerdict.FAIL)
				.finalScore(0.9)
				.finalRank(1)
				.returned(true)
				.createdAt(OffsetDateTime.now())
				.build())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("FAIL");
	}

	/**
	 * {@code table_schema = current_schema()} 가 필요하다. 테스트 DB 하나에 스키마를 여럿 두고
	 * (JDBC {@code ?currentSchema=}) 격리하면 같은 표가 스키마마다 있어 1행 기대가 깨진다 —
	 * 보려는 것은 지금 이 연결이 쓰는 스키마의 칸 타입이다.
	 */
	@Test
	@DisplayName("JSONB · UUID · VARCHAR[] 컬럼이 PostgreSQL 타입 그대로 살아 있다")
	void postgresSpecificColumnTypesAreInPlace() {
		String featureValuesType = this.jdbcTemplate.queryForObject("""
				SELECT data_type FROM information_schema.columns
				WHERE table_name = 'recommendation_candidate' AND column_name = 'feature_values'
				  AND table_schema = current_schema()
				""", String.class);
		String reasonCodesType = this.jdbcTemplate.queryForObject("""
				SELECT data_type FROM information_schema.columns
				WHERE table_name = 'recommendation_candidate' AND column_name = 'reason_codes'
				  AND table_schema = current_schema()
				""", String.class);

		assertThat(featureValuesType).isEqualTo("jsonb");
		assertThat(reasonCodesType).isEqualTo("ARRAY");
	}

	private void insertCandidate(UUID placeId, String stage, boolean eligible, ConstraintVerdict verdict,
			Double finalScore, Integer finalRank, boolean returned) {
		this.jdbcTemplate.update(INSERT_CANDIDATE, UUID.randomUUID(), this.requestId, placeId, stage, eligible,
				verdict.name(), finalScore, finalRank, returned);
	}
}
