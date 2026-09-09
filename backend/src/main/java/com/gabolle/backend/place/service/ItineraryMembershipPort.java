package com.gabolle.backend.place.service;

import java.util.UUID;

/**
 * 그 장소가 <b>지정된 일정</b>에 들어 있는가 — S15P21E201-476 의 마지막 완료 기준.
 *
 * <h2>왜 여행이 아니라 일정을 지정받는가</h2>
 *
 * 포함 여부는 "그 여행 어딘가에 있나" 가 아니라 "그 일정의 최신 판에 있나" 다. 한 여행은 일정을
 * 여럿 가질 수 있고({@code ix_itinerary_trip} 이 그것을 허용한다), 여행 식별자만 받으면 그중 어느
 * 일정을 볼지 <b>이 조회가 몰래 정하게 된다.</b> 화면이 보고 있는 일정과 서버가 고른 일정이 다르면
 * 사용자는 "방금 넣었는데 안 넣었다고 나온다" 를 보게 되고, 그 어긋남은 응답만 봐서는 알 수 없다.
 * 그래서 어느 일정인지는 부르는 쪽이 정한다.
 *
 * <p>일정을 지정하지 않는 것도 정상적인 요청이다 — 여행 맥락 없이 장소만 열어 보는 화면이 있다.
 * 그때는 {@link #REASON_NOT_SPECIFIED} 를 담은 {@code UNAVAILABLE} 이 답이고, 그것은 오류가 아니다.
 *
 * <h2>🔴 왜 볼 수 없는 일정에 오류가 아니라 UNAVAILABLE 로 답하는가</h2>
 *
 * 남의 일정 식별자를 넣어 봤을 때 {@code INCLUDED} 와 {@code NOT_INCLUDED} 가 갈리면, 그 차이만으로
 * 남의 일정에 어떤 장소가 들어 있는지 하나씩 알아낼 수 있다. 그래서 요청자가 그 일정을 볼 수 있는
 * 사람인지 먼저 확인하고, 아니면 판정하지 않는다.
 *
 * <p>확인에 실패했을 때 404·403 을 던지지 않는 이유가 둘이다. 하나는 이 엔드포인트의 본체가 장소
 * 정보라는 것이다 — 포함 여부 한 칸을 모른다고 장소 상세 화면 전체를 깨뜨릴 이유가 없다. 다른
 * 하나가 더 중요하다. <b>응답 코드 자체가 "그 일정이 존재하는가" 를 알려주는 신호가 된다.</b>
 * 없는 일정에 404, 남의 일정에 403 을 주면 식별자를 넣어 보는 것만으로 존재 여부를 가릴 수 있다.
 *
 * <p>같은 이유로 {@link #REASON_NOT_VISIBLE} 하나가 <b>없는 일정과 남의 일정을 함께</b> 맡는다.
 * 두 경우를 {@code reason} 문자열로 나누면 방금 막은 것이 그 문자열로 되살아난다.
 *
 * <h2>왜 포트(port)인가 — 부르는 쪽이 인터페이스를 정의한다</h2>
 *
 * 포트는 "이 기능이 밖에 무엇을 요구하는가" 를 요구하는 쪽 언어로 적어 둔 인터페이스다. 장소
 * 조회는 일정에 무엇이 담겼는지 알아야 하지만 일정 도메인의 저장소를 직접 부르면 안 된다 —
 * 백엔드 README 의 "다른 기능의 Repository 를 직접 호출하지 않습니다" 다. 그래서 인터페이스는
 * 쓰는 쪽({@code place})에 두고 구현은 제공하는 쪽({@code itinerary.application
 * .ItineraryPlaceMembershipService})에 둔다. {@code recommendation.application.port
 * .ItineraryDraftPort} 가 이미 같은 모양이다.
 *
 * <p>이 인터페이스는 일정 도메인의 타입을 하나도 노출하지 않는다. {@link UUID} 와
 * {@link Inclusion} 뿐이라 {@code place} 는 일정의 판·항목 구조를 몰라도 된다.
 *
 * <p>구현이 없는 컨텍스트도 있다 — {@code PlaceSliceApplication} 은 {@code common}·{@code place}
 * 만 스캔하므로 이 포트의 구현이 빈으로 없다. 그때 {@code PlaceDetailService} 는
 * {@link #REASON_LOOKUP_UNAVAILABLE} 로 답한다. 근거는 {@code PlaceDetailService.inclusionOf}
 * 주석에 적어 뒀다.
 */
public interface ItineraryMembershipPort {

	/**
	 * 요청이 일정을 지정하지 않았다. 여행 맥락 없이 장소를 열어 본 <b>정상적인</b> 경우이므로
	 * 화면은 이것을 오류로 그리지 않고 포함 여부 표시만 감춘다.
	 */
	String REASON_NOT_SPECIFIED = "ITINERARY_NOT_SPECIFIED";

	/**
	 * 그 일정을 이 요청자에게 보여줄 수 없다. <b>없는 일정과 남의 일정이 같은 값을 쓴다</b> —
	 * 둘을 나누면 이 칸이 존재 여부를 알려주는 신호가 된다.
	 */
	String REASON_NOT_VISIBLE = "ITINERARY_NOT_VISIBLE";

	/**
	 * 서버가 지금 판정할 수 없다. 요청자·일정과 <b>무관한</b> 사정이라 이 값으로는 아무것도
	 * 알아낼 수 없다 — 이 배포에 일정 조회 구현이 없거나, 최신 판 포인터가 가리키는 판을 읽지
	 * 못한 경우다.
	 */
	String REASON_LOOKUP_UNAVAILABLE = "ITINERARY_LOOKUP_UNAVAILABLE";

	/**
	 * @param userId 요청자. {@code null} 이면 누구인지 모르니 권한을 판정할 수 없어
	 *        {@link #REASON_NOT_VISIBLE} 이다
	 * @param itineraryId 어느 일정에 대해 묻는가. {@code null} 이면
	 *        {@link #REASON_NOT_SPECIFIED}
	 * @param placeId 이 장소가 그 일정에 있는가
	 */
	Inclusion inclusionOf(UUID userId, UUID itineraryId, UUID placeId);

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
