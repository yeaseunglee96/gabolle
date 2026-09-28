package com.gabolle.backend.trip.support;

import com.gabolle.backend.trip.application.TripCreationService;

/** 여행 만들기 명령을 시험에서 다루는 도우미. */
public final class TripCommands {

	private TripCommands() {
	}

	/**
	 * 같은 명령에 숙소 동네(해운대)만 채운 사본. 1박 이상 여행은 숙소가 있어야 만들어진다(S15P21E201-1585) —
	 * 숙소가 아닌 것을 재는 시험이 그 규칙에 걸리지 않게 쓴다. 숙소 규칙 자체는 {@code TripConditionRulesTest} 가 잰다.
	 */
	public static TripCreationService.Command withLodging(TripCreationService.Command c) {
		return new TripCreationService.Command(c.userId(), c.startDate(), c.finishDate(), c.originLat(),
				c.originLng(), c.budgetKrw(), c.partySize(), c.timeWindow(), c.timezone(), c.preferences(),
				c.constraints(), c.ownerType(), c.accommodationPlaceId(), c.englishMenuRequired(),
				c.foreignCardRequired(), c.soloFriendlyPriority(), c.maxTransitTransfers(), c.mustVisitPlaceIds(),
				c.travelAreas(), c.accommodation(), "HAEUNDAE");
	}
}
