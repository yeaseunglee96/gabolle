package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 그 시각에 문을 여는가.
 *
 * <h2>🔴 왜 포트인가 — 영업시간 칸이 아직 없다</h2>
 *
 * {@code place} 표에 영업시간·휴무일 칸이 없다. S15P21E201-262 가 <b>일부러</b> 뺐다 — 담당 티켓
 * (-88·-97·-300 계열)이 값의 모양을 아직 안 정했는데 여기서 먼저 만들면 지어낸 기본값이 계약이
 * 되기 때문이다 (V20260904000000 주석 22~25행).
 *
 * <p>그래서 후보 조회가 {@code openNowAt} 을 받아도 지금은 거를 수 없다. 🔴 <b>조용히 무시하지
 * 않는다.</b> 무시하면 호출자는 걸러진 줄 알고 영업이 끝난 곳을 추천한다. 대신 응답의
 * {@code notApplied} 에 "이 조건은 적용하지 못했고 이유는 미수집" 이라고 적는다.
 */
public interface OpeningHoursFilterPort {

	/** 이 필터를 지금 적용할 수 있는가. 거짓이면 호출자가 응답에 못 걸렀다고 적는다. */
	boolean isAvailable();

	/** 적용할 수 없을 때 그 이유. 응답에 그대로 실린다. */
	String unavailableReason();

	/**
	 * 그 시각에 여는가. {@link #isAvailable()} 이 거짓이면 부르지 않는다.
	 */
	boolean isOpenAt(UUID placeId, OffsetDateTime at);
}
