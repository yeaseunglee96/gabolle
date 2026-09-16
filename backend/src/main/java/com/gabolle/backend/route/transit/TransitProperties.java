package com.gabolle.backend.route.transit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대중교통 탐색 설정 — S15P21E201-1104.
 *
 * <p>🔴 <b>필드 기본값만으로 돌아야 한다.</b> 노선망이 아직 없어도 서버는 떠야 하고,
 * 그때는 탐색기가 빈 답을 내고 호출자가 직선거리 어림값으로 간다 —
 * {@code WeatherProperties}·{@code RouteProperties} 가 같은 이유로 같은 규칙을 지킨다.
 */
@ConfigurationProperties(prefix = "gabolle.route.transit")
public class TransitProperties {

	/**
	 * 출발·도착 지점에서 정류장까지 걸어갈 최대 거리(m).
	 *
	 * <p>🔴 이 값이 크면 <b>느려지는 게 아니라 답이 나빠진다.</b> 1km 떨어진 정류장까지
	 * 걸어가는 경로를 후보에 넣으면, 걸어서 15분이 걸리는 길이 "대중교통 경로" 로 나온다.
	 * 사람은 그 길을 안 쓴다. 800m 는 대략 10분 거리다.
	 */
	private int accessRadiusM = 800;

	/**
	 * 후보로 볼 정류장 수의 상한 (출발·도착 각각).
	 *
	 * <p>반경 안에 정류장이 스무 곳이면 RAPTOR 가 스무 곳에서 동시에 출발한다. 가까운 것부터
	 * 몇 곳만 봐도 답은 거의 같고, 그 뒤는 계산만 늘어난다.
	 */
	private int maxAccessStops = 6;

	/**
	 * 최대 몇 번까지 타 볼 것인가. 환승 횟수는 이보다 하나 적다.
	 *
	 * <p>🔴 무한이 아닌 이유는 성능이 아니라 <b>사람</b>이다 — 세 번 갈아타는 길은 시간이
	 * 짧아도 아무도 안 쓴다. 답을 못 주는 것보다 나쁘지 않다.
	 */
	private int maxRides = 3;

	/**
	 * 정류장까지 걷는 속도(km/h).
	 *
	 * <p>🔴 {@code RouteProperties.walkSpeedKmh} 와 같은 값이어야 하지만 일부러 따로 둔다 —
	 * 저쪽은 "직선거리로 어림잡을 때" 의 속도이고 이쪽은 "정류장까지 실제로 걷는" 속도다.
	 * 나중에 한쪽만 실측으로 맞출 수 있어야 한다. 🔴 <b>둘 다 아직 아무도 안 쟀다.</b>
	 */
	private double accessWalkSpeedKmh = 4.0;

	/**
	 * 정류장 사이를 <b>달리는</b> 속도(km/h) — S15P21E201-1123.
	 *
	 * <p>🔴 {@code RouteProperties.transitSpeedKmh}(18)와 다른 값이다. 저쪽은 기다리는
	 * 시간과 환승까지 <b>뭉뚱그린</b> 값이라 그만큼 낮고, 이쪽은 차가 실제로 달리는 동안의
	 * 속도다 — 기다리는 시간은 배차간격에서, 서는 시간은 아래 값에서 따로 온다.
	 *
	 * <p>🔴 <b>잰 값이 아니다.</b> 이 저장소의 다른 속도들과 같다.
	 */
	private double rideSpeedKmh = 22.0;

	/**
	 * 정류장 한 곳에 서느라 드는 초. 지나는 중간 정류장 수만큼 붙는다.
	 * 🔴 잰 값이 아니다.
	 */
	private int dwellSecondsPerStop = 20;

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

	public int getDwellSecondsPerStop() {
		return this.dwellSecondsPerStop;
	}

	public void setDwellSecondsPerStop(int dwellSecondsPerStop) {
		this.dwellSecondsPerStop = dwellSecondsPerStop;
	}
}
