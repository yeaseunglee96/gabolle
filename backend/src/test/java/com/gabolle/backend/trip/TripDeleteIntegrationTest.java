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
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.testslice.TripSliceApplication;

/**
 * 누가 지울 수 있고 지운 뒤에 무엇이 닫히는가. 삭제는 행을 지우지 않고 {@code deleted_at} 을
 * 찍는 방식이라, 그 칸 하나로 목록·조회가 전부 닫혀야 "지웠다" 가 참이 된다.
 *
 * <p>진짜 PostgreSQL 을 쓴다 — 지운 여행을 거르는 것이 질의 안에 있어 메모리 구현으로는 한 줄도
 * 안 재게 되고, 행이 실제로 남아 있는지는 표를 직접 세어 봐야 안다.
 */
@SpringBootTest(classes = TripSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TripDeleteIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private TripQueryService queryService;

	@Autowired
	private TripDeletionService deletionService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String ownerId;

	private String companionId;

	private String strangerId;

	@BeforeEach
	void seedUsers() {
		ownerId = newUser();
		companionId = newUser();
		strangerId = newUser();
	}

	@Test
	@DisplayName("소유자가 지우면 목록에서 빠지고 조회도 없는 여행이 된다")
	void ownerDeletesAndEveryDoorCloses() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(ownerId, now);
		addMember(tripId, companionId, TripMember.Role.EDITOR, now);

		deletionService.delete(tripId, ownerId);

		assertThat(queryService.list(ownerId, 50)).isEmpty();
		assertThatThrownBy(() -> queryService.get(tripId, ownerId))
				.isInstanceOf(TripQueryService.TripNotFoundException.class);

		// 동행자에게도 닫힌다 — 소유자 화면에서만 사라지면 그건 삭제가 아니라 숨기기다.
		assertThat(queryService.list(companionId, 50)).isEmpty();
		assertThatThrownBy(() -> queryService.get(tripId, companionId))
				.isInstanceOf(TripQueryService.TripNotFoundException.class);
	}

	@Test
	@DisplayName("🔴 행은 남는다 — 일정·기록·공유 링크가 이 여행을 가리키고 있다")
	void theRowSurvivesAndOnlyTheDeletedAtColumnIsFilled() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(ownerId, now);

		deletionService.delete(tripId, ownerId);

		Integer rows = jdbcTemplate.queryForObject("SELECT count(*) FROM trip WHERE trip_id = ?", Integer.class,
				UUID.fromString(tripId));
		assertThat(rows).as("행 자체는 남아 있어야 한다").isEqualTo(1);

		OffsetDateTime deletedAt = jdbcTemplate.queryForObject(
				"SELECT deleted_at FROM trip WHERE trip_id = ?", OffsetDateTime.class, UUID.fromString(tripId));
		assertThat(deletedAt).as("지운 시각이 실제로 찍혀야 한다").isNotNull();

		// 지워지기 전에 어느 단계였는지는 남는다 — status 를 DELETED 로 덮지 않는 이유다.
		String status = jdbcTemplate.queryForObject("SELECT status FROM trip WHERE trip_id = ?", String.class,
				UUID.fromString(tripId));
		assertThat(status).isEqualTo("PLANNING");
	}

	@Test
	@DisplayName("🔴 동행자는 못 지운다 — 403. 남의 여행이 사라지는 일을 막는다")
	void companionCannotDelete() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(ownerId, now);
		addMember(tripId, companionId, TripMember.Role.EDITOR, now);

		assertThatThrownBy(() -> deletionService.delete(tripId, companionId))
				.isInstanceOf(TripDeletionService.TripDeleteForbiddenException.class);

		// 거부로 끝나야 한다 — 예외를 던지고도 지워져 있으면 안 된다.
		assertThat(queryService.get(tripId, ownerId).trip().isDeleted()).isFalse();
	}

	@Test
	@DisplayName("🔴 회원이 아닌 사람에게는 403 이 아니라 404 다 — 403 은 그 여행이 있다는 확인이 된다")
	void strangerGetsNotFoundNotForbidden() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(ownerId, now);

		assertThatThrownBy(() -> deletionService.delete(tripId, strangerId))
				.isInstanceOf(TripQueryService.TripNotFoundException.class);
	}

	@Test
	@DisplayName("없는 여행을 지우면 404 — 남의 여행일 때와 같은 답이다")
	void unknownTripLooksTheSameAsSomeoneElsesTrip() {
		assertThatThrownBy(() -> deletionService.delete(UUID.randomUUID().toString(), ownerId))
				.isInstanceOf(TripQueryService.TripNotFoundException.class);
	}

	@Test
	@DisplayName("🔴 두 번 지워도 두 번째가 오류가 아니고, 처음 지운 시각이 덮어써지지 않는다")
	void deletingTwiceSucceedsAndKeepsTheFirstTimestamp() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(ownerId, now);

		deletionService.delete(tripId, ownerId);
		OffsetDateTime first = jdbcTemplate.queryForObject(
				"SELECT deleted_at FROM trip WHERE trip_id = ?", OffsetDateTime.class, UUID.fromString(tripId));

		// 응답이 끊긴 앱은 다시 누른다. 그때 오류가 나오면 사용자는 안 지워졌다고 믿는다.
		deletionService.delete(tripId, ownerId);

		OffsetDateTime second = jdbcTemplate.queryForObject(
				"SELECT deleted_at FROM trip WHERE trip_id = ?", OffsetDateTime.class, UUID.fromString(tripId));
		assertThat(second).as("재시도한 시각이 아니라 처음 지운 시각이 사실이다").isEqualTo(first);
	}

	@Test
	@DisplayName("지워진 여행이라도 동행자에게는 여전히 403 이다 — 지울 수 있었다는 신호를 주지 않는다")
	void companionStillCannotDeleteAnAlreadyDeletedTrip() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = saveTrip(ownerId, now);
		addMember(tripId, companionId, TripMember.Role.EDITOR, now);
		deletionService.delete(tripId, ownerId);

		assertThatThrownBy(() -> deletionService.delete(tripId, companionId))
				.isInstanceOf(TripDeletionService.TripDeleteForbiddenException.class);
	}

	private String newUser() {
		String id = UUID.randomUUID().toString();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				UUID.fromString(id), now, now);
		return id;
	}

	private String saveTrip(String creatorId, Instant at) {
		String tripId = UUID.randomUUID().toString();
		Trip trip = new Trip(tripId, creatorId,
				LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
				35.1587, 129.1604, 300000, 2,
				"MORNING_TO_EVENING", "Asia/Seoul", at);
		TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, creatorId, at);
		PreferenceSnapshot snapshot = new PreferenceSnapshot(
				UUID.randomUUID().toString(), tripId, 1, List.of(), PersonalizationScope.TRIP, List.of(), at);
		tripRepository.save(trip, List.of(), owner, snapshot);
		return tripId;
	}

	private void addMember(String tripId, String userId, TripMember.Role role, Instant at) {
		jdbcTemplate.update(
				"INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, ?)",
				UUID.randomUUID(), UUID.fromString(tripId), UUID.fromString(userId), role.name(),
				at.atOffset(ZoneOffset.UTC));
	}
}
