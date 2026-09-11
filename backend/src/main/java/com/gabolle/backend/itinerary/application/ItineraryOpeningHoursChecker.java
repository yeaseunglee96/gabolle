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
 * 영업시간이 <b>일부 장소에만</b> 있다. S15P21E201-852 가 관광공사 자료에서 268곳을 넣었고
 * 상가정보 2,355곳에는 아직 없다. 그래서 문({@link OpeningHoursFilterPort})의 답이 장소마다
 * 다르다 — 연다 · 닫는다 · 모른다.
 *
 * <p>모르는 것을 위반 없음으로 접으면 <b>틀린 안심</b>을 준다. 화면은 "영업시간 확인했고 문제
 * 없음" 과 "영업시간을 볼 수 없었음" 을 구분할 수 없게 된다. 그래서 위반 목록과 별도로
 * {@link Result#notChecked()} 에 <b>무슨 검사를 왜 못 했는지</b>를 담아 올린다. 같은 어휘를
 * 장소 후보 조회가 이미 쓰고 있다({@code PlaceCandidateResponse.notApplied} — 값도
 * {@code OPENING_HOURS} · {@code NOT_COLLECTED} 로 같다).
 *
 * <p>하루치 항목 하나하나에 문을 따로 묻는다. 항목이 한 자리 수라 질의 수가 문제가 되지
 * 않는 자리이고, 대신 <b>항목마다 다른 시각</b>을 물을 수 있다. 후보 목록처럼 대상이 수백인
 * 자리는 한 번에 읽어 둔 피처로 직접 판정한다({@code PlaceCandidateQueryService}).
 *
 * <h2>부르는 자리</h2>
 * 일정 편집 다섯 경로가 전부 이것을 지난다(S15P21E201-858) — 순서 바꾸기·장소 더하기·재계획은
 * 요청에 날짜가 있어 {@link #checkDay}, 고정·해제는 그 항목의 날짜로 {@link #checkDay},
 * 되돌리기는 판 전체가 바뀌므로 {@link #checkAll} 이다. 예전에는 순서 바꾸기 하나만 지났고
 * 나머지 넷은 빈 결과를 보내 화면이 그것을 "확인했고 문제 없음" 으로 읽었다.
 */
@Component
@Profile({ "db", "dev" })
public class ItineraryOpeningHoursChecker {

	/** 응답에 실리는 검사 이름. 장소 후보 조회가 쓰는 값과 같다. */
	public static final String CHECK = OpeningHoursFilterPort.CHECK;

	/** 위반 하나의 종류 — "그 시각에 그 집은 문을 닫는다". */
	public static final String VIOLATION_CLOSED = "OPENING_HOURS_CLOSED";

	/** 방문 시각이 없는 항목이 있어 그 항목만은 판정하지 못했다. */
	public static final String REASON_NO_ITEM_TIME = "NO_ITEM_TIME";

	/** 그 장소의 영업시간을 아직 아무도 안 넣어서 판정하지 못했다. */
	public static final String REASON_NOT_COLLECTED = OpeningHoursFilterPort.REASON_NOT_COLLECTED;

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
			// 🔴 세 갈래를 그대로 옮긴다. 모른다를 위반으로도, 통과로도 접지 않는다.
			switch (this.openingHours.openAt(UUID.fromString(placeId), at)) {
				case CLOSED ->
					violations.add(new Violation(VIOLATION_CLOSED, item.itemKey(), placeId, at.toString()));
				case NOT_COLLECTED -> reasons.add(REASON_NOT_COLLECTED);
				case OPEN -> {
					// 봤고 문제 없다. 적을 것이 없다.
				}
			}
		}

		List<NotChecked> notChecked = reasons.stream()
				.map((reason) -> new NotChecked(CHECK, reason))
				.toList();
		return new Result(List.copyOf(violations), notChecked);
	}

	/**
	 * 일정 전체를 날짜별로 판정해 하나로 합친다 — S15P21E201-858.
	 *
	 * <p>되돌리기처럼 <b>한 날이 아니라 판 전체</b>가 바뀌는 편집이 쓴다. 되살린 판의 위반이
	 * 어느 날에 있을지 모르므로 한 날만 보는 것으로는 빠뜨린다.
	 *
	 * <p>위반은 이어 붙이고 못 한 검사는 <b>이유별로 한 번만</b> 남긴다. 사흘 모두 영업시간을
	 * 모른다고 세 줄을 올리면 화면이 같은 문구를 세 번 보여 준다 — 사용자가 알아야 하는 것은
	 * "몇 번 못 봤나" 가 아니라 "무엇을 못 봤나" 다.
	 */
	public Result checkAll(List<ItineraryItem> items) {
		List<Integer> days = items.stream().map(ItineraryItem::dayIndex).distinct().sorted().toList();
		List<Violation> violations = new ArrayList<>();
		Set<String> reasons = new LinkedHashSet<>();
		for (int dayIndex : days) {
			Result result = checkDay(items, dayIndex);
			violations.addAll(result.violations());
			result.notChecked().forEach((notChecked) -> reasons.add(notChecked.reason()));
		}
		return new Result(List.copyOf(violations),
				reasons.stream().map((reason) -> new NotChecked(CHECK, reason)).toList());
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
