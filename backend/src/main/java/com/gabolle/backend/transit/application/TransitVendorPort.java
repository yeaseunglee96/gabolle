package com.gabolle.backend.transit.application;

/**
 * TAGO(국토교통부 버스 정류소·도착정보) 호출 자리. 실패는 빈 값이 아니라 예외로 던진다 —
 * 벤더가 없는데 도착 시간을 지어내면 실시간 정보가 아니다.
 * 원문 JSON을 그대로 돌려주고 파싱은 부르는 쪽({@code TransitService})이 한다.
 */
public interface TransitVendorPort {

	/**
	 * @return TAGO {@code getCrdntPrxmtSttnList}(좌표기반근접정류소목록조회) 응답 원문(JSON)
	 * @throws TransitVendorException 키가 없거나, 호출이 실패했거나, 업체가 결과를 못 줬다
	 */
	String fetchNearbyStopsJson(double lat, double lng);

	/**
	 * @return TAGO {@code getSttnAcctoArvlPrearngeInfoList}(버스도착정보) 응답 원문(JSON)
	 * @throws TransitVendorException 키가 없거나, 호출이 실패했거나, 업체가 결과를 못 줬다
	 */
	String fetchArrivalsJson(String cityCode, String nodeId);

	/** 로그와 응답 진단에 남길 이름. */
	String providerName();
}
