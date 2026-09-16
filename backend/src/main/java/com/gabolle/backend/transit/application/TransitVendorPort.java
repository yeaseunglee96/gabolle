package com.gabolle.backend.transit.application;

/**
 * TAGO(국토교통부 버스 정류소·도착정보) 호출 자리 — S15P21E201-988.
 *
 * <p>🔴 {@code WeatherVendorPort}와 같은 이유로 <b>실패를 빈 값이 아니라 예외로 던진다.</b>
 * 벤더가 없는데 도착 시간을 지어내면 그건 실시간 정보가 아니라 창작이다.
 *
 * <p>원문 JSON을 그대로 돌려주고 파싱은 부르는 쪽({@code TransitService})이 한다 — 캐시가
 * 없는 실시간 데이터라 {@code WeatherVendorPort}처럼 캐시 재사용을 위한 것은 아니고, 벤더
 * 응답 모양과 도메인 파싱 책임을 분리해 두기 위해서다.
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
