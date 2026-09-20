package com.gabolle.backend.route.transit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대중교통 탐색 설정. 설정 파일이 없어도 필드 기본값만으로 서버가 떠야 한다 —
 * 노선망이 없으면 탐색기가 빈 답을 내고 호출자가 직선거리 어림값으로 간다.
 */
@ConfigurationProperties(prefix = "gabolle.route.transit")
public class TransitProperties {

	/**
	 * 출발·도착 지점에서 정류장까지 걸어갈 최대 거리(m). 800m 는 대략 도보 10분이다.
	 * 키우면 느려지는 게 아니라 답이 나빠진다 — 걸어서 15분인 길이 대중교통 경로로 나온다.
	 */
	private int accessRadiusM = 800;

	/** 후보로 볼 정류장 수의 상한 (출발·도착 각각). 가까운 것 몇 곳이면 답이 거의 같다. */
	private int maxAccessStops = 6;

	/**
	 * 최대 몇 번까지 타 볼 것인가. 환승 횟수는 이보다 하나 적다.
	 * 성능이 아니라 사람 때문에 제한한다 — 세 번 갈아타는 길은 짧아도 아무도 안 쓴다.
	 */
	private int maxRides = 3;

	/**
	 * 정류장까지 걷는 속도(km/h). RouteProperties.walkSpeedKmh 와 값이 같지만 한쪽만 실측으로
	 * 맞출 수 있도록 일부러 따로 둔다. 둘 다 아직 실측이 아니다.
	 */
	private double accessWalkSpeedKmh = 4.0;

	/**
	 * 버스가 노선을 따라 나아가는 속도(km/h) — 정차 시간을 포함한 표정속도다. 차내 속도와
	 * 정차 시간을 나누지 않고 하나로 둔다.
	 *
	 * BIMS 실시간 관측(60초 폴링, 노선 5개 · 운행 439건 · 11,897km)의 중앙값이다.
	 * 다시 재려면 node bigData/analysis/bims-travel-times.mjs.
	 *
	 * 이 속도는 정류장 좌표를 직선으로 이어 잰 거리 기준이라, 정류장 사이 거리에 우회 계수를
	 * 곱하면 같은 보정을 두 번 하게 된다.
	 * 관측된 노선은 전부 일반버스다 — 급행·마을버스는 재지 않고 이 값을 그대로 쓴다.
	 */
	private double rideSpeedKmh = 14.4;

	public int getAccessRadiusM() {
		return this.accessRadiusM;
	}

	public void setAccessRadiusM(int accessRadiusM) {
		this.accessRadiusM = accessRadiusM;
	}

	public int getMaxAccessStops() {
		return this.maxAccessStops;
	}

	public void setMaxAccessStops(int maxAccessStops) {
		this.maxAccessStops = maxAccessStops;
	}

	public int getMaxRides() {
		return this.maxRides;
	}

	public void setMaxRides(int maxRides) {
		this.maxRides = maxRides;
	}

	public double getAccessWalkSpeedKmh() {
		return this.accessWalkSpeedKmh;
	}

	public void setAccessWalkSpeedKmh(double accessWalkSpeedKmh) {
		this.accessWalkSpeedKmh = accessWalkSpeedKmh;
	}

	public double getRideSpeedKmh() {
		return this.rideSpeedKmh;
	}

	public void setRideSpeedKmh(double rideSpeedKmh) {
		this.rideSpeedKmh = rideSpeedKmh;
	}
}
