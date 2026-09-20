package com.gabolle.backend.itinerary.application.port;

import com.gabolle.backend.itinerary.domain.ItineraryItem;

/**
 * 구간 하나의 이동 거리·시간과 그 값을 얼마나 믿을 수 있는지.
 * 참·거짓 대신 {@link ItineraryItem.DataStatus} 를 쓴다. "어림잡았다" 와 "좌표가 없어 아무것도
 * 못 쟀다" 는 화면이 서로 다르게 그려야 하는 상태다 — 앞은 "예상 25분" 을 띄우고 뒤는 아무것도
 * 안 띄운다.
 *
 * @param distanceM 미터. 모르면 {@code null}
 * @param durationMin 분. 모르면 {@code null}
 * @param dataStatus {@code VERIFIED} 길찾기 실제 응답 · {@code ESTIMATED} 직선거리 어림값 ·
 *        {@code UNKNOWN} 잴 수 없었다
 * @param fareKrw 이 구간의 이동 요금(원). 모르면 {@code null} 이고 그것이 대부분이다
 */
public record TravelTime(Integer distanceM, Integer durationMin, ItineraryItem.DataStatus dataStatus,
		Integer fareKrw) {

	/**
	 * 요금 없이 만든다. 도보처럼 요금이라는 것이 아예 없는 이동수단이 이 자리를 쓴다.
	 */
	public TravelTime(Integer distanceM, Integer durationMin, ItineraryItem.DataStatus dataStatus) {
		this(distanceM, durationMin, dataStatus, null);
	}

	/** 좌표가 없거나 계산할 수 없었다. {@code 0} 이 아니라 {@code null} 이다. */
	public static TravelTime unknown() {
		return new TravelTime(null, null, ItineraryItem.DataStatus.UNKNOWN, null);
	}

	public boolean known() {
		return this.dataStatus != ItineraryItem.DataStatus.UNKNOWN;
	}

	/**
	 * 요금을 모르는 것과 요금이 0원인 것은 다르다.
	 * {@code null} 은 "얼마인지 모른다" 이고 {@code 0} 은 "공짜다" 다. 화면이 둘을 같게 그리면 요금
	 * 출처가 없는 이동수단이 전부 「무료」로 보인다. 채우는 쪽은 확실할 때만 값을 넣고 나머지는 비운다.
	 */
	public boolean hasFare() {
		return this.fareKrw != null;
	}
}
