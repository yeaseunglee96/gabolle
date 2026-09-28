package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.trip.application.TripDeletionService;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.testslice.TripSliceApplication;

/**
 * 같은 조건으로 여행을 지웠다 다시 만들 때 (S15P21E201-1716).
 *
 * <p>프론트는 여행 조건으로 늘 같은 {@code Idempotency-Key} 를 만든다. 서버는 같은 키가 다시 오면 저장된 여행을 돌려주는데,
 * 그 여행이 이미 지워진 것이면 사용자는 「없는 여행」 을 받고 추천 요청이 404 로 끝났다. 운영 웹 점검(2026-09-26)에서
 * 발견했다 — 여행을 지우고 같은 조건으로 다시 만들면 「지금은 일정을 만들 수 없어요」 가 떴다.
 *
 * <p>진짜 PostgreSQL 이 필요하다 — 키를 잡는 것이 {@code trip_idempotency} 표의 유일 제약과 {@code ON CONFLICT} 다.
 */
@SpringBootTest(classes = TripSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TripIdempotencyAfterDeleteIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private TripDeletionService deletionService;

	@Autowired
	private JdbcTemplate jdbc;

	private String ownerId;

	private String key;

	@BeforeEach
	void setUp() {
		this.ownerId = newUser();
		this.key = "test-key-" + UUID.randomUUID();
	}

	private String newUser() {
		String id = UUID.randomUUID().toString();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				UUID.fromString(id), now, now);
		return id;
	}

	private TripRepository.SaveOutcome create(String fingerprint) {
		Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = UUID.randomUUID().toString();
		Trip trip = new Trip(tripId, this.ownerId, LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 26), 35.1587, 129.1604,
				300000, 2, "MORNING_TO_EVENING", "Asia/Seoul", at);
		TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, this.ownerId, at);
		PreferenceSnapshot snapshot = new PreferenceSnapshot(UUID.randomUUID().toString(), tripId, 1, List.of(),
				PersonalizationScope.TRIP, List.of(), at);
		return this.tripRepository.saveWithIdempotency(this.ownerId, this.key, fingerprint, trip, List.of(), owner,
				snapshot);
	}

	private String boundTripId() {
		return this.jdbc.queryForObject("SELECT trip_id FROM trip_idempotency WHERE user_id = ? AND idempotency_key = ?",
				String.class, UUID.fromString(this.ownerId), this.key);
	}

	@Test
	@DisplayName("기준선 — 지우지 않았으면 같은 키로 다시 와도 같은 여행을 돌려준다(재시도)")
	void replayWithoutDeleteReturnsTheSameTrip() {
		TripRepository.SaveOutcome first = create("fp-1");
		TripRepository.SaveOutcome again = create("fp-1");

		assertThat(first.created()).isTrue();
		assertThat(again.created()).isFalse();
		assertThat(again.trip().tripId()).isEqualTo(first.trip().tripId());
	}

	@Test
	@DisplayName("🔴 지운 여행에 묶인 키로 다시 오면 새 여행을 만든다 — 지운 여행을 돌려주지 않는다")
	void replayAfterDeleteCreatesANewTrip() {
		TripRepository.SaveOutcome first = create("fp-1");
		this.deletionService.delete(first.trip().tripId(), this.ownerId);

		TripRepository.SaveOutcome again = create("fp-1");

		assertThat(again.created()).as("지운 여행이 아니라 새 여행이어야 한다").isTrue();
		assertThat(again.trip().tripId()).isNotEqualTo(first.trip().tripId());
		assertThat(again.trip().isDeleted()).isFalse();
		// 키는 이제 새 여행을 가리킨다.
		assertThat(boundTripId()).isEqualTo(again.trip().tripId());
		// 새 여행은 실제로 저장돼 있다.
		assertThat(this.tripRepository.findById(again.trip().tripId())).isPresent();
	}

	@Test
	@DisplayName("🔴 갈아 묶은 뒤에 같은 키가 또 오면 그 새 여행을 돌려준다 — 두 번 만들지 않는다")
	void replayAfterReboundReturnsTheNewTrip() {
		TripRepository.SaveOutcome first = create("fp-1");
		this.deletionService.delete(first.trip().tripId(), this.ownerId);
		TripRepository.SaveOutcome second = create("fp-1");

		TripRepository.SaveOutcome third = create("fp-1");

		assertThat(third.created()).isFalse();
		assertThat(third.trip().tripId()).isEqualTo(second.trip().tripId());
	}

	@Test
	@DisplayName("🔴 두 번 지우고 두 번 다시 만들어도 매번 새 여행이다")
	void deleteAndRecreateRepeatedly() {
		String previous = create("fp-1").trip().tripId();
		for (int round = 0; round < 3; round += 1) {
			this.deletionService.delete(previous, this.ownerId);
			TripRepository.SaveOutcome next = create("fp-1");
			assertThat(next.created()).isTrue();
			assertThat(next.trip().tripId()).isNotEqualTo(previous);
			previous = next.trip().tripId();
		}
	}

	@Test
	@DisplayName("🔴 본문(지문)이 다르면 지운 뒤에도 여전히 충돌이다 — 같은 키를 다른 요청에 쓰는 실수는 계속 막는다")
	void differentFingerprintStillConflictsAfterDelete() {
		TripRepository.SaveOutcome first = create("fp-1");
		this.deletionService.delete(first.trip().tripId(), this.ownerId);

		assertThatThrownBy(() -> create("fp-DIFFERENT")).isInstanceOf(TripRepository.IdempotencyKeyConflictException.class);
	}

	@Test
	@DisplayName("다른 사용자의 같은 키는 서로 영향이 없다")
	void otherUsersAreUnaffected() {
		TripRepository.SaveOutcome mine = create("fp-1");
		this.deletionService.delete(mine.trip().tripId(), this.ownerId);

		String other = newUser();
		Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = UUID.randomUUID().toString();
		Trip trip = new Trip(tripId, other, LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 26), 35.1587, 129.1604, 300000,
				2, "MORNING_TO_EVENING", "Asia/Seoul", at);
		TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, other, at);
		PreferenceSnapshot snapshot = new PreferenceSnapshot(UUID.randomUUID().toString(), tripId, 1, List.of(),
				PersonalizationScope.TRIP, List.of(), at);
		TripRepository.SaveOutcome theirs = this.tripRepository.saveWithIdempotency(other, this.key, "fp-1", trip,
				List.of(), owner, snapshot);

		assertThat(theirs.created()).isTrue();
		assertThat(theirs.trip().tripId()).isEqualTo(tripId);
	}

}
