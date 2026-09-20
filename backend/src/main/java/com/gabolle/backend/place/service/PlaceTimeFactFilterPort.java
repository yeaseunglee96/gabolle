package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 그 시각이 브레이크타임에 걸리는가 · 라스트오더를 지났는가.
 *
 * <p>{@link OpeningHoursFilterPort} 와 같이 셋 중 하나로 답한다 — 연다 · 걸린다 · 모른다.
 * 값이 있는 장소가 일부뿐이라 "모른다" 를 둘 중 하나로 접으면 안 된다.
 */
public interface PlaceTimeFactFilterPort {

	/** 응답과 로그에 쓰는 검사 이름 — 브레이크타임. */
	String BREAK_TIME_CHECK = "BREAK_TIME";

	/** 응답과 로그에 쓰는 검사 이름 — 라스트오더. */
	String LAST_ORDER_CHECK = "LAST_ORDER_TIME";

	/** 그 장소의 값을 아직 아무도 안 넣었거나 값을 못 읽었다는 뜻. */
	String REASON_NOT_COLLECTED = OpeningHoursFilterPort.REASON_NOT_COLLECTED;

	/**
	 * {@link OpeningHoursFilterPort#openAt} 과 같이 장소 하나씩 묻는다 — 하루치 일정 항목처럼
	 * 대상이 몇 개인 자리에서만 부른다.
	 */
	OpeningHoursFilterPort.Answer breakTimeAt(UUID placeId, OffsetDateTime at);

	OpeningHoursFilterPort.Answer lastOrderAt(UUID placeId, OffsetDateTime at);
}
