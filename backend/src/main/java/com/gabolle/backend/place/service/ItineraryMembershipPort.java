package com.gabolle.backend.place.service;

import java.util.UUID;

/**
 * 이 장소가 그 사용자의 일정에 들어 있는가.
 *
 * <h2>🔴 왜 포트인가 — 지금은 답할 수 없기 때문이다</h2>
 *
 * S15P21E201-476 의 완료 기준에 "일정 포함 여부가 응답에 있다" 가 있다. 그런데 지금 스키마에는
 * <b>일정에 장소를 담는 항목 표가 없다.</b> {@code itineraries} 와 {@code itinerary_versions} 둘뿐이고
 * (V20260903150000), 어느 장소가 어느 날 몇 번째에 있는지를 적는 곳이 아직 없다.
 *
 * <p>그래서 {@code false} 를 돌려주면 거짓말이다. "확실히 안 들어 있다" 와 "알 수 없다" 는 다른
 * 말이고, 화면은 그 둘을 다르게 그려야 한다. 항목 표가 생기기 전까지는 <b>모른다고 말한다.</b>
 *
 * <p>같은 판단이 {@code RecommendationEnginePort} 에도 있다. 그 클래스 주석이 이유를 이렇게 적어
 * 뒀다 — "지어낸 구현은 진짜 계약이 오면 전부 버려지는데, 그 사이에 그것을 진짜라고 믿는 코드가
 * 붙는다." 항목 표가 생기면 이 포트의 구현체 하나만 갈아 끼우면 되고, 응답 계약은 안 바뀐다.
 */
public interface ItineraryMembershipPort {

	Inclusion inclusionOf(UUID userId, UUID placeId);

	/**
	 * 일정 포함 여부.
	 *
	 * @param state {@code INCLUDED} · {@code NOT_INCLUDED} · {@code UNAVAILABLE}
	 * @param reason {@code UNAVAILABLE} 일 때 왜 알 수 없는지. 그 밖에는 {@code null}
	 */
	record Inclusion(String state, String reason) {

		public static Inclusion unavailable(String reason) {
			return new Inclusion("UNAVAILABLE", reason);
		}

		public static Inclusion included() {
			return new Inclusion("INCLUDED", null);
		}

		public static Inclusion notIncluded() {
			return new Inclusion("NOT_INCLUDED", null);
		}
	}
}
