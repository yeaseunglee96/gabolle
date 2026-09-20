package com.gabolle.backend.place.service;

import java.util.UUID;

/**
 * 그 장소가 지정된 일정에 들어 있는가.
 *
 * <p>여행이 아니라 일정을 받는다. 한 여행이 일정을 여럿 가질 수 있어서, 여행 식별자만 받으면
 * 어느 일정을 볼지 이 조회가 몰래 정하게 된다. 일정을 지정하지 않는 것도 정상적인 요청이고
 * ({@link #REASON_NOT_SPECIFIED}) 오류가 아니다.
 *
 * <p>볼 수 없는 일정에는 404·403 이 아니라 {@code UNAVAILABLE} 로 답한다. 응답 코드가 갈리면
 * 식별자를 넣어 보는 것만으로 남의 일정의 존재와 내용을 알아낼 수 있다. 같은 이유로
 * {@link #REASON_NOT_VISIBLE} 하나가 없는 일정과 남의 일정을 함께 맡는다 — 둘을 나누면 방금
 * 막은 것이 {@code reason} 문자열로 되살아난다.
 *
 * <p>포트를 쓰는 쪽({@code place})에 두는 이유는 일정 도메인의 저장소를 직접 부르지 않기
 * 위해서다. 구현은 {@code itinerary} 쪽에 있고, 일정 도메인 타입은 하나도 노출하지 않는다.
 * 구현이 빈으로 없는 컨텍스트에서는 {@link #REASON_LOOKUP_UNAVAILABLE} 이 답이다.
 */
public interface ItineraryMembershipPort {

	/** 요청이 일정을 지정하지 않았다. 오류가 아니므로 화면은 포함 여부 표시만 감춘다. */
	String REASON_NOT_SPECIFIED = "ITINERARY_NOT_SPECIFIED";

	/** 그 일정을 이 요청자에게 보여줄 수 없다. 없는 일정과 남의 일정이 같은 값을 쓴다. */
	String REASON_NOT_VISIBLE = "ITINERARY_NOT_VISIBLE";

	/**
	 * 서버가 지금 판정할 수 없다. 요청자·일정과 무관한 사정이라 이 값으로는 아무것도 알아낼 수
	 * 없다 — 일정 조회 구현이 없거나, 최신 판을 읽지 못한 경우다.
	 */
	String REASON_LOOKUP_UNAVAILABLE = "ITINERARY_LOOKUP_UNAVAILABLE";

	/**
	 * @param userId {@code null} 이면 권한을 판정할 수 없어 {@link #REASON_NOT_VISIBLE}
	 * @param itineraryId {@code null} 이면 {@link #REASON_NOT_SPECIFIED}
	 */
	Inclusion inclusionOf(UUID userId, UUID itineraryId, UUID placeId);

	/**
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
