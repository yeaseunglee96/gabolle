package com.gabolle.backend.route.transit;

import org.springframework.stereotype.Component;

import com.gabolle.backend.place.service.GeoDistance;

/**
 * 대중교통 여정 하나의 요금 — S15P21E201-1291.
 *
 * <h2>🔴 규칙은 하나다 — 「지금까지 낸 것보다 비싸면 그 차액만」</h2>
 *
 * 부산시 안내가 그렇게 적었다 — <i>「처음 탄 버스의 요금보다 갈아탄 버스의 요금이 비쌀 경우
 * 그 차액만큼 추가요금을 내야 한다」</i>, <i>「좌석버스에서 일반버스로 환승할 때는 추가 요금을
 * 내지 않아도 된다」</i>.
 *
 * <p>즉 <b>한 환승 묶음의 총운임 = 그 안에서 탄 수단들의 기본요금 중 최댓값</b>이다.
 * 요금표의 환승 절 네 줄이 <b>전부 이 한 줄의 사례</b>다 — 그래서 표에 없는 조합
 * (버스↔버스)도 같은 규칙으로 답이 나온다.
 *
 * <pre>
 *   일반버스 1,550 → 도시철도 1,600   추가  50 = 1600-1550   총 1,600
 *   도시철도 1,600 → 좌석버스 2,100   추가 500 = 2100-1600   총 2,100
 *   도시철도 1,600 → 일반버스 1,550   추가   0 (싸다)        총 1,600
 *   좌석버스 2,100 → 도시철도 1,600   추가   0 (싸다)        총 2,100
 * </pre>
 *
 * <h2>🔴 환승을 끊는 것은 도보가 아니라 시간과 횟수다</h2>
 *
 * 정류장까지 걷는 것은 <b>원래 환승의 일부</b>다. 끊는 것은 둘이다(BIMS 일반 환승안내) —
 * <i>「30분 이내 2번까지 갈아타는 것」</i>, <i>「3번째로 갈아탄 교통수단의 요금할인 혜택은
 * 없습니다」</i>. 끊기면 그 다음 탑승부터 <b>새 묶음</b>이 시작되고 요금을 처음부터 낸다.
 *
 * <p>30분은 <b>내린 시각부터 다음에 탄 시각까지</b>로 잰다. 걷는 시간이 그 사이에 들어 있다 —
 * 그래서 아주 먼 정류장으로 걸어가면 환승이 끊기고, 그것이 실제 동작과 같다.
 *
 * <h2>🔴 모르는 요금이 하나라도 끼면 전체가 「모른다」다</h2>
 *
 * 급행버스처럼 요금표에 값이 없는 종류가 섞이면 <b>합계를 내지 않는다</b>({@code null}).
 * 아는 것만 더하면 <b>실제보다 싼 값</b>이 나오고, 그것은 틀린 값을 자신 있게 보여주는 것이다.
 *
 * <h2>⚠️ 세 번 타는 경우는 공식 문구로 확정하지 못했다</h2>
 *
 * 갈아탐 2회(탑승 3회)일 때 「직전 요금과 비교」인지 「지금까지 낸 최고와 비교」인지가 갈린다
 * (좌석→일반→좌석 이 그 둘을 가른다). 요금표의 네 줄과 <i>「처음 탄 버스의 요금보다」</i> 라는
 * 표현이 <b>최고값 기준</b>을 가리켜 그쪽으로 구현했다. <b>공식 표에서 세 번 타는 사례를 찾으면
 * 다시 봐야 한다.</b>
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
	 * @return 요금. 🔴 <b>탑승이 하나도 없으면 0</b>(걷기만 한 여정 — 「공짜」가 맞다).
	 *         🔴 <b>요금을 모르는 노선이 하나라도 끼면 {@code null}</b>(「모른다」)
	 */
	public Integer fareKrw(RaptorPlanner.Journey journey, TransitNetwork network) {
		int total = 0;
		int chainMax = 0;
		int transfersInChain = 0;
		Integer previousArrive = null;

		for (RaptorPlanner.Ride ride : journey.rides()) {
			if (ride.isWalk()) {
				// 🔴 걷기는 환승을 안 끊는다. 다만 시간은 흐르므로 내린 시각을 갱신하지 않는다 —
				//    30분은 「내린 시각 → 다음에 탄 시각」이고 걷는 시간이 그 사이에 들어 있다.
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
				// 🔴 비싼 쪽으로 갈아타면 차액만, 싼 쪽이면 0 — 둘 다 「최댓값」 한 줄로 끝난다.
				chainMax = Math.max(chainMax, fare);
				transfersInChain++;
			}
			previousArrive = ride.arriveMinOfDay();
		}
		return total + chainMax;
	}

	/**
	 * 이 탑승 하나의 기본요금.
	 *
	 * <p>지하철은 종류가 하나라 <b>거리</b>로 1·2구간을 가르고, 버스는 <b>종류</b>로 가른다.
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
			// 🔴 좌표를 모르면 구간을 못 가른다. 1구간으로 찍으면 2구간 여정이 200원 싸게 나온다.
			if (from == null || to == null) {
				return null;
			}
			// 🔴 직선거리다. 실제 선로는 더 길어서 10km 경계 근처에서는 1구간으로 기울 수 있다 —
			//    역간 거리를 갖게 되면 그것으로 바꾼다. 지어내는 것이 아니라 가진 것으로 재는 것이다.
			int meters = (int) Math.round(
					GeoDistance.meters(from.lat(), from.lng(), to.lat(), to.lng()));
			return this.fareTable.subwayFareKrw(meters);
		}
		return this.fareTable.busFareKrw(route.fareType());
	}
}
