package com.gabolle.backend.itinerary.application.port;

import com.gabolle.backend.itinerary.domain.ItineraryItem;

/**
 * 구간 하나의 이동 거리·시간과 <b>그 값을 얼마나 믿을 수 있는지</b> — S15P21E201-179.
 *
 * <p>🔴 참·거짓 대신 {@link ItineraryItem.DataStatus} 를 쓴다. "어림잡았다" 와 "좌표가 없어
 * 아무것도 못 쟀다" 는 화면이 서로 다르게 그려야 하는 상태다 — 앞은 "예상 25분" 을 띄우고
 * 뒤는 아무것도 안 띄운다. 참·거짓으로는 그 둘이 같아진다.
 *
 * @param distanceM 미터. 모르면 {@code null}
 * @param durationMin 분. 모르면 {@code null}
 * @param dataStatus {@code VERIFIED} 길찾기 실제 응답 · {@code ESTIMATED} 직선거리 어림값 ·
 *        {@code UNKNOWN} 잴 수 없었다
 */
public record TravelTime(Integer distanceM, Integer durationMin, ItineraryItem.DataStatus dataStatus) {

	/** 좌표가 없거나 계산할 수 없었다. 🔴 {@code 0} 이 아니라 {@code null} 이다. */
	public static TravelTime unknown() {
		return new TravelTime(null, null, ItineraryItem.DataStatus.UNKNOWN);
	}

	public boolean known() {
		return this.dataStatus != ItineraryItem.DataStatus.UNKNOWN;
	}
}
