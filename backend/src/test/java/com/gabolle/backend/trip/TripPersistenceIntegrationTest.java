package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.testslice.TripSliceApplication;

/**
 * 여행·멤버·취향 스냅샷·제약·멱등 키가 실제 PostgreSQL 에 저장·복원되는지 본다.
 * H2 가 아닌 이유는 TIMESTAMPTZ·UUID·JSONB·CHECK 제약이 PostgreSQL 에서만 진짜로
 * 검증되기 때문이다.
 */
@SpringBootTest(classes = TripSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TripPersistenceIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String userId;

	@BeforeEach
	void seedOwner() {
		userId = UUID.randomUUID().toString();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				UUID.fromString(userId), now, now);
	}

	private Trip newTrip(String tripId, Instant now) {
		return new Trip(tripId, userId,
				LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
				35.1587, 129.1604, 300000, 2,
				"MORNING_TO_EVENING", "Asia/Seoul", now);
	}

	@Test
	void tripRoundTripsThroughPostgres() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = UUID.randomUUID().toString();

		Trip trip = newTrip(tripId, now);
		TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, userId, now);
		PreferenceSnapshot snapshot = new PreferenceSnapshot(
				UUID.randomUUID().toString(), tripId, 1, List.of(), PersonalizationScope.TRIP, List.of(), now);

		tripRepository.save(trip, List.of(), owner, snapshot);

		Trip found = tripRepository.findById(tripId).orElseThrow();

		assertThat(found.tripId()).isEqualTo(tripId);
		assertThat(found.createdBy()).isEqualTo(userId);
		assertThat(found.startDate()).isEqualTo(LocalDate.of(2026, 9, 6));
		assertThat(found.finishDate()).isEqualTo(LocalDate.of(2026, 9, 8));
		assertThat(found.originLat()).isEqualTo(35.1587);
		assertThat(found.originLng()).isEqualTo(129.1604);
		assertThat(found.budgetKrw()).isEqualTo(300000);
		assertThat(found.partySize()).isEqualTo(2);
		assertThat(found.timeWindow()).isEqualTo("MORNING_TO_EVENING");
		assertThat(found.timezone()).isEqualTo("Asia/Seoul");
		assertThat(found.status()).isEqualTo(Trip.Status.PLANNING);
		assertThat(found.createdAt()).isEqualTo(now);
		assertThat(found.updatedAt()).isEqualTo(now);
		assertThat(found.deletedAt()).isNull();
	}

	@Test
	void unknownTripIdIsEmpty() {
		assertThat(tripRepository.findById(UUID.randomUUID().toString())).isEmpty();
	}

	@Test
	void ownerIsPersistedAsMember() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = UUID.randomUUID().toString();
		Trip trip = newTrip(tripId, now);
		TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, userId, now);
		PreferenceSnapshot snapshot = new PreferenceSnapshot(
				UUID.randomUUID().toString(), tripId, 1, List.of(), PersonalizationScope.TRIP, List.of(), now);

		tripRepository.save(trip, List.of(), owner, snapshot);

		List<TripMember> members = tripRepository.findMembers(tripId);
		assertThat(members).hasSize(1);
		assertThat(members.get(0).userId()).isEqualTo(userId);
		assertThat(members.get(0).role()).isEqualTo(TripMember.Role.OWNER);
	}

	@Test
	void preferenceAnswerAndConstraintRoundTrip() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = UUID.randomUUID().toString();
		Trip trip = newTrip(tripId, now);
		TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, userId, now);

		// dimension 은 ck_preference_answer_dimension 이 허용하는 8종 중 하나여야 하고,
		// value 는 JSONB 라 따옴표를 포함한 valid JSON 이어야 한다.
		PreferenceSnapshot snapshot = new PreferenceSnapshot(
				UUID.randomUUID().toString(), tripId, 1,
				List.of(new PreferenceSnapshot.PreferenceAnswer(
						"SLOPE_PREFERENCE", "\"RELAXED\"", PreferenceSnapshot.AnswerStatus.SELECTED)),
				PersonalizationScope.TRIP, List.of(), now);

		TripConstraint constraint = new TripConstraint(
				UUID.randomUUID().toString(), tripId, "MOBILITY", "MAX_WALKING_METERS",
				TripConstraint.Severity.HARD, "LTE", null, 5000.0,
				TripConstraint.EvidenceStatus.NEEDS_REVIEW, TripConstraint.AnswerStatus.SELECTED,
				PersonalizationScope.TRIP, null);

		tripRepository.save(trip, List.of(constraint), owner, snapshot);

		PreferenceSnapshot foundSnapshot = tripRepository.findLatestSnapshot(tripId).orElseThrow();
		assertThat(foundSnapshot.version()).isEqualTo(1);
		var slope = foundSnapshot.answers().stream()
				.filter(a -> a.dimension().equals("SLOPE_PREFERENCE")).findFirst().orElseThrow();
		assertThat(slope.valueJson()).isEqualTo("\"RELAXED\"");
		assertThat(slope.status()).isEqualTo(PreferenceSnapshot.AnswerStatus.SELECTED);

		List<TripConstraint> foundConstraints = tripRepository.findConstraints(tripId);
		assertThat(foundConstraints).hasSize(1);
		TripConstraint found = foundConstraints.get(0);
		assertThat(found.type()).isEqualTo("MOBILITY");
		assertThat(found.constraintKey()).isEqualTo("MAX_WALKING_METERS");
		assertThat(found.severity()).isEqualTo(TripConstraint.Severity.HARD);
		assertThat(found.threshold()).isEqualTo(5000.0);
		assertThat(found.answerStatus()).isEqualTo(TripConstraint.AnswerStatus.SELECTED);
	}

	@Test
	void codedAllergyConstraintRoundTrips() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = UUID.randomUUID().toString();
		Trip trip = newTrip(tripId, now);
		TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, userId, now);
		PreferenceSnapshot snapshot = new PreferenceSnapshot(
				UUID.randomUUID().toString(), tripId, 1, List.of(), PersonalizationScope.TRIP, List.of(), now);

		TripConstraint allergy = new TripConstraint(
				UUID.randomUUID().toString(), tripId, "ALLERGY", "PEANUT",
				TripConstraint.Severity.HARD, "EXCLUDES", null, null,
				TripConstraint.EvidenceStatus.NEEDS_REVIEW, TripConstraint.AnswerStatus.SELECTED,
				PersonalizationScope.TRIP, null);

		tripRepository.save(trip, List.of(allergy), owner, snapshot);

		TripConstraint found = tripRepository.findConstraints(tripId).get(0);
		assertThat(found.type()).isEqualTo("ALLERGY");
		assertThat(found.constraintKey()).isEqualTo("PEANUT");
		assertThat(found.severity()).isEqualTo(TripConstraint.Severity.HARD);
	}

	@Test
	void sameIdempotencyKeyReturnsSameTrip() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = UUID.randomUUID().toString();
		Trip trip = newTrip(tripId, now);
		TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, userId, now);
		PreferenceSnapshot snapshot = new PreferenceSnapshot(
				UUID.randomUUID().toString(), tripId, 1, List.of(), PersonalizationScope.TRIP, List.of(), now);

		var first = tripRepository.saveWithIdempotency(userId, "key_1", "fp_1", trip, List.of(), owner, snapshot);
		assertThat(first.created()).isTrue();

		String retryTripId = UUID.randomUUID().toString();
		Trip retryTrip = newTrip(retryTripId, now);
		TripMember retryOwner = TripMember.owner(UUID.randomUUID().toString(), retryTripId, userId, now);
		PreferenceSnapshot retrySnapshot = new PreferenceSnapshot(
				UUID.randomUUID().toString(), retryTripId, 1, List.of(), PersonalizationScope.TRIP, List.of(), now);

		var second = tripRepository.saveWithIdempotency(
				userId, "key_1", "fp_1", retryTrip, List.of(), retryOwner, retrySnapshot);

		assertThat(second.created()).isFalse();
		assertThat(second.trip().tripId()).isEqualTo(tripId);
		assertThat(tripRepository.findById(retryTripId)).isEmpty();
	}

	@Test
	void sameKeyDifferentFingerprintIsRejected() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = UUID.randomUUID().toString();
		Trip trip = newTrip(tripId, now);
		TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, userId, now);
		PreferenceSnapshot snapshot = new PreferenceSnapshot(
				UUID.randomUUID().toString(), tripId, 1, List.of(), PersonalizationScope.TRIP, List.of(), now);

		tripRepository.saveWithIdempotency(userId, "key_2", "fp_a", trip, List.of(), owner, snapshot);

		String otherTripId = UUID.randomUUID().toString();
		Trip otherTrip = newTrip(otherTripId, now);
		TripMember otherOwner = TripMember.owner(UUID.randomUUID().toString(), otherTripId, userId, now);
		PreferenceSnapshot otherSnapshot = new PreferenceSnapshot(
				UUID.randomUUID().toString(), otherTripId, 1, List.of(), PersonalizationScope.TRIP, List.of(), now);

		assertThrows(TripRepository.IdempotencyKeyConflictException.class,
				() -> tripRepository.saveWithIdempotency(
						userId, "key_2", "fp_b", otherTrip, List.of(), otherOwner, otherSnapshot));
	}
}
