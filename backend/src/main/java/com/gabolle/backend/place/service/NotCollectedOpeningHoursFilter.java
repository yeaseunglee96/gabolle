package com.gabolle.backend.place.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 영업시간을 아직 수집하지 않아 거를 수 없다고 답하는 구현.
 *
 * <p>영업시간 칸이 생기면 그것을 읽는 구현을 하나 더하고 이 클래스를 지운다. 응답 계약은 그대로다.
 *
 * <p>🔴 {@link #isOpenAt} 이 {@code true} 를 돌려주지 않는다. "모른다" 를 "열려 있다" 로 바꾸는
 * 순간 그 값이 진짜인지 아무도 구분할 수 없게 된다. {@link #isAvailable()} 이 거짓인 동안 이
 * 메서드는 불리면 안 되고, 불렸다면 그것 자체가 결함이다.
 */
@Component
@Profile({"db", "dev"})
public class NotCollectedOpeningHoursFilter implements OpeningHoursFilterPort {

	static final String REASON_NOT_COLLECTED = "NOT_COLLECTED";

	@Override
	public boolean isAvailable() {
		return false;
	}

	@Override
	public String unavailableReason() {
		return REASON_NOT_COLLECTED;
	}

	@Override
	public boolean isOpenAt(UUID placeId, OffsetDateTime at) {
		throw new IllegalStateException("영업시간을 수집하지 않았다. isAvailable() 을 먼저 확인해야 한다.");
	}
}
