package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.testslice.TripSliceApplication;

/**
 * 내 여행 목록 — 누가 어떤 여행을 보는가. 소유자만이 아니라 초대받은 사람도 보이는지, 남의
 * 여행이 새지 않는지, 지운 여행이 빠지는지.
 *
 * <p>진짜 PostgreSQL 을 쓴다 — 정렬·상한·{@code deleted_at} 거르기가 전부 질의에 들어 있어서
 * 메모리 구현으로 재면 그 질의를 한 줄도 안 재게 된다.
 */
@SpringBootTest(classes = TripSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TripListIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private TripQueryService queryService;

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
	void listsTripsIOwnAndTripsIWasInvitedTo() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String mine = saveTrip(ownerId, now);
		String invited = saveTrip(strangerId, now);
		addMember(invited, companionId, TripMember.Role.EDITOR, now);

		List<TripRepository.MemberTrip> ownerView = queryService.list(ownerId, 50);
		List<TripRepository.MemberTrip> companionView = queryService.list(companionId, 50);

		assertThat(ownerView).extracting(row -> row.trip().tripId()).containsExactly(mine);
		assertThat(ownerView).extracting(TripRepository.MemberTrip::role).containsExactly(TripMember.Role.OWNER);

		// 초대받은 사람에게 그 여행이 안 보이면 들어갈 경로가 아예 없다
		assertThat(companionView).extracting(row -> row.trip().tripId()).containsExactly(invited);
		assertThat(companionView).extracting(TripRepository.MemberTrip::role).containsExactly(TripMember.Role.EDITOR);
	}

	@Test
	void doesNotLeakTripsIAmNotAMemberOf() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		saveTrip(strangerId, now);

		assertThat(queryService.list(ownerId, 50)).isEmpty();
	}

	@Test
	void deletedTripsAreLeftOut() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String kept = saveTrip(ownerId, now);
		String removed = saveTrip(ownerId, now);
		jdbcTemplate.update("UPDATE trip SET deleted_at = ? WHERE trip_id = ?",
				OffsetDateTime.now(ZoneOffset.UTC), UUID.fromString(removed));

		assertThat(queryService.list(ownerId, 50)).extracting(row -> row.trip().tripId()).containsExactly(kept);
	}

	@Test
	void mostRecentlyTouchedComesFirstAndTheLimitHolds() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String older = saveTrip(ownerId, now.minusSeconds(600));
		String newer = saveTrip(ownerId, now);

		assertThat(queryService.list(ownerId, 50))
				.extracting(row -> row.trip().tripId())
				.containsExactly(newer, older);

		// 상한은 잘라 낼 뿐 순서를 바꾸지 않는다 — 자른 뒤에 정렬하면 오래된 것이 남는다
		assertThat(queryService.list(ownerId, 1))
				.extracting(row -> row.trip().tripId())
				.containsExactly(newer);
	}

	/** 여행에 한 번도 안 들어간 사람은 빈 목록이다 — 오류가 아니다. */
	@Test
	void aUserWithNoTripsGetsAnEmptyList() {
		assertThat(queryService.list(ownerId, 50)).isEmpty();
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
