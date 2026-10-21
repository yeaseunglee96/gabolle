package com.gabolle.backend.trip.application;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripMembershipRepository;
import com.gabolle.backend.trip.infra.TripBudgetJpaEntity;
import com.gabolle.backend.trip.infra.TripBudgetJpaRepository;
import com.gabolle.backend.trip.infra.TripExpenseJpaEntity;
import com.gabolle.backend.trip.infra.TripExpenseJpaRepository;

/**
 * 여행 돈 — 쓴 돈 적기·지우기와 예산(S15P21E201-1935).
 *
 * <p>권한:
 * <ul>
 * <li>보기·적기 — 구성원 누구나. 보기 전용 동행자도 같이 다니며 돈을 쓴다. 비회원과 없는 여행은 같은 404
 * ({@link TripQueryService#get})
 * <li>지우기 — 적은 사람 또는 여행을 만든 사람. 남이 적은 줄을 지우면 그 사람 장부가 말없이 바뀐다 — 403
 * <li>예산 — 고칠 수 있는 사람(OWNER·EDITOR)만. 보기 전용 동행자는 403
 * </ul>
 *
 * <p>정산(누가 누구에게 얼마)은 여기서 계산하지 않는다 — 화면이 구성원 목록과 이 줄들로 계산한다.
 * 구성원이 들고 나면 정산이 바뀌는데, 서버가 계산해 저장하면 그 값이 낡는다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripExpenseService {

	private final TripQueryService tripQueryService;

	private final TripMembershipRepository memberships;

	private final TripExpenseJpaRepository expenses;

	private final TripBudgetJpaRepository budgets;

	private final Clock clock;

	public TripExpenseService(TripQueryService tripQueryService, TripMembershipRepository memberships,
			TripExpenseJpaRepository expenses, TripBudgetJpaRepository budgets, Clock clock) {
		this.tripQueryService = tripQueryService;
		this.memberships = memberships;
		this.expenses = expenses;
		this.budgets = budgets;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Ledger find(String tripId, UUID requester) {
		this.tripQueryService.get(tripId, requester.toString());
		return read(UUID.fromString(tripId));
	}

	/** 쓴 돈 한 줄을 적는다. 낸 사람을 비우면 적는 사람이다. 낸 사람은 이 여행 구성원이어야 한다. */
	@Transactional
	public Ledger add(String tripId, UUID requester, NewExpense request) {
		if (request == null || request.amountKrw() == null) {
			throw new IllegalArgumentException("금액(amountKrw)이 비었습니다.");
		}
		this.tripQueryService.get(tripId, requester.toString());
		UUID payer = request.paidBy() == null ? requester : request.paidBy();
		if (!payer.equals(requester) && this.memberships.findMember(tripId, payer.toString()).isEmpty()) {
			throw new IllegalArgumentException("낸 사람(paidBy)이 이 여행 구성원이 아닙니다.");
		}
		OffsetDateTime now = OffsetDateTime.now(this.clock);
		this.expenses.save(new TripExpenseJpaEntity(UUID.fromString(tripId), requester, payer, request.amountKrw(),
				request.category(), request.placeName(), request.note(),
				request.splitEven() == null || request.splitEven(),
				request.spentAt() == null ? now : request.spentAt(), now));
		this.expenses.flush();
		return read(UUID.fromString(tripId));
	}

	@Transactional
	public Ledger remove(String tripId, UUID requester, UUID expenseId) {
		TripQueryService.View view = this.tripQueryService.get(tripId, requester.toString());
		UUID trip = UUID.fromString(tripId);
		TripExpenseJpaEntity expense = this.expenses.findById(expenseId)
				.filter(found -> found.getTripId().equals(trip))
				.orElseThrow(() -> new ExpenseNotFoundException(expenseId));
		if (!expense.getCreatedBy().equals(requester) && view.role() != TripMember.Role.OWNER) {
			throw new ExpenseForbiddenException("적은 사람이나 여행을 만든 사람만 지울 수 있어요.");
		}
		this.expenses.delete(expense);
		this.expenses.flush();
		return read(trip);
	}

	/** 예산을 정하거나 바꾼다. {@code null} 이면 예산을 없앤다. */
	@Transactional
	public Ledger setBudget(String tripId, UUID requester, Integer amountKrw) {
		TripQueryService.View view = this.tripQueryService.get(tripId, requester.toString());
		if (!view.role().canEdit()) {
			throw new ExpenseForbiddenException("예산은 여행을 고칠 수 있는 사람만 정해요.");
		}
		UUID trip = UUID.fromString(tripId);
		OffsetDateTime now = OffsetDateTime.now(this.clock);
		if (amountKrw == null) {
			this.budgets.findById(trip).ifPresent(this.budgets::delete);
		} else {
			this.budgets.findById(trip).ifPresentOrElse(
					existing -> existing.change(amountKrw, requester, now),
					() -> this.budgets.save(new TripBudgetJpaEntity(trip, amountKrw, requester, now)));
		}
		this.budgets.flush();
		return read(trip);
	}

	private Ledger read(UUID trip) {
		List<Expense> items = this.expenses.findByTripIdOrderBySpentAtDescCreatedAtDesc(trip).stream()
				.map(row -> new Expense(row.getExpenseId(), row.getCreatedBy(), row.getPaidBy(), row.getAmountKrw(),
						row.getCategory(), row.getPlaceName(), row.getNote(), row.isSplitEven(), row.getSpentAt()))
				.toList();
		long total = items.stream().mapToLong(Expense::amountKrw).sum();
		Integer budget = this.budgets.findById(trip).map(TripBudgetJpaEntity::getAmountKrw).orElse(null);
		return new Ledger(budget, total, items);
	}

	/** 화면에 내려가는 장부. {@code budgetKrw} 는 안 정했으면 {@code null}. */
	public record Ledger(Integer budgetKrw, long totalKrw, List<Expense> items) {
	}

	public record Expense(UUID expenseId, UUID createdBy, UUID paidBy, int amountKrw, String category,
			String placeName, String note, boolean splitEven, OffsetDateTime spentAt) {
	}

	/** 적을 때 받는 값. {@code paidBy}·{@code spentAt}·{@code splitEven} 은 비워도 된다. */
	public record NewExpense(Integer amountKrw, String category, UUID paidBy, String placeName, String note,
			Boolean splitEven, OffsetDateTime spentAt) {
	}

	public static class ExpenseNotFoundException extends RuntimeException {

		public ExpenseNotFoundException(UUID expenseId) {
			super("쓴 돈 줄을 찾지 못했습니다: " + expenseId);
		}
	}

	public static class ExpenseForbiddenException extends RuntimeException {

		public ExpenseForbiddenException(String message) {
			super(message);
		}
	}
}
