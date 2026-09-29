package com.gabolle.backend.itinerary.application.port;

import java.util.List;

import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;

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
 * @param path 이 구간이 지나는 길의 좌표 목록. {@code [경도, 위도]} 순서다 — GeoJSON·지도
 *        라이브러리와 같은 순서이고 {@code RouteLeg.path} 가 이미 그렇게 정해 뒀다.
 *        <b>어느 길로 가는지 모르면 {@code null}</b> 이고, 그때 출발·도착 두 점을 이어
 *        만든 직선을 넣지 않는다 — 그러면 실제로 잰 길과 구분이 사라져 화면이 직선을
 *        실선으로 그린다(S15P21E201-1251·1234)
 * @param pieces {@code path} 를 경사·계단이 같은 조각으로 나눈 것({@link ItineraryLeg.Piece}). 선형이 없거나 조각을
 *        모르면(자동차·대중교통·어림) {@code null} — 경사를 모르는 길을 평지로 지어내지 않는다
 */
public record TravelTime(Integer distanceM, Integer durationMin, ItineraryItem.DataStatus dataStatus,
		Integer fareKrw, List<double[]> path, List<ItineraryLeg.Piece> pieces) {

	/** 조각 없이 만든다 — 경사·계단 조각을 모르는 선형이다. */
	public TravelTime(Integer distanceM, Integer durationMin, ItineraryItem.DataStatus dataStatus,
			Integer fareKrw, List<double[]> path) {
		this(distanceM, durationMin, dataStatus, fareKrw, path, null);
	}

	/**
	 * 요금 없이 만든다. 도보처럼 요금이라는 것이 아예 없는 이동수단이 이 자리를 쓴다.
	 */
	public TravelTime(Integer distanceM, Integer durationMin, ItineraryItem.DataStatus dataStatus) {
		this(distanceM, durationMin, dataStatus, null, null);
	}

	/**
	 * 선형 없이 만든다. 이 자리를 쓰는 곳은 거리·시간만 아는 계산기들이다 — 선형을
	 * {@code null} 로 두는 것이 사실이다.
	 */
	public TravelTime(Integer distanceM, Integer durationMin, ItineraryItem.DataStatus dataStatus,
			Integer fareKrw) {
		this(distanceM, durationMin, dataStatus, fareKrw, null);
	}

	/** 좌표가 없거나 계산할 수 없었다. {@code 0} 이 아니라 {@code null} 이다. */
	public static TravelTime unknown() {
		return new TravelTime(null, null, ItineraryItem.DataStatus.UNKNOWN, null, null);
	}

	/**
	 * 그릴 수 있는 선형이 있는가. 점이 둘은 있어야 선이다 — 점 하나는 길이 아니고,
	 * 빈 목록은 「없다」와 같은 뜻인데 저장하면 「잰 결과가 비었다」로 읽힌다.
	 */
	public boolean hasPath() {
		return this.path != null && this.path.size() >= 2;
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
