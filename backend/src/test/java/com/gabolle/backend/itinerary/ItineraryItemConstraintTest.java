package com.gabolle.backend.itinerary;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.recommendation.support.PersonalizationFixture;
import com.gabolle.backend.recommendation.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 애플리케이션을 우회해도 {@code itinerary_item}·{@code itinerary_leg} 의 DB 제약이
 * 스스로 막는가.
 *
 * <p>원시 SQL 로 직접 넣는다. 자바 검증({@code ItineraryItem}·{@code ItineraryLeg}
 * 생성자)을 건너뛰어야 제약 자체가 도는지 알 수 있다.
 */
class ItineraryItemConstraintTest extends PostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private PersonalizationFixture.Ids references;

	private UUID placeId;

	private UUID itineraryVersionId;

	private void setUpFixture() {
		this.references = PersonalizationFixture.insert(this.jdbcTemplate);
		this.placeId = insertPlace();
		this.itineraryVersionId = insertItineraryVersion(this.references.tripId(), this.references.userId(), null);
	}

	@Test
	@DisplayName("같은 판·같은 날에 sequence 가 중복되면 DB 가 거부한다")
	void duplicateSequenceInSameDayIsRejected() {
		setUpFixture();
		insertItem(this.itineraryVersionId, UUID.randomUUID(), 0, LocalDate.of(2026, 9, 10), 1, this.placeId,
				null, null, null, null);

		assertThatThrownBy(() -> insertItem(this.itineraryVersionId, UUID.randomUUID(), 0,
				LocalDate.of(2026, 9, 10), 1, this.placeId, null, null, null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 판 안에서 item_key 가 중복되면 DB 가 거부한다 — 판을 건너 항목을 잇는 열쇠다")
	void duplicateItemKeyInSameVersionIsRejected() {
		setUpFixture();
		UUID itemKey = UUID.randomUUID();
		insertItem(this.itineraryVersionId, itemKey, 0, LocalDate.of(2026, 9, 10), 1, this.placeId,
				null, null, null, null);

		assertThatThrownBy(() -> insertItem(this.itineraryVersionId, itemKey, 0,
				LocalDate.of(2026, 9, 10), 2, this.placeId, null, null, null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("없는 place_id 를 가리키는 항목은 DB 가 거부한다")
	void missingPlaceIsRejected() {
		setUpFixture();
		assertThatThrownBy(() -> insertItem(this.itineraryVersionId, UUID.randomUUID(), 0,
				LocalDate.of(2026, 9, 10), 1, UUID.randomUUID(), null, null, null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("end_time 이 start_time 보다 앞이거나 같으면 DB 가 거부한다")
	void endTimeNotAfterStartTimeIsRejected() {
		setUpFixture();
		assertThatThrownBy(() -> insertItem(this.itineraryVersionId, UUID.randomUUID(), 0,
				LocalDate.of(2026, 9, 10), 1, this.placeId, LocalTime.of(10, 0), LocalTime.of(10, 0), null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("반쪽 시간(start_time 만 있고 end_time 이 없음)은 DB 가 거부한다")
	void halfTimeIsRejected() {
		setUpFixture();
		assertThatThrownBy(() -> insertItem(this.itineraryVersionId, UUID.randomUUID(), 0,
				LocalDate.of(2026, 9, 10), 1, this.placeId, LocalTime.of(10, 0), null, null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("sequence = 0 은 DB 가 거부한다 — 그 날 안에서 1부터다")
	void sequenceZeroIsRejected() {
		setUpFixture();
		assertThatThrownBy(() -> insertItem(this.itineraryVersionId, UUID.randomUUID(), 0,
				LocalDate.of(2026, 9, 10), 0, this.placeId, null, null, null, null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("음수 비용은 DB 가 거부한다")
	void negativeCostIsRejected() {
		setUpFixture();
		assertThatThrownBy(() -> insertItem(this.itineraryVersionId, UUID.randomUUID(), 0,
				LocalDate.of(2026, 9, 10), 1, this.placeId, null, null, null, -1))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 travel_mode = 'HELICOPTER' 는 DB 가 거부한다 — trip.travel_modes 와 같은 아홉 개 목록이다")
	void unknownTravelModeIsRejected() {
		setUpFixture();
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO itinerary_leg (
				    itinerary_leg_id, itinerary_version_id, day_index, sequence, to_place_id, travel_mode, created_at)
				VALUES (?, ?, 0, 1, ?, 'HELICOPTER', now())
				""", UUID.randomUUID(), this.itineraryVersionId, this.placeId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 source_request_id 로 판을 두 번 만들면 DB 가 거부한다 — 같은 추천 요청은 판을 하나만 만든다")
	void duplicateSourceRequestIdIsRejected() {
		this.references = PersonalizationFixture.insert(this.jdbcTemplate);
		// recommendation_job.request_id 가 FK 대상으로 있기만 하면 된다 —
		// ck_recommendation_job_result_present 를 건드리지 않게 FAILED 로 넣는다.
		UUID requestId = insertFailedJob();

		insertItineraryVersion(this.references.tripId(), this.references.userId(), requestId);

		assertThatThrownBy(() -> insertItineraryVersion(this.references.tripId(), this.references.userId(), requestId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 SUCCEEDED 인 ITINERARY_GENERATION Job 에 itinerary_id 가 없으면 DB 가 거부한다")
	void succeededItineraryGenerationJobWithoutItineraryIsRejected() {
		this.references = PersonalizationFixture.insert(this.jdbcTemplate);

		assertThatThrownBy(this::insertSucceededItineraryGenerationJob)
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 막지 못하는 것 — visit_date 가 여행 기간 밖이어도 이 표의 CHECK 는 통과한다"
			+ " (itinerary_item ↔ itinerary_versions ↔ itineraries ↔ trip 을 건너는 검사라 CHECK 로 못 쓴다."
			+ " ItineraryDraftService 가 응용 계층에서 지킨다.)")
	void visitDateOutsideTripRangeIsNotBlockedByTheDatabase() {
		setUpFixture();
		assertThatCode(() -> insertItem(this.itineraryVersionId, UUID.randomUUID(), 0,
				LocalDate.of(2099, 1, 1), 1, this.placeId, null, null, null, null))
				.doesNotThrowAnyException();
	}

	private UUID insertPlace() {
		UUID placeId = UUID.randomUUID();
		this.jdbcTemplate.update(
				"INSERT INTO place (place_id, name_ko, created_at) VALUES (?, '테스트 장소', now())", placeId);
		return placeId;
	}

	private UUID insertItineraryVersion(UUID tripId, UUID userId, UUID sourceRequestId) {
		UUID itineraryId = UUID.randomUUID();
		this.jdbcTemplate.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, now())",
				itineraryId, tripId);

		UUID itineraryVersionId = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO itinerary_versions
				    (itinerary_version_id, itinerary_id, version, operation, created_by, request_id,
				     source_request_id, created_at)
				VALUES (?, ?, 1, 'CREATE', ?, ?, ?, now())
				""", itineraryVersionId, itineraryId, userId, "req_" + UUID.randomUUID(), sourceRequestId);
		return itineraryVersionId;
	}

	/** SUCCEEDED · ITINERARY_GENERATION · itinerary_id 없음 — ck_recommendation_job_result_present 의 표적. */
	private UUID insertSucceededItineraryGenerationJob() {
		UUID requestId = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, job_type, job_status,
				    preference_snapshot_id, constraint_snapshot_id,
				    model_version, feature_version, ontology_version, policy_version, dataset_version,
				    service_version, deployment_environment, created_at)
				VALUES (?, ?, ?, 'ITINERARY_GENERATION', 'SUCCEEDED', ?, ?,
				        'm', 'f', 'o', 'p', 'd', 's', 'test', now())
				""", UUID.randomUUID(), requestId, this.references.userId(),
				this.references.preferenceSnapshotId(), this.references.constraintSnapshotId());
		return requestId;
	}

	/** FK 대상만 필요할 때 쓰는 최소 Job — 다른 CHECK 를 건드리지 않는다. */
	private UUID insertFailedJob() {
		UUID requestId = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, job_type, job_status, error_code, created_at)
				VALUES (?, ?, ?, 'ITINERARY_GENERATION', 'FAILED', 'TEST_FIXTURE', now())
				""", UUID.randomUUID(), requestId, this.references.userId());
		return requestId;
	}

	private void insertItem(UUID itineraryVersionId, UUID itemKey, int dayIndex, LocalDate visitDate,
			int sequence, UUID placeId, LocalTime startTime, LocalTime endTime, Integer stayMinutes,
			Integer estimatedCostKrw) {
		this.jdbcTemplate.update("""
				INSERT INTO itinerary_item (
				    itinerary_item_id, itinerary_version_id, item_key, day_index, visit_date, sequence,
				    place_id, start_time, end_time, stay_minutes, estimated_cost_krw, data_status, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UNKNOWN', now())
				""", UUID.randomUUID(), itineraryVersionId, itemKey, dayIndex, visitDate, sequence, placeId,
				startTime, endTime, stayMinutes, estimatedCostKrw);
	}
}
