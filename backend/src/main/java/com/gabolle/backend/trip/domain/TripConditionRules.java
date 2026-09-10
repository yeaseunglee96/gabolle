package com.gabolle.backend.trip.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 여행 조건이 성립하는가 — S15P21E201-440. <b>규칙이 사는 한 자리</b>다.
 *
 * <h2>화면의 검사를 서버가 다시 한다</h2>
 * 화면의 검사는 브라우저에서 도는 것이라 우회할 수 있다. 서버가 같은 조건을 다시 보지 않으면
 * 말이 안 되는 여행이 저장되고, 그 위에서 일정 계산기가 터진다. <b>계산기 안에서 터진 오류는
 * 원인을 짚기 어렵다</b> — 들어올 때 막는 것이 훨씬 싸다.
 *
 * <h2>첫 번째 위반에서 멈추지 않는다</h2>
 * 어긋난 것을 <b>전부 모아</b> 돌려준다. 하나씩 알려 주면 사용자가 고칠 때마다 다시 거절당하고,
 * 그 왕복이 몇 번이면 사람은 기능이 고장 났다고 생각한다. 그래서 이 클래스는 예외를 던지지 않고
 * 위반 목록을 돌려주는 것을 기본으로 둔다({@link #check}).
 *
 * <h2>출발지 좌표 — 2026-09-10 에 켰다</h2>
 * 티켓(S15P21E201-440)이 적어 둔 조건인데 한동안 <b>일부러 꺼 두었다.</b> 그때 켰으면 배포된
 * 앱이 여행을 하나도 못 만들었을 것이다 — 화면은 "목록에서 출발지를 선택해 좌표를 확인해
 * 주세요" 로 사용자를 막으면서, 정작 요청을 만들 때 그 자리에 {@code null} 을 박아 보내고
 * 있었다(S15P21E201-791).
 *
 * <p>그 결함이 고쳐져({@code !479}) 앱이 좌표를 실어 보내기 시작했으므로 이제 켠다. 함께
 * 고쳐야 했던 것이 하나 더 있다 — 앱 본문을 고정해 둔 검사
 * ({@code TripCreateAppPayloadIntegrationTest})가 아직 {@code originLat: null} 을 사실로
 * 못 박고 있었다. 그 검사가 낡으면 초록이면서 지키는 것이 없다.
 *
 * <p>🔴 켠 순서가 요점이다. <b>프런트 → 검사 → 서버 규칙</b> 이고, 이 순서를 바꾸면 서버가
 * 자기 앱을 막는다. 앞으로 "요청에 무엇이 반드시 있어야 한다" 는 규칙을 더할 때마다 같은
 * 순서를 지킨다.
 *
 * <p>알려진 대가 하나 — <b>예전에 깔린 앱은 이제 여행을 못 만든다.</b> 그 빌드는 여전히
 * {@code null} 을 보내고 400 을 받는다. 켜는 것 자체가 그 요구이므로 감수하는 쪽으로 정했다.
 *
 * <h2>이미 다른 자리에 있는 규칙 — 시간대</h2>
 * 티켓은 "이동 시간대의 끝이 시작보다 뒤" 도 조건으로 적어 뒀는데, 그 판정은
 * {@link TimeWindows#parseRange} 가 이미 갖고 있고 <b>자기 오류 코드</b>
 * ({@code INVALID_TIME_WINDOW})로 답한다. 그것을 여기서 가로채면 같은 잘못에 다른 코드가
 * 나가고, 그 코드를 보고 있던 검사와 화면이 조용히 어긋난다(실제로 한 번 그렇게 만들었다가
 * 검사 둘이 빨개져 되돌렸다).
 *
 * <p>그래서 규칙을 옮기지 않고 그 자리에 둔다. 이 클래스가 "규칙이 사는 한 자리" 인 것은
 * <b>새로 만든 규칙</b>에 대한 이야기다 — 이미 자기 자리와 자기 코드를 가진 규칙을 끌어오는
 * 것은 한 자리로 모으는 것이 아니라 사본을 만드는 것이다.
 *
 * <h2>도메인의 생성자 검사를 대신하지 않는다</h2>
 * {@link Trip} 생성자도 같은 종류의 것을 본다. 그쪽은 <b>마지막 방어선</b>이라 남겨 둔다 — 다만
 * 그쪽 메시지에는 어느 항목인지가 없어서, 사용자에게 보여 줄 답은 여기서 만든다. 두 곳이 다른
 * 답을 내지 않도록 여기 규칙은 도메인 규칙보다 <b>같거나 더 좁게</b>만 둔다.
 */
public final class TripConditionRules {

	/** 최대 숙박 수. 8박 이상은 일정 계산이 사실상 다른 문제가 된다(상세설계서 3장). */
	public static final int MAX_NIGHTS = 7;

	/** 예산의 최소값과 단위. 1,000원 단위 입력은 화면이 만들 수 없는 값이다. */
	public static final int BUDGET_MIN_KRW = 10_000;

	public static final int BUDGET_UNIT_KRW = 10_000;

	private TripConditionRules() {
	}

	/**
	 * 어긋난 항목을 전부 모아 돌려준다. 빈 목록이면 성립한다.
	 *
	 * @param originLat 출발지 위도. {@code originLng} 과 <b>함께</b> 있어야 한다 — 위 머리말의 "출발지 좌표"
	 *                  참고. 하나만 오면 그것도 위반이다 — 위도만으로는 아무 데도 못 가리킨다
	 * @param originLng 출발지 경도. 위와 같다
	 * @param timeWindow 하루 활동 시간대. <b>여기서 보지 않는다</b> — 아래 "이미 다른 자리에
	 *                   있는 규칙" 참고
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

		// 🔴 출발지 좌표는 둘 다 있어야 한다. 하나만 있는 것은 좌표가 아니다 — 위도만으로는
		//    아무 데도 못 가리키고, 그 상태를 통과시키면 추천 엔진이 출발지를 못 찾아
		//    (ENGINE_ORIGIN_MISSING) 개인화가 조용히 기준선으로 떨어진다. 사용자 눈에는
		//    "여행은 만들어졌는데 추천이 이상하다" 로 보이고 원인을 아무도 못 찾는다.
		//    place 표의 ck_place_origin_pair 가 같은 이유로 짝을 강제한다.
		if (originLat == null || originLng == null) {
			violations.add(new Violation("originLat", "출발지 좌표가 없다. 목록에서 출발지를 골라 주세요"));
		}

		// 예산은 안 보낼 수 있다. 안 보낸 것과 잘못 보낸 것은 다르다 — 안 보내면 "아직 안
		//    정했다" 이고, 그 여행도 만들 수 있어야 한다.
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

	/**
	 * 성립하지 않으면 거부한다.
	 *
	 * @throws TripConditionRejectedException 어긋난 항목이 하나 이상 있다
	 */
	public static void require(LocalDate startDate, LocalDate finishDate, Integer partySize,
			Integer budgetKrw, Double originLat, Double originLng, String timeWindow) {

		List<Violation> violations = check(startDate, finishDate, partySize, budgetKrw, originLat, originLng,
				timeWindow);
		if (!violations.isEmpty()) {
			throw new TripConditionRejectedException(violations);
		}
	}

	/**
	 * 어긋난 항목 하나.
	 *
	 * @param field  항목 이름. 응답에 그대로 실려 화면이 어느 칸을 짚을지 정한다 — 요청의 칸
	 *               이름과 같게 쓴다
	 * @param reason 왜 어긋났나. 사람이 읽는다
	 */
	public record Violation(String field, String reason) {

		/** 응답의 {@code error.fields} 한 줄. 기존 검증 실패와 같은 모양을 쓴다. */
		public String toFieldLine() {
			return this.field + ": " + this.reason;
		}
	}

	/**
	 * 여행 조건이 성립하지 않는다 — 400.
	 *
	 * <p>{@link IllegalArgumentException} 을 물려받는다. 그러면 이 예외를 위한 처리기를 아직
	 * 안 붙인 경로에서도 기존 처리기가 400 으로 답한다 — 조용히 500 이 되지 않는다.
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
