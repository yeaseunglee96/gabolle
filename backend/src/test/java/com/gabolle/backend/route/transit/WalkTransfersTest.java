package com.gabolle.backend.route.transit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 걸어서 갈아타기를 좌표로 만든다 (S15P21E201-2067) — 전에는 비어 있어 버스↔지하철 환승이 하나도 없었다. */
class WalkTransfersTest {

	@Test
	void 삼백미터_안의_다른_정류장_역을_양방향으로_잇고_역이면_2분을_더한다() {
		TransitNetwork.Stop station = new TransitNetwork.Stop("st", "서면역", 35.1578, 129.0599, TransitNetwork.Kind.SUBWAY);
		TransitNetwork.Stop busNear = new TransitNetwork.Stop("b1", "서면교차로", 35.1590, 129.0599, TransitNetwork.Kind.BUS); // 약 133m
		TransitNetwork.Stop busFar = new TransitNetwork.Stop("b2", "먼 정류장", 35.1650, 129.0599, TransitNetwork.Kind.BUS); // 약 800m

		List<TransitNetwork.Transfer> transfers = BusanTransitNetworkPort.walkTransfers(List.of(station, busNear, busFar));

		assertThat(transfers).extracting(TransitNetwork.Transfer::fromStopId, TransitNetwork.Transfer::toStopId)
			.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("st", "b1"), org.assertj.core.groups.Tuple.tuple("b1", "st"));
		// 133m × 1.3 ÷ 66.7m/분 ≈ 2.6 → 3분, 역이라 +2 = 5분
		assertThat(transfers.get(0).walkMin()).isEqualTo(5);
	}
}
