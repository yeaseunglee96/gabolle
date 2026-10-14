package com.gabolle.backend.facility.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 주차장 요금표와 예상요금 (S15P21E201-2052).
 *
 * <p>요금표는 「기본 {@code baseMinutes}분에 {@code baseWon}원, 그 뒤로 {@code addMinutes}분마다 {@code addWon}원, 하루 최대
 * {@code dayMaxWon}원, 처음 {@code freeMinutes}분은 무료」 한 벌이다. 칸이 비어 있으면(null) <b>모르는 것</b>이다 — 0 으로 읽지 않는다.
 *
 * <h2>🔴 모르는 값을 0원으로 읽지 않는다</h2>
 * 원천 자료는 요금을 모를 때 칸을 비우지 않고 0 이나 정수 최댓값(2147483647)을 넣은 줄이 많다. 그대로 계산하면 요금을 모르는 주차장이
 * 「0원」이 되어 <b>가장 싼 곳으로 맨 위에 뜬다.</b> 그래서 적재기가 {@link #normalize} 로 이상한 값을 null 로 비우고, 계산은 비운 칸을
 * 만나면 숫자를 지어내지 않고 {@code null}(요금 정보 없음)을 돌려준다.
 *
 * <p>추가요금({@code addWon})만 비어 있는 곳도 같다. 「기본 30분 300원」만 알고 그 뒤를 모르면, 30분을 넘는 체류의 요금은 모르는 것이다
 * (같은 요금표를 쓰는 다른 주차장은 추가요금이 있고, 일 최대 2,400원이 있다는 것 자체가 추가요금이 0 이 아니라는 뜻이다). 모른다고 말한다.
 */
public record ParkingFee(Integer baseMinutes, Integer baseWon, Integer addMinutes, Integer addWon, Integer dayMaxWon,
		Integer freeMinutes) {

	/** 예상요금을 셀 수 있는 가장 긴 체류 — 하루. 일 최대 요금이 하루 단위라 더 길면 모델이 맞지 않는다. */
	public static final int MAX_STAY_MINUTES = 24 * 60;

	/** 30분 기준 기본요금이 이보다 크면 월정기권(수만 원) 같은 다른 값이 섞인 것이다. 실제 자료의 가장 큰 정상 값은 2,100원. */
	static final int MAX_PLAUSIBLE_BASE_WON = 10_000;

	static final int MAX_PLAUSIBLE_ADD_WON = 10_000;

	static final int MAX_PLAUSIBLE_DAY_MAX_WON = 200_000;

	/** 이상한 값을 비운 이유 — 적재기 로그가 이 낱말로 센다. */
	public static final String NOTE_BASE_WON_ZERO = "BASE_WON_ZERO";

	public static final String NOTE_BASE_WON_IMPLAUSIBLE = "BASE_WON_IMPLAUSIBLE";

	public static final String NOTE_BASE_MISSING = "BASE_MISSING";

	public static final String NOTE_ADD_WON_ZERO = "ADD_WON_ZERO";

	public static final String NOTE_ADD_WON_IMPLAUSIBLE = "ADD_WON_IMPLAUSIBLE";

	public static final String NOTE_DAY_MAX_IMPLAUSIBLE = "DAY_MAX_IMPLAUSIBLE";

	/** 원천 값을 다듬은 결과. {@code fee} 가 null 이면 요금표를 쓸 수 없다. {@code notes} 는 비운 칸의 이유. */
	public record Normalized(ParkingFee fee, List<String> notes) {
	}

	/**
	 * 원천의 숫자 여섯을 다듬는다. 이상한 값은 null 로 비운다.
	 *
	 * <ul>
	 *   <li>기본요금이 없거나 0 이거나 {@value #MAX_PLAUSIBLE_BASE_WON}원을 넘으면 요금표 전체를 쓸 수 없다 → {@code fee} null</li>
	 *   <li>추가요금이 0 이거나 이상하게 크면 그 칸만 null — 기본요금까지는 아는 것이다</li>
	 *   <li>하루 최대가 이상하게 크거나 기본요금보다 작으면 그 칸만 null</li>
	 * </ul>
	 */
	public static Normalized normalize(Integer baseMinutes, Integer baseWon, Integer addMinutes, Integer addWon,
			Integer dayMaxWon, Integer freeMinutes) {
		List<String> notes = new ArrayList<>();
		if (baseWon == null || baseMinutes == null || baseMinutes <= 0 || baseMinutes > MAX_STAY_MINUTES) {
			notes.add(NOTE_BASE_MISSING);
			return new Normalized(null, notes);
		}
		if (baseWon <= 0) {
			notes.add(NOTE_BASE_WON_ZERO);
			return new Normalized(null, notes);
		}
		if (baseWon > MAX_PLAUSIBLE_BASE_WON) {
			notes.add(NOTE_BASE_WON_IMPLAUSIBLE);
			return new Normalized(null, notes);
		}
		Integer add = addWon;
		if (add != null && add <= 0) {
			notes.add(NOTE_ADD_WON_ZERO);
			add = null;
		}
		else if (add != null && add > MAX_PLAUSIBLE_ADD_WON) {
			notes.add(NOTE_ADD_WON_IMPLAUSIBLE);
			add = null;
		}
		Integer addMin = addMinutes != null && addMinutes > 0 && addMinutes <= MAX_STAY_MINUTES ? addMinutes : null;
		Integer dayMax = dayMaxWon;
		if (dayMax != null && (dayMax < baseWon || dayMax > MAX_PLAUSIBLE_DAY_MAX_WON)) {
			notes.add(NOTE_DAY_MAX_IMPLAUSIBLE);
			dayMax = null;
		}
		Integer free = freeMinutes != null && freeMinutes >= 0 && freeMinutes <= MAX_STAY_MINUTES ? freeMinutes : null;
		return new Normalized(new ParkingFee(baseMinutes, baseWon, addMin, add, dayMax, free), notes);
	}

	/**
	 * {@code stayMinutes} 분 머물렀을 때의 예상요금(원). 셀 수 없으면 {@code null}.
	 *
	 * <p>순서: 무료 시간을 뺀다 → 남은 시간이 없으면 0원 → 기본 시간까지는 기본요금 → 그 뒤는 추가 단위마다 추가요금(남는 시간은 한
	 * 단위로 올림) → 하루 최대로 자른다. 기본 시간을 넘는데 추가요금을 모르면 {@code null} 이다.
	 */
	public Integer estimate(int stayMinutes) {
		if (this.baseWon == null || this.baseMinutes == null) {
			return null;
		}
		int stay = Math.max(0, Math.min(stayMinutes, MAX_STAY_MINUTES));
		int billable = stay - (this.freeMinutes == null ? 0 : this.freeMinutes);
		if (billable <= 0) {
			return 0;
		}
		long total = this.baseWon;
		if (billable > this.baseMinutes) {
			if (this.addWon == null || this.addMinutes == null) {
				return null;
			}
			long units = (billable - this.baseMinutes + this.addMinutes - 1L) / this.addMinutes;
			total += units * this.addWon;
		}
		if (this.dayMaxWon != null) {
			total = Math.min(total, this.dayMaxWon);
		}
		return (int) total;
	}

	/** 저장 모양 — 모르는 칸은 null 로 둔다. */
	public Map<String, Object> toDetail() {
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("baseMinutes", this.baseMinutes);
		out.put("baseWon", this.baseWon);
		out.put("addMinutes", this.addMinutes);
		out.put("addWon", this.addWon);
		out.put("dayMaxWon", this.dayMaxWon);
		out.put("freeMinutes", this.freeMinutes);
		return out;
	}

	/** 저장된 모양을 읽는다. 객체가 아니거나 기본요금을 모르면 {@code null} — 요금표를 쓸 수 없다. */
	public static ParkingFee fromDetail(Object node) {
		if (!(node instanceof Map<?, ?> map)) {
			return null;
		}
		ParkingFee fee = new ParkingFee(intOf(map.get("baseMinutes")), intOf(map.get("baseWon")),
				intOf(map.get("addMinutes")), intOf(map.get("addWon")), intOf(map.get("dayMaxWon")),
				intOf(map.get("freeMinutes")));
		return fee.baseWon() == null || fee.baseMinutes() == null ? null : fee;
	}

	private static Integer intOf(Object value) {
		return value instanceof Number number ? Integer.valueOf(number.intValue()) : null;
	}
}
