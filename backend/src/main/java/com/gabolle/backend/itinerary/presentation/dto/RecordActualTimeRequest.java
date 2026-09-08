package com.gabolle.backend.itinerary.presentation.dto;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * 방문지 실제 도착·출발 시각 요청 본문 — S15P21E201-293.
 * {@code PUT /api/v1/itineraries/{itineraryId}/items/{itemKey}/actual}.
 *
 * <p>🔴 두 칸 모두 {@link OffsetDateTime} 이다 — 문자열로 받아 나중에 파싱하면 <b>시간대
 * 없는 값이 조용히 UTC 로 해석된다.</b> 부산에서 09:00 에 도착한 것이 18:00 으로 적히고
 * 아무도 모른다({@code IngestEventRequest} 가 같은 이유로 같은 타입을 쓴다).
 *
 * <p>{@code baseVersion} 이 없다. 이 기록은 판에 실리지 않아서 낡은 판을 보고 있어도
 * 어긋나지 않는다 — 편집 요청과 갈리는 지점이고 근거는
 * {@code ItineraryActualTimeService} 클래스 주석에 있다.
 *
 * <p>둘 중 하나만 보낼 수 있다. 둘 다 비었거나 출발이 도착보다 앞서면 400 이다 — 그 판정은
 * Bean Validation 으로 적을 수 없어({@code @NotNull} 을 어느 칸에도 걸 수 없다) 도메인
 * {@code ItineraryItemActual} 생성자가 한다.
 *
 * <p>보낸 것이 그 방문지의 최종 상태다. 도착만 보내면 출발은 {@code null} 이 된다.
 */
public record RecordActualTimeRequest(OffsetDateTime arrivedAt, OffsetDateTime departedAt) {

	public Instant arrivedAtInstant() {
		return this.arrivedAt == null ? null : this.arrivedAt.toInstant();
	}

	public Instant departedAtInstant() {
		return this.departedAt == null ? null : this.departedAt.toInstant();
	}
}
