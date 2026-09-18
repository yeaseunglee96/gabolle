package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 그 시각에 문을 여는가.
 *
 * <h2>🔴 답이 둘이 아니라 셋이다 — S15P21E201-852 에서 바뀌었다</h2>
 *
 * 예전에는 {@code isAvailable()} 로 <b>서비스 전체</b>가 영업시간을 보는지 아닌지를 먼저 묻고,
 * 그 뒤에 참·거짓을 물었다. {@code place} 표에 영업시간 칸이 없던 동안(S15P21E201-262 가 일부러
 * 비워 뒀다) 그 모양이 맞았다 — 아무 장소도 값이 없었으니 "전체가 모른다" 가 사실이었다.
 *
 * <p>이제 값이 <b>일부</b> 장소에만 있다. 관광공사 자료에서 정규화한 268곳이 들어가고, 이미
 * 적재된 상가정보 2,355곳에는 없다. 그 상태에서 참·거짓만으로 답하면 둘 중 하나를 지어내야 한다
 * — <b>거짓</b>이면 수집 안 된 곳이 전부 "문 닫았다" 가 되고, <b>참</b>이면 모른다가 조용히
 * "열려 있다" 가 된다. 둘 다 사용자에게 거짓말이다.
 *
 * <p>그래서 판정을 {@link Answer} 셋으로 넓혔다. 부르는 쪽은 {@link Answer#NOT_COLLECTED} 를
 * 받으면 그 사실을 응답에 적는다 — 응답에 쓰이는 낱말({@code OPENING_HOURS} ·
 * {@code NOT_COLLECTED})은 그대로라 화면은 고칠 것이 없다.
 */
public interface OpeningHoursFilterPort {

	/** 응답과 로그에 쓰는 검사 이름. 장소 후보 조회와 일정 편집이 같은 값을 쓴다. */
	String CHECK = "OPENING_HOURS";

	/** 그 장소의 영업시간을 아직 아무도 안 넣었다는 뜻. 응답에 이유로 그대로 실린다. */
	String REASON_NOT_COLLECTED = "NOT_COLLECTED";

	/**
	 * 그 시각에 여는가.
	 *
	 * <p>🔴 이 메서드는 <b>장소 하나</b>를 묻는다. 후보 목록처럼 장소가 수십·수백인 자리에서
	 * 이것을 돌려 부르면 질의가 장소 수만큼 나간다 — 그 자리
	 * ({@code PlaceCandidateQueryService})는 이미 한 번에 읽어 둔 피처를
	 * {@link OpeningHoursValue} 로 직접 판정하고 이 문을 부르지 않는다. 여기를 부르는 쪽은
	 * 하루치 일정 항목처럼 대상이 몇 개인 자리다.
	 */
	Answer openAt(UUID placeId, OffsetDateTime at);

	/** 연다 · 닫는다 · 모른다. 🔴 모른다를 나머지 둘 중 하나로 접지 않는 것이 이 타입의 요점이다. */
	enum Answer {

		/** 그 시각에 열려 있다. */
		OPEN,

		/** 그 시각에 닫는다고 원천이 말했다. */
		CLOSED,

		/** 그 장소의 영업시간을 모른다 — 행이 없거나, 값이 있어도 오늘을 판정할 수 없다. */
		NOT_COLLECTED
	}
}
