package com.gabolle.backend.trip;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.application.TripRatingService;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 여행 별점이 진짜 표({@code trip_rating})에 맞게 저장·덮어쓰기·지우기 되는지 (S15P21E201-1908).
 * 엔티티가 표와 어긋나면 {@code ddl-auto=validate} 가 여기서 잡는다.
 */
class TripRatingPostgresTest extends AuthPostgresIntegrationTest {

	@Autowired
	private TripRatingService service;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private UUID owner;

	private UUID companion;

	private UUID stranger;

	private String tripId;

	@BeforeEach
	void setUp() {
		this.owner = createUser();
		this.companion = createUser();
		this.stranger = createUser();
		UUID trip = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO trip (trip_id, owner_user_id, owner_type, start_date, end_date, created_at, updated_at)
				VALUES (?, ?, 'USER', ?, ?, now(), now())
				""", trip, this.owner, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
		addMember(trip, this.owner, "OWNER");
		addMember(trip, this.companion, "VIEWER");
		this.tripId = trip.toString();
	}

	@Test
	@DisplayName("구성원마다 하나 — 다시 매기면 덮어쓰고 평균·개수가 따라 바뀐다")
	void rateOverwriteAndSummary() {
		this.service.rate(this.tripId, this.owner, 5);
		this.service.rate(this.tripId, this.companion, 2);
		TripRatingService.Rating rerated = this.service.rate(this.tripId, this.companion, 4);

		assertThat(rerated.myScore()).isEqualTo(4);
		assertThat(rerated.count()).isEqualTo(2);
		assertThat(rerated.average()).isEqualTo(4.5);
	}

	@Test
	@DisplayName("지우면 내 줄만 없어진다 · 없던 것을 지워도 실패가 아니다")
	void clear() {
		this.service.rate(this.tripId, this.owner, 3);
		this.service.rate(this.tripId, this.companion, 5);

		TripRatingService.Rating cleared = this.service.clear(this.tripId, this.companion);
		assertThat(cleared.myScore()).isNull();
		assertThat(cleared.count()).isEqualTo(1);

		assertThat(this.service.clear(this.tripId, this.companion).count()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 아무도 안 매겼으면 평균은 null, 개수 0")
	void empty() {
		TripRatingService.Rating rating = this.service.find(this.tripId, this.owner);
		assertThat(rating.myScore()).isNull();
		assertThat(rating.average()).isNull();
		assertThat(rating.count()).isZero();
	}

	@Test
	@DisplayName("🔴 구성원이 아니면 매기지도 읽지도 못한다(404)")
	void strangerRejected() {
		assertThatThrownBy(() -> this.service.rate(this.tripId, this.stranger, 5))
				.isInstanceOf(TripQueryService.TripNotFoundException.class);
		assertThatThrownBy(() -> this.service.find(this.tripId, this.stranger))
				.isInstanceOf(TripQueryService.TripNotFoundException.class);
	}

	@Test
	@DisplayName("🔴 1~5 밖은 저장하기 전에 막는다")
	void outOfRange() {
		assertThatThrownBy(() -> this.service.rate(this.tripId, this.owner, 0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.service.rate(this.tripId, this.owner, 6))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(this.service.find(this.tripId, this.owner).count()).isZero();
	}

	private void addMember(UUID trip, UUID user, String role) {
		this.jdbc.update("INSERT INTO trip_member (trip_member_id, trip_id, user_id, role, joined_at) "
				+ "VALUES (?, ?, ?, ?, now())", UUID.randomUUID(), trip, user, role);
	}

	private UUID createUser() {
		return this.transactionTemplate.execute(status -> this.userRepository.save(AppUser.register("여행자", "KO",
				Instant.now(), "2026-01", PersonalizationMode.EXPLICIT_ONLY, UserStatus.ACTIVE)).getUserId());
	}
}
