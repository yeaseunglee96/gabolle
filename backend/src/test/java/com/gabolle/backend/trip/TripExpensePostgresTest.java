package com.gabolle.backend.trip;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.gabolle.backend.auth.support.AuthPostgresIntegrationTest;
import com.gabolle.backend.trip.application.TripExpenseService;
import com.gabolle.backend.trip.application.TripExpenseService.NewExpense;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.user.domain.AppUser;
import com.gabolle.backend.user.domain.PersonalizationMode;
import com.gabolle.backend.user.domain.UserStatus;
import com.gabolle.backend.user.repository.AppUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 여행 돈이 진짜 표({@code trip_expense}·{@code trip_budget})에 맞게 저장·지우기 되는지 (S15P21E201-1935).
 * 엔티티가 표와 어긋나면 {@code ddl-auto=validate} 가 여기서 잡는다.
 */
class TripExpensePostgresTest extends AuthPostgresIntegrationTest {

	@Autowired
	private TripExpenseService service;

	@Autowired
	private AppUserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private UUID owner;

	private UUID viewer;

	private UUID stranger;

	private String tripId;

	@BeforeEach
	void setUp() {
		this.owner = createUser();
		this.viewer = createUser();
		this.stranger = createUser();
		UUID trip = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO trip (trip_id, owner_user_id, owner_type, start_date, end_date, created_at, updated_at)
				VALUES (?, ?, 'USER', ?, ?, now(), now())
				""", trip, this.owner, LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 4));
		addMember(trip, this.owner, "OWNER");
		addMember(trip, this.viewer, "VIEWER");
		this.tripId = trip.toString();
	}

	private static NewExpense expense(int amount, String category, UUID paidBy) {
		return new NewExpense(amount, category, paidBy, "광안리 밀면집", null, true, null);
	}

	@Test
	@DisplayName("적으면 합계가 따라 바뀌고 최근 것이 위에 온다 · 보기 전용 동행자도 적는다")
	void addAndList() {
		OffsetDateTime morning = OffsetDateTime.of(2026, 10, 3, 9, 40, 0, 0, ZoneOffset.ofHours(9));
		this.service.add(this.tripId, this.owner, new NewExpense(24000, "ADMISSION", null, "해변열차", null, true, morning));
		TripExpenseService.Ledger ledger = this.service.add(this.tripId, this.viewer, expense(18000, "FOOD", null));

		assertThat(ledger.totalKrw()).isEqualTo(42000);
		assertThat(ledger.items()).hasSize(2);
		assertThat(ledger.items().get(0).category()).isEqualTo("FOOD");
		assertThat(ledger.items().get(0).paidBy()).isEqualTo(this.viewer);
		assertThat(ledger.budgetKrw()).isNull();
	}

	@Test
	@DisplayName("남이 낸 것을 대신 적을 수 있다 — 낸 사람은 구성원이어야 한다")
	void paidByMustBeMember() {
		TripExpenseService.Ledger ledger = this.service.add(this.tripId, this.owner, expense(6800, "TRANSPORT", this.viewer));
		assertThat(ledger.items().get(0).paidBy()).isEqualTo(this.viewer);
		assertThat(ledger.items().get(0).createdBy()).isEqualTo(this.owner);

		assertThatThrownBy(() -> this.service.add(this.tripId, this.owner, expense(1000, "FOOD", this.stranger)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("🔴 지우기 — 적은 사람과 여행을 만든 사람만. 남의 줄은 403")
	void removeRules() {
		UUID ownersLine = this.service.add(this.tripId, this.owner, expense(5000, "CAFE", null)).items().get(0).expenseId();
		UUID viewersLine = this.service.add(this.tripId, this.viewer, expense(7000, "CAFE", null)).items().get(0).expenseId();

		assertThatThrownBy(() -> this.service.remove(this.tripId, this.viewer, ownersLine))
				.isInstanceOf(TripExpenseService.ExpenseForbiddenException.class);
		assertThat(this.service.remove(this.tripId, this.owner, viewersLine).items()).hasSize(1);
		assertThat(this.service.remove(this.tripId, this.owner, ownersLine).items()).isEmpty();
	}

	@Test
	@DisplayName("🔴 예산은 고칠 수 있는 사람만 · null 이면 없앤다")
	void budget() {
		assertThat(this.service.setBudget(this.tripId, this.owner, 300000).budgetKrw()).isEqualTo(300000);
		assertThat(this.service.setBudget(this.tripId, this.owner, 250000).budgetKrw()).isEqualTo(250000);
		assertThatThrownBy(() -> this.service.setBudget(this.tripId, this.viewer, 1))
				.isInstanceOf(TripExpenseService.ExpenseForbiddenException.class);
		assertThat(this.service.setBudget(this.tripId, this.owner, null).budgetKrw()).isNull();
	}

	@Test
	@DisplayName("🔴 구성원이 아니면 읽지도 적지도 못한다(404)")
	void strangerRejected() {
		assertThatThrownBy(() -> this.service.find(this.tripId, this.stranger))
				.isInstanceOf(TripQueryService.TripNotFoundException.class);
		assertThatThrownBy(() -> this.service.add(this.tripId, this.stranger, expense(1000, "FOOD", null)))
				.isInstanceOf(TripQueryService.TripNotFoundException.class);
	}

	@Test
	@DisplayName("🔴 금액·갈래가 맞지 않으면 저장하기 전에 막는다")
	void invalid() {
		assertThatThrownBy(() -> this.service.add(this.tripId, this.owner, expense(0, "FOOD", null)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.service.add(this.tripId, this.owner, expense(1000, "DRINKS", null)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(this.service.find(this.tripId, this.owner).items()).isEmpty();
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
