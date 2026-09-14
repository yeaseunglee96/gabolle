package com.gabolle.backend.place.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 갈래 열람 기록이 두 갈래로 만들어진다 — S15P21E201-894.
 *
 * <p>DB 없이 도는 검사다. 전역 열람이 생기면서 {@code tripId} 가 비어 있을 수 있게 됐는데, 그
 * 완화가 <b>여행 번호를 실수로 빠뜨린 호출</b>까지 통과시키면 집계에서 둘을 가를 수 없다. 그
 * 경계를 재는 자리다.
 */
class PlaceFacetViewTest {

	private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 9, 14, 12, 0, 0, 0, ZoneOffset.UTC);

	@Test
	@DisplayName("여행 안에서 연 기록은 그 여행 번호를 가진다")
	void aTripFacetViewKeepsItsTripId() {
		UUID tripId = UUID.randomUUID();

		PlaceFacetView view = PlaceFacetView.of("NIGHT_MARKET", tripId, NOW);

		assertThat(view.facetKey()).isEqualTo("NIGHT_MARKET");
		assertThat(view.tripId()).isEqualTo(tripId);
	}

	@Test
	@DisplayName("전역 탐색에서 연 기록은 여행 번호가 비어 있다")
	void aGlobalFacetViewHasNoTripId() {
		PlaceFacetView view = PlaceFacetView.ofGlobal("SEA_BEACH", NOW);

		assertThat(view.facetKey()).isEqualTo("SEA_BEACH");
		assertThat(view.tripId()).isNull();
	}

	@Test
	@DisplayName("🔴 여행 기록 쪽으로는 빈 여행 번호가 못 지나간다 — 빠뜨린 호출과 전역 열람이 같아지면 안 된다")
	void theTripFactoryStillRefusesAMissingTripId() {
		assertThatThrownBy(() -> PlaceFacetView.of("WALK", null, NOW))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("ofGlobal");
	}

	@Test
	@DisplayName("갈래 코드와 시각은 두 갈래 모두에서 필수다")
	void facetKeyAndTimeAreRequiredOnBothPaths() {
		assertThatThrownBy(() -> PlaceFacetView.ofGlobal("  ", NOW))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PlaceFacetView.ofGlobal("WALK", null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PlaceFacetView.of(null, UUID.randomUUID(), NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
