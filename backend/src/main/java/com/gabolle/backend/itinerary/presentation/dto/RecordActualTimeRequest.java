package com.gabolle.backend.itinerary.presentation.dto;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * 방문지 실제 도착·출발 시각 요청 본문.
 * 두 칸 모두 {@link OffsetDateTime} 이다 — 문자열로 받아 나중에 파싱하면 시간대 없는 값이
 * 조용히 UTC 로 해석되어, 부산에서 09:00 에 도착한 것이 18:00 으로 적히고 아무도 모른다.
 * {@code baseVersion} 이 없다. 이 기록은 판에 실리지 않아 낡은 판을 보고 있어도 어긋나지
 * 않는다 — 근거는 {@code ItineraryActualTimeService} 클래스 주석에 있다.
 * 둘 중 하나만 보낼 수 있다. 둘 다 비었거나 출발이 도착보다 앞서면 400 인데, 그 판정은 Bean
 * Validation 으로 적을 수 없어 도메인 {@code ItineraryItemActual} 생성자가 한다.
 * 보낸 것이 그 방문지의 최종 상태다. 도착만 보내면 출발은 {@code null} 이 된다.
 */
public record RecordActualTimeRequest(OffsetDateTime arrivedAt, OffsetDateTime departedAt) {

	public Instant arrivedAtInstant() {
		return this.arrivedAt == null ? null : this.arrivedAt.toInstant();
	}

	public Instant departedAtInstant() {
		return this.departedAt == null ? null : this.departedAt.toInstant();
	}
}
