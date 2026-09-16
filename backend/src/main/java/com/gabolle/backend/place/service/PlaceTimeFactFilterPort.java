package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 그 시각이 브레이크타임에 걸리는가 · 라스트오더를 지났는가 — S15P21E201-94.
 *
 * <p>{@link OpeningHoursFilterPort} 와 같은 이유로 셋 중 하나로 답한다 — 연다 · 걸린다 · 모른다.
 * {@code BREAK_TIME}·{@code LAST_ORDER_TIME} 값은 관광공사 자료에서 옮긴 일부 장소에만 있다
 * ({@code OpeningHoursLoader} 부류가 채운다). 값이 없는 곳을 "걸린다" 로 접으면 아직 안 채운
 * 장소가 전부 일정에서 빠지고, "안 걸린다" 로 접으면 모른다가 조용히 안전한 것이 된다 —
 * 둘 다 사용자에게 거짓말이다.
 */
public interface PlaceTimeFactFilterPort {

	/** 응답과 로그에 쓰는 검사 이름 — 브레이크타임. */
	String BREAK_TIME_CHECK = "BREAK_TIME";

	/** 응답과 로그에 쓰는 검사 이름 — 라스트오더. */
	String LAST_ORDER_CHECK = "LAST_ORDER_TIME";

	/** 그 장소의 값을 아직 아무도 안 넣었거나 값을 못 읽었다는 뜻. */
	String REASON_NOT_COLLECTED = OpeningHoursFilterPort.REASON_NOT_COLLECTED;

	/**
	 * 그 시각이 브레이크타임 구간 안인가.
	 *
	 * <p>🔴 {@link OpeningHoursFilterPort#openAt} 과 같은 이유로 장소 하나를 묻는다 — 하루치
	 * 일정 항목처럼 대상이 몇 개인 자리에서만 부른다.
	 */
	OpeningHoursFilterPort.Answer breakTimeAt(UUID placeId, OffsetDateTime at);

	/** 그 시각이 라스트오더를 지났는가. */
	OpeningHoursFilterPort.Answer lastOrderAt(UUID placeId, OffsetDateTime at);
}
