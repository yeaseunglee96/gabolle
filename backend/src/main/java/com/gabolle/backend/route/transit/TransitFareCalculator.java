package com.gabolle.backend.route.transit;

import org.springframework.stereotype.Component;

import com.gabolle.backend.place.service.GeoDistance;

/**
 * 대중교통 여정 하나의 요금(원).
 *
 * 한 환승 묶음의 총운임은 그 안에서 탄 수단들의 기본요금 중 최댓값이다 — 부산시 안내의 「처음
 * 탄 버스의 요금보다 갈아탄 버스의 요금이 비쌀 경우 그 차액만큼 추가요금」이 이 한 줄과 같다.
 * 그래서 요금표에 없는 조합(버스↔버스)도 같은 규칙으로 답이 나온다.
 *
 * 환승을 끊는 것은 도보가 아니라 시간과 횟수다 — 내린 시각부터 다음에 탄 시각까지 30분 이내,
 * 갈아탐 2회까지(BIMS 일반 환승안내). 걷는 시간이 그 30분 안에 들어 있어서, 먼 정류장으로
 * 걸어가면 환승이 끊긴다. 끊기면 다음 탑승부터 새 묶음이고 요금을 처음부터 낸다.
 *
 * 요금표에 값이 없는 종류가 하나라도 섞이면 합계를 내지 않는다({@code null}). 아는 것만
 * 더하면 실제보다 싼 값이 나온다.
 *
 * TODO 탑승 3회(갈아탐 2회)에서 「직전 요금과 비교」인지 「지금까지 낸 최고와 비교」인지를
 * 공식 문구로 확정하지 못했다. 지금은 최고값 기준으로 구현했고, 공식 표에서 세 번 타는 사례를
 * 찾으면 다시 봐야 한다.
 */
@Component
public class TransitFareCalculator {

	/** 내린 뒤 이 시간 안에 다시 타야 환승이다. BIMS 일반 환승안내 — 「30분이내 탑승」. */
	static final int TRANSFER_WINDOW_MIN = 30;

	/** 한 묶음에서 허용되는 갈아탐 횟수. 「3번째로 갈아탄 교통수단의 요금할인 혜택은 없습니다」. */
	static final int MAX_TRANSFERS_PER_CHAIN = 2;

	private final TransitFareTable fareTable;

	public TransitFareCalculator(TransitFareTable fareTable) {
		this.fareTable = fareTable;
	}

	/**
	 * 이 여정의 요금(원).
	 *
	 * @param journey 탑승과 걷기가 섞인 여정
	 * @param network 노선의 종류를 찾는 데 쓴다
	 * @return 요금(원). 탑승이 하나도 없으면 0(걷기만 한 여정). 요금을 모르는 노선이 하나라도
	 *         끼면 {@code null}
	 */
	public Integer fareKrw(RaptorPlanner.Journey journey, TransitNetwork network) {
		int total = 0;
		int chainMax = 0;
		int transfersInChain = 0;
		Integer previousArrive = null;

		for (RaptorPlanner.Ride ride : journey.rides()) {
			if (ride.isWalk()) {
				// 걷기는 환승을 안 끊는다. 다만 내린 시각을 갱신하지 않는다 — 30분은 「내린 시각 →
				// 다음에 탄 시각」이고 걷는 시간이 그 사이에 들어 있다.
				continue;
			}
			Integer fare = fareOf(ride, network);
			if (fare == null) {
				// 모르는 요금이 하나라도 끼면 합계를 안 낸다. 아는 것만 더하면 실제보다 싸다.
				return null;
			}
			boolean startsNewChain = previousArrive == null
					|| ride.departMinOfDay() - previousArrive > TRANSFER_WINDOW_MIN
					|| transfersInChain >= MAX_TRANSFERS_PER_CHAIN;
			if (startsNewChain) {
				total += chainMax;
				chainMax = fare;
				transfersInChain = 0;
			}
			else {
				// 비싼 쪽으로 갈아타면 차액만, 싼 쪽이면 0 — 둘 다 「최댓값」 한 줄로 끝난다.
				chainMax = Math.max(chainMax, fare);
				transfersInChain++;
			}
			previousArrive = ride.arriveMinOfDay();
		}
		return total + chainMax;
	}

	/**
	 * 이 탑승 하나의 기본요금(원). 지하철은 종류가 하나라 거리로 1·2구간을 가르고, 버스는
	 * 종류로 가른다.
	 *
	 * @return 모르면 {@code null}
	 */
	private Integer fareOf(RaptorPlanner.Ride ride, TransitNetwork network) {
		TransitNetwork.Route route = network.route(ride.routeId());
		if (route == null) {
			return null;
		}
		if (route.kind() == TransitNetwork.Kind.SUBWAY) {
			TransitNetwork.Stop from = network.stop(ride.fromStopId());
			TransitNetwork.Stop to = network.stop(ride.toStopId());
			// 좌표를 모르면 구간을 못 가른다. 1구간으로 찍으면 2구간 여정이 200원 싸게 나온다.
			if (from == null || to == null) {
				return null;
			}
			// 직선거리다. 실제 선로는 더 길어서 10km 경계 근처에서는 1구간으로 기울 수 있다 —
			// 역간 거리를 갖게 되면 그것으로 바꾼다.
			int meters = (int) Math.round(
					GeoDistance.meters(from.lat(), from.lng(), to.lat(), to.lng()));
			return this.fareTable.subwayFareKrw(meters);
		}
		return this.fareTable.busFareKrw(route.fareType());
	}
}
