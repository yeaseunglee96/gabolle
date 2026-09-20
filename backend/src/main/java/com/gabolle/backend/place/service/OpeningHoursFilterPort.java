package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 그 시각에 문을 여는가. 답은 참·거짓이 아니라 {@link Answer} 셋이다.
 *
 * <p>영업시간 값이 일부 장소에만 있어서, 참·거짓으로만 답하면 수집 안 된 곳이 전부 "문 닫았다"
 * 아니면 "열려 있다" 로 지어진다. 부르는 쪽은 {@link Answer#NOT_COLLECTED} 를 받으면 그 사실을
 * 응답에 적는다.
 */
public interface OpeningHoursFilterPort {

	/** 응답과 로그에 쓰는 검사 이름. 장소 후보 조회와 일정 편집이 같은 값을 쓴다. */
	String CHECK = "OPENING_HOURS";

	/** 그 장소의 영업시간을 아직 아무도 안 넣었다는 뜻. 응답에 이유로 그대로 실린다. */
	String REASON_NOT_COLLECTED = "NOT_COLLECTED";

	/**
	 * 장소 하나를 묻는다. 후보 목록처럼 장소가 수십·수백인 자리에서 돌려 부르면 질의가 장소
	 * 수만큼 나간다 — 그런 자리는 한 번에 읽어 둔 피처를 {@link OpeningHoursValue} 로 직접
	 * 판정하고 이 문을 안 쓴다.
	 */
	Answer openAt(UUID placeId, OffsetDateTime at);

	/** 모른다를 나머지 둘 중 하나로 접지 않는 것이 이 타입의 요점이다. */
	enum Answer {

		OPEN,

		CLOSED,

		/** 행이 없거나, 값이 있어도 그날을 판정할 수 없다. */
		NOT_COLLECTED
	}
}
