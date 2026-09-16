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
	 * 버스가 노선을 따라 실제로 나아가는 속도(km/h) — <b>정차 시간을 포함한 표정속도</b>다.
	 *
	 * <h2>🟢 이 값은 잰 값이다 — 이 저장소에서 드문 경우다</h2>
	 *
	 * BIMS 실시간 관측 기록(<code>bigData/data/raw/transit/bims-2026-08-26·27.ndjson</code>,
	 * 60초 간격 폴링)을 전부 파싱해서 냈다. 다시 재려면
	 * <code>node bigData/analysis/bims-travel-times.mjs</code>.
	 *
	 * <pre>
	 * 응답 5,275줄 · 차량 135대 · 운행 구간 439건 · 관측 11,897km · 821시간
	 * 표정속도 중앙값 14.4 km/h   (노선별 12.4 · 13.6 · 14.5 · 15.1 · 16.9)
	 * 정류장 한 칸 중앙값 92초    (평균 간격 368m)
	 * </pre>
	 *
	 * <h2>🔴 정차 시간을 따로 두지 않는 이유</h2>
	 *
	 * 처음에는 "차내 22km/h + 정류장당 20초" 로 나눠 두었다. <b>둘 다 내가 정한 값이었고
	 * 합치면 한 칸에 80초가 나왔다 — 실제는 92초다.</b> 관측이 재 주는 것은 "달리는 속도"가
	 * 아니라 <b>"정차까지 포함해 실제로 얼마나 나아갔나"</b> 이므로, 그 모양 그대로 하나의
	 * 값으로 둔다. 지어낸 값 둘보다 잰 값 하나가 낫다.
	 *
	 * <p>🔴 그래서 정류장 사이 거리에 <b>우회 계수를 곱하지 않는다.</b> 위 속도가 정류장
	 * 좌표를 직선으로 이어 잰 거리 기준이라, 곱하면 같은 보정을 두 번 하게 된다.
	 *
	 * <h2>한계</h2>
	 *
	 * 관측된 노선은 <b>다섯 개(15·23·96·111·126)이고 전부 일반버스</b>다. 급행·마을버스는
	 * 이 값을 그대로 쓴다 — 재지 않았다는 뜻이고, 폴링 대상이 늘면 종류별로 갈라야 한다.
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
