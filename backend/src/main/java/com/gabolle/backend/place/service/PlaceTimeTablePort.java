package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 한 장소의 영업표 — 영업시간·브레이크타임·라스트오더를 한 번에 읽어 두고, 시각마다의 판정은 메모리에서 한다
 * (S15P21E201-1663).
 *
 * <p>{@link OpeningHoursFilterPort}·{@link PlaceTimeFactFilterPort} 는 묻는 시각마다 DB 를 읽는다. 일정 조립처럼 한 장소를
 * 여러 시각에 묻는 자리에서는 이 문을 쓴다. 답은 두 문과 같다 — 같은 행을 같은 판정 함수
 * ({@link OpeningHoursValue}·{@link TimeFactValue})로 읽는다.
 */
public interface PlaceTimeTablePort {

	/** 그 장소의 영업표. 값이 하나도 없는 장소도 표를 돌려준다 — 모든 시각에 「모른다」라고 답하는 표다. */
	PlaceTimeTable tableOf(UUID placeId);

	/** 셋 다 {@link OpeningHoursFilterPort.Answer} 세 갈래(연다·걸린다·모른다)로 답한다. */
	interface PlaceTimeTable {

		/** {@link OpeningHoursFilterPort#openAt} 과 같은 답. */
		OpeningHoursFilterPort.Answer openAt(OffsetDateTime at);

		/** {@link PlaceTimeFactFilterPort#breakTimeAt} 과 같은 답. */
		OpeningHoursFilterPort.Answer breakTimeAt(OffsetDateTime at);

		/** {@link PlaceTimeFactFilterPort#lastOrderAt} 과 같은 답. */
		OpeningHoursFilterPort.Answer lastOrderAt(OffsetDateTime at);
	}
}
