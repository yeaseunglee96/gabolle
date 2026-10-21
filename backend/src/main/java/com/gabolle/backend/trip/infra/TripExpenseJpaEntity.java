package com.gabolle.backend.trip.infra;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code trip_expense} 표 매핑 — 여행에서 쓴 돈 한 줄(S15P21E201-1935).
 *
 * <p>금액은 원(KRW) 정수다. 외화로 쓴 돈도 화면이 원으로 바꿔 적는다 — 통화를 섞어 두면 합계를 못 낸다.
 */
@Entity
@Table(name = "trip_expense")
public class TripExpenseJpaEntity {

	/** DB 의 {@code ck_trip_expense_amount} 와 같다. 1억 원을 넘는 한 줄은 오타로 본다. */
	public static final int MAX_AMOUNT_KRW = 100_000_000;

	/** DB 의 {@code ck_trip_expense_category} 와 같다. */
	public static final Set<String> CATEGORIES = Set.of("FOOD", "CAFE", "TRANSPORT", "ADMISSION", "SHOPPING", "LODGING", "OTHER");

	public static final int MAX_PLACE_NAME = 120;

	public static final int MAX_NOTE = 200;

	@Id
	@Column(name = "expense_id", nullable = false, updatable = false)
	private UUID expenseId;

	@Column(name = "trip_id", nullable = false, updatable = false)
	private UUID tripId;

	/** 적은 사람 — 지우기 권한과 탈퇴 정리가 이 칸을 본다. */
	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	/** 낸 사람 — 정산이 이 칸을 본다. 적은 사람과 다를 수 있다(남이 낸 것을 대신 적기). */
	@Column(name = "paid_by", nullable = false)
	private UUID paidBy;

	@Column(name = "amount_krw", nullable = false)
	private int amountKrw;

	@Column(name = "category", nullable = false, length = 20)
	private String category;

	@Column(name = "place_name", length = MAX_PLACE_NAME)
	private String placeName;

	@Column(name = "note", length = MAX_NOTE)
	private String note;

	/** 구성원 모두가 똑같이 나눌 것인가. 아니면 낸 사람 혼자 쓴 돈이다. */
	@Column(name = "split_even", nullable = false)
	private boolean splitEven;

	@Column(name = "spent_at", nullable = false)
	private OffsetDateTime spentAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected TripExpenseJpaEntity() {
		// JPA 전용
	}

	public TripExpenseJpaEntity(UUID tripId, UUID createdBy, UUID paidBy, int amountKrw, String category,
			String placeName, String note, boolean splitEven, OffsetDateTime spentAt, OffsetDateTime now) {
		this.expenseId = UUID.randomUUID();
		this.tripId = tripId;
		this.createdBy = createdBy;
		this.paidBy = paidBy;
		this.amountKrw = requireAmount(amountKrw);
		this.category = requireCategory(category);
		this.placeName = trimToLimit(placeName, MAX_PLACE_NAME, "장소 이름");
		this.note = trimToLimit(note, MAX_NOTE, "메모");
		this.splitEven = splitEven;
		this.spentAt = spentAt;
		this.createdAt = now;
	}

	public static int requireAmount(int amountKrw) {
		if (amountKrw < 1 || amountKrw > MAX_AMOUNT_KRW) {
			throw new IllegalArgumentException("금액은 1원~" + MAX_AMOUNT_KRW + "원 사이여야 합니다: " + amountKrw);
		}
		return amountKrw;
	}

	public static String requireCategory(String category) {
		if (category == null || !CATEGORIES.contains(category)) {
			throw new IllegalArgumentException("갈래(category)는 " + CATEGORIES + " 중 하나여야 합니다: " + category);
		}
		return category;
	}

	/** 앞뒤 공백을 걷고, 비었으면 {@code null}. 너무 길면 자르지 않고 거부한다 — 잘린 글은 다른 뜻이 된다. */
	static String trimToLimit(String value, int limit, String label) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		if (trimmed.isEmpty()) {
			return null;
		}
		if (trimmed.length() > limit) {
			throw new IllegalArgumentException(label + "은 " + limit + "자까지입니다.");
		}
		return trimmed;
	}

	public UUID getExpenseId() {
		return this.expenseId;
	}

	public UUID getTripId() {
		return this.tripId;
	}

	public UUID getCreatedBy() {
		return this.createdBy;
	}

	public UUID getPaidBy() {
		return this.paidBy;
	}

	public int getAmountKrw() {
		return this.amountKrw;
	}

	public String getCategory() {
		return this.category;
	}

	public String getPlaceName() {
		return this.placeName;
	}

	public String getNote() {
		return this.note;
	}

	public boolean isSplitEven() {
		return this.splitEven;
	}

	public OffsetDateTime getSpentAt() {
		return this.spentAt;
	}
}
