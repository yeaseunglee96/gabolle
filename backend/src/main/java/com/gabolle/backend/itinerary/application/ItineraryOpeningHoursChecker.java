package com.gabolle.backend.itinerary.application;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.place.service.OpeningHoursFilterPort;

/**
 * 그날의 방문 시각이 장소의 영업시간을 어기는가 — S15P21E201-268 의 마지막 완료 기준.
 *
 * <h2>순서를 되돌리지 않는다. 알려 주기만 한다</h2>
 * 완료 기준의 문장이 <i>"문 닫은 시간에 걸리는 순서를 보내면 순서가 유지된 채 경고가 온다"</i> 다.
 * 사용자가 아침에 바다부터 보고 싶다고 순서를 바꿨을 때 서버가 그것을 더 나은 순서로 고쳐 놓으면
 * 사용자는 기능이 고장 났다고 생각한다. 그래서 이 검사는 <b>아무것도 바꾸지 않고</b> 판정만
 * 돌려주고, 부르는 쪽이 그것을 응답에 실어 보낸다.
 *
 * <h2>못 본 것을 조용히 넘기지 않는다</h2>
 * {@code place} 표에 영업시간 칸이 아직 없다. 그 칸의 모양은 담당 티켓(-88 · -97 · -300 계열)이
 * 정하기로 하고 S15P21E201-262 가 일부러 비워 뒀다. 그래서 지금은 영업시간을 묻는 문
 * ({@link OpeningHoursFilterPort})이 "수집 안 했다" 고 답한다.
 *
 * <p>그때 위반이 없다고 답하면 <b>틀린 안심</b>을 준다 — 화면은 "영업시간 확인했고 문제 없음" 과
 * "영업시간을 볼 수 없었음" 을 구분할 수 없게 된다. 그래서 위반 목록과 별도로
 * {@link Result#notChecked()} 에 <b>무슨 검사를 왜 못 했는지</b>를 담아 올린다. 같은 어휘를
 * 장소 후보 조회가 이미 쓰고 있다({@code PlaceCandidateResponse.notApplied} — 값도
 * {@code OPENING_HOURS} · {@code NOT_COLLECTED} 로 같다).
 *
 * <p>영업시간 칸이 생기고 그것을 읽는 구현이 붙으면 이 클래스는 한 줄도 안 고쳐도 된다. 문이
 * {@code isAvailable()} 로 참을 답하는 순간 위반이 실제로 잡히기 시작한다.
 */
@Component
@Profile({ "db", "dev" })
public class ItineraryOpeningHoursChecker {

	/** 응답에 실리는 검사 이름. 장소 후보 조회가 쓰는 값과 같다. */
	public static final String CHECK = "OPENING_HOURS";

	/** 위반 하나의 종류 — "그 시각에 그 집은 문을 닫는다". */
	public static final String VIOLATION_CLOSED = "OPENING_HOURS_CLOSED";

	/** 방문 시각이 없는 항목이 있어 그 항목만은 판정하지 못했다. */
	public static final String REASON_NO_ITEM_TIME = "NO_ITEM_TIME";

	private final OpeningHoursFilterPort openingHours;

	/**
	 * 방문 시각을 절대 시각으로 바꿀 때 쓰는 시간대. 일정의 시각은 날짜와 시:분으로만 저장돼
	 * 있어서 어느 지역의 시각인지가 값에 없다. 이 서비스는 국내 여행만 다루므로 응답의 다른
	 * 시각들과 같은 {@code Asia/Seoul} 로 읽는다.
	 */
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	public ItineraryOpeningHoursChecker(OpeningHoursFilterPort openingHours) {
		this.openingHours = openingHours;
	}

	/**
	 * 하루치 항목의 방문 시각을 영업시간과 대조한다.
	 *
	 * <p>판정 대상은 <b>방문 시각과 장소가 둘 다 있는 항목</b>뿐이다. 시각이 없는 항목은 언제
	 * 가는지가 정해지지 않았다는 뜻이라 문이 열렸는지 물어볼 수가 없다 — 그런 항목이 하나라도
	 * 있으면 {@link Result#notChecked()} 에 그 사실을 적는다.
	 *
	 * @param items    새 순서가 적용된 <b>일정 전체</b>의 항목. 이 메서드가 그중 그날만 고른다
	 * @param dayIndex 며칠째
	 */
	public Result checkDay(List<ItineraryItem> items, int dayIndex) {
		if (!this.openingHours.isAvailable()) {
			// 🔴 isOpenAt 을 부르지 않는다. 그 구현은 불리면 예외를 던지기로 약속했고,
			//    "모른다" 를 "열려 있다" 로 바꾸지 않는 것이 그 약속의 요점이다.
			return new Result(List.of(),
					List.of(new NotChecked(CHECK, this.openingHours.unavailableReason())));
		}

		List<Violation> violations = new ArrayList<>();
		Set<String> reasons = new LinkedHashSet<>();

		for (ItineraryItem item : items) {
			if (item.dayIndex() != dayIndex) {
				continue;
			}
			LocalTime startTime = item.startTime();
			String placeId = item.placeId();
			if (startTime == null || placeId == null) {
				reasons.add(REASON_NO_ITEM_TIME);
				continue;
			}

			OffsetDateTime at = item.visitDate().atTime(startTime).atZone(ZONE).toOffsetDateTime();
			if (!this.openingHours.isOpenAt(UUID.fromString(placeId), at)) {
				violations.add(new Violation(VIOLATION_CLOSED, item.itemKey(), placeId, at.toString()));
			}
		}

		List<NotChecked> notChecked = reasons.stream()
				.map((reason) -> new NotChecked(CHECK, reason))
				.toList();
		return new Result(List.copyOf(violations), notChecked);
	}

	/**
	 * 판정 결과.
	 *
	 * @param violations 어긴 항목들. 비어 있는 것은 <b>"봤고 문제 없음"</b> 이다
	 * @param notChecked 못 한 검사와 그 이유. 비어 있지 않으면 위 목록이 빈 것을 "문제 없음" 으로
	 *                   읽어서는 안 된다
	 */
	public record Result(List<Violation> violations, List<NotChecked> notChecked) {

		/** 이 편집에서 영업시간을 아예 보지 않았다는 뜻. 응답에서 두 칸이 함께 비게 된다. */
		public static Result notEvaluated() {
			return new Result(List.of(), List.of());
		}
	}

	/**
	 * @param code    위반의 종류
	 * @param itemKey 어긴 항목. 판이 바뀌어도 같은 항목을 가리키는 값이다
	 * @param placeId 그 항목의 장소
	 * @param at      어긴 시각. ISO-8601 + 시간대
	 */
	public record Violation(String code, String itemKey, String placeId, String at) {
	}

	/**
	 * @param check  못 한 검사의 이름
	 * @param reason 왜 못 했나
	 */
	public record NotChecked(String check, String reason) {
	}
}
