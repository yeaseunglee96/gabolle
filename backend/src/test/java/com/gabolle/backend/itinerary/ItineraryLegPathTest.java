package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;

/**
 * 구간이 「어느 길로 가는지」를 어떻게 들고 있는가 — S15P21E201-1251.
 *
 * <p>요점 하나다. <b>「모른다」와 「재 봤더니 비었다」는 다른 사실이고, 한 칸에 섞이면
 * 안 된다.</b> 빈 목록을 그대로 저장해 두면 나중에 읽는 쪽이 뒤엣것으로 읽는다.
 */
class ItineraryLegPathTest {

	private static ItineraryLeg legWith(List<double[]> path) {
		return new ItineraryLeg(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 0, 1,
				null, UUID.randomUUID().toString(), "WALK", 8_400, 21, 8_400, null, null,
				ItineraryItem.DataStatus.VERIFIED, null, path, Instant.now());
	}

	@Test
	@DisplayName("점이 둘 이상이면 선형으로 들고 있는다")
	void keepsARealLine() {
		ItineraryLeg leg = legWith(List.of(
				new double[] { 129.1604, 35.1587 },
				new double[] { 129.0756, 35.1796 }));

		assertThat(leg.hasPath()).isTrue();
		assertThat(leg.path()).hasSize(2);
	}

	@Test
	@DisplayName("🔴 빈 목록과 점 하나는 「없다」로 눕힌다 — 「재 봤더니 비었다」로 읽히면 안 된다")
	void emptyOrSinglePointBecomesNull() {
		assertThat(legWith(List.of()).path()).isNull();
		assertThat(legWith(List.of(new double[] { 129.1604, 35.1587 })).path()).isNull();
		assertThat(legWith(null).path()).isNull();
	}

	private static ItineraryLeg legWith(List<double[]> path, List<ItineraryLeg.Piece> pieces) {
		return new ItineraryLeg(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 0, 1,
				null, UUID.randomUUID().toString(), "WALK", 8_400, 21, 8_400, null, null,
				ItineraryItem.DataStatus.VERIFIED, null, path, pieces, null, Instant.now());
	}

	@Test
	@DisplayName("🔴 경사·계단 조각은 선형이 있을 때만 든다 — 선형이 없으면 가리킬 자리가 없다. 빈 목록도 「없다」다")
	void piecesNeedAPath() {
		List<double[]> road = List.of(new double[] { 129.1604, 35.1587 }, new double[] { 129.0756, 35.1796 });
		List<ItineraryLeg.Piece> pieces = List.of(new ItineraryLeg.Piece(0, 1, 4.2, false));

		assertThat(legWith(road, pieces).pieces()).isEqualTo(pieces);
		assertThat(legWith(null, pieces).pieces()).isNull();
		assertThat(legWith(road, List.of()).pieces()).isNull();
		assertThat(legWith(road).pieces()).as("조각 칸 이전의 생성자").isNull();
	}

	@Test
	@DisplayName("선형 칸 이전의 생성자는 그대로 돈다 — 그때 만들어진 구간은 선형이 없다")
	void olderConstructorStillWorks() {
		ItineraryLeg leg = new ItineraryLeg(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
				0, 1, null, UUID.randomUUID().toString(), "WALK", 8_400, 21, 8_400, null, null,
				ItineraryItem.DataStatus.VERIFIED, 1_500, Instant.now());

		assertThat(leg.path()).isNull();
		assertThat(leg.fareKrw()).as("요금은 그대로 실린다").isEqualTo(1_500);
	}

	@Test
	@DisplayName("불러 준 목록을 나중에 고쳐도 구간 안의 선형은 안 바뀐다")
	void pathIsNotAliasedToTheCallersList() {
		List<double[]> mutable = new ArrayList<>(List.of(
				new double[] { 129.1604, 35.1587 },
				new double[] { 129.0756, 35.1796 }));

		ItineraryLeg leg = legWith(mutable);
		mutable.clear();

		assertThat(leg.path()).hasSize(2);
	}
}
