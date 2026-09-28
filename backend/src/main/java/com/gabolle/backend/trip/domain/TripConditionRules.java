package com.gabolle.backend.trip.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 여행 조건이 성립하는가. 화면의 검사는 우회할 수 있어 서버가 같은 조건을 다시 본다 — 들어올 때
 * 막지 않으면 말이 안 되는 여행이 저장되고 일정 계산기 안에서 터진다.
 *
 * <p>첫 위반에서 멈추지 않고 어긋난 것을 전부 모아 돌려준다({@link #check}). 하나씩 알려 주면
 * 사용자가 고칠 때마다 다시 거절당한다.
 *
 * <p>시간대(끝이 시작보다 뒤)는 여기서 보지 않는다. {@link TimeWindows#parseRange} 가 이미
 * 자기 오류 코드({@code INVALID_TIME_WINDOW})로 답하고, 여기서 가로채면 같은 잘못에 다른 코드가
 * 나간다. {@link Trip} 생성자도 같은 종류를 보지만 그쪽은 마지막 방어선이라 남겨 둔다 — 두 곳이
 * 다른 답을 내지 않도록 여기 규칙은 도메인 규칙보다 같거나 더 좁게만 둔다.
 */
public final class TripConditionRules {

	/** 8박 이상은 일정 계산이 사실상 다른 문제가 된다. */
	public static final int MAX_NIGHTS = 7;

	/** 예산의 최소값과 단위. 화면이 1,000원 단위 입력을 만들 수 없다. */
	public static final int BUDGET_MIN_KRW = 10_000;

	public static final int BUDGET_UNIT_KRW = 10_000;

	private TripConditionRules() {
	}

	/**
	 * 어긋난 항목을 전부 모아 돌려준다. 빈 목록이면 성립한다.
	 *
	 * @param originLat {@code originLng} 과 함께 있어야 한다. 하나만 오는 것도 위반이다
	 * @param timeWindow 여기서 보지 않는다 — {@link TimeWindows#parseRange} 의 몫이다
	 */
	public static List<Violation> check(LocalDate startDate, LocalDate finishDate, Integer partySize,
			Integer budgetKrw, Double originLat, Double originLng, String timeWindow) {

		List<Violation> violations = new ArrayList<>();

		if (startDate == null) {
			violations.add(new Violation("startDate", "가는 날이 없다"));
		}
		if (finishDate == null) {
			violations.add(new Violation("finishDate", "오는 날이 없다"));
		}
		if (startDate != null && finishDate != null) {
			if (finishDate.isBefore(startDate)) {
				violations.add(new Violation("finishDate",
						"오는 날이 가는 날보다 앞이다 (" + startDate + " → " + finishDate + ")"));
			}
			else {
				long nights = ChronoUnit.DAYS.between(startDate, finishDate);
				if (nights > MAX_NIGHTS) {
					violations.add(new Violation("finishDate",
							"최대 " + MAX_NIGHTS + "박까지다 (" + nights + "박)"));
				}
			}
		}

		if (partySize == null) {
			violations.add(new Violation("partySize", "인원이 없다"));
		}
		else if (partySize < 1) {
			violations.add(new Violation("partySize", "인원은 1명 이상이어야 한다 (" + partySize + ")"));
		}

		// 좌표 하나만 통과시키면 추천 엔진이 출발지를 못 찾아 개인화가 조용히 기준선으로 떨어진다.
		// place 표의 ck_place_origin_pair 가 같은 이유로 짝을 강제한다.
		if (originLat == null || originLng == null) {
			violations.add(new Violation("originLat", "출발지 좌표가 없다. 목록에서 출발지를 골라 주세요"));
		}

		// 예산은 안 보낼 수 있다. 안 보낸 것은 "아직 안 정했다" 이고 그 여행도 만들 수 있어야 한다.
		if (budgetKrw != null) {
			if (budgetKrw < BUDGET_MIN_KRW) {
				violations.add(new Violation("budgetKrw",
						"예산은 " + BUDGET_MIN_KRW + "원 이상이어야 한다 (" + budgetKrw + ")"));
			}
			else if (budgetKrw % BUDGET_UNIT_KRW != 0) {
				violations.add(new Violation("budgetKrw",
						"예산은 " + BUDGET_UNIT_KRW + "원 단위여야 한다 (" + budgetKrw + ")"));
			}
		}

		return List.copyOf(violations);
	}

	/** @throws TripConditionRejectedException 어긋난 항목이 하나 이상 있다 */
	public static void require(LocalDate startDate, LocalDate finishDate, Integer partySize,
			Integer budgetKrw, Double originLat, Double originLng, String timeWindow) {

		List<Violation> violations = check(startDate, finishDate, partySize, budgetKrw, originLat, originLng,
				timeWindow);
		if (!violations.isEmpty()) {
			throw new TripConditionRejectedException(violations);
		}
	}

	/**
	 * 1박 이상이면 숙소가 있어야 한다 — 우리 표의 장소든 묵을 동네든 (S15P21E201-1585). 당일치기는 숙소 없이 된다.
	 *
	 * <p>숙소를 모르면 둘째 날 아침을 어디서 여는지 몰라 매일 여행 출발지(역·집)에서 나서는 일정이 되고,
	 * 첫날 밤엔 돌아갈 자리가 없다. 사용자 결정(2026-09-24): 숙소를 안 고르면 추천하지 않는다.
	 *
	 * <p>{@link #check} 와 따로 둔 것은 여행 만들기에서 <b>호텔 스냅샷을 장소로 바꾼 뒤</b>에 봐야 해서다 —
	 * 스냅샷으로 고른 호텔도 숙소다. 추천 요청은 저장된 여행의 두 칸을 그대로 넘긴다.
	 *
	 * @param accommodationArea 동네 코드. 모르는 코드는 없는 것이다 — 저장할 때도 버려진다({@link TravelArea#of})
	 * @throws TripConditionRejectedException {@code accommodation} 칸 하나로 — 화면이 이 이름으로 문장을 고른다
	 */
	public static void requireLodging(LocalDate startDate, LocalDate finishDate, String accommodationPlaceId,
			String accommodationArea) {
		if (startDate == null || finishDate == null || !finishDate.isAfter(startDate)) {
			return;
		}
		boolean hasPlace = accommodationPlaceId != null && !accommodationPlaceId.isBlank();
		if (hasPlace || TravelArea.of(accommodationArea).isPresent()) {
			return;
		}
		throw new TripConditionRejectedException(List.of(
				new Violation("accommodation", "1박 이상 여행은 숙소가 있어야 한다. 숙소나 묵을 동네를 골라 주세요")));
	}

	/** @param field 응답에 그대로 실려 화면이 어느 칸을 짚을지 정한다 — 요청의 칸 이름과 같게 쓴다 */
	public record Violation(String field, String reason) {

		/** 응답의 {@code error.fields} 한 줄. 기존 검증 실패와 같은 모양이다. */
		public String toFieldLine() {
			return this.field + ": " + this.reason;
		}
	}

	/**
	 * {@link IllegalArgumentException} 을 물려받는다 — 전용 처리기를 안 붙인 경로에서도 기존
	 * 처리기가 400 으로 답하고 조용히 500 이 되지 않는다.
	 */
	public static class TripConditionRejectedException extends IllegalArgumentException {

		private final List<Violation> violations;

		public TripConditionRejectedException(List<Violation> violations) {
			super(violations.stream().map(Violation::toFieldLine).reduce((a, b) -> a + ", " + b).orElse(""));
			this.violations = List.copyOf(violations);
		}

		public List<Violation> violations() {
			return this.violations;
		}

		public List<String> fieldLines() {
			return this.violations.stream().map(Violation::toFieldLine).toList();
		}
	}
}
