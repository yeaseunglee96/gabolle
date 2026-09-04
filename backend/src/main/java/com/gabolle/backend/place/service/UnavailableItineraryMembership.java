package com.gabolle.backend.place.service;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 일정 포함 여부를 아직 알 수 없다고 답하는 구현.
 *
 * <p>일정 항목 표가 생기기 전까지 유일한 구현이다. 항목 표가 들어오면 그것을 읽는 구현을 하나
 * 더하고 이 클래스를 지우면 된다 — 응답 계약은 그대로다.
 *
 * <p>🔴 여기서 {@code NOT_INCLUDED} 를 돌려주지 않는 것이 핵심이다. 그러면 화면이 "이 장소는
 * 일정에 없다" 고 단정하게 되고, 나중에 항목 표가 생겨 실제로는 들어 있었다는 것이 드러나도
 * 그동안 사용자가 본 것은 되돌릴 수 없다.
 */
@Component
@Profile({"db", "dev"})
public class UnavailableItineraryMembership implements ItineraryMembershipPort {

	/** 왜 알 수 없는지. 화면과 다음 담당자가 같은 말을 보도록 코드로 남긴다. */
	static final String REASON_NO_ITEM_TABLE = "ITINERARY_ITEMS_NOT_STORED";

	@Override
	public Inclusion inclusionOf(UUID userId, UUID placeId) {
		return Inclusion.unavailable(REASON_NO_ITEM_TABLE);
	}
}
