package com.gabolle.backend.place;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.domain.Place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link Place#assignSubwayExit} 검증. */
class PlaceTest {

	private Place newPlace() {
		return Place.imported(UUID.randomUUID(), "어느 집", "FOOD", "부산광역시 중구 광복로 1", 35.10, 129.03,
				"TOURAPI", "129156", OffsetDateTime.now(), null, "test-202609");
	}

	@Test
	@DisplayName("지하철 출구를 붙이면 조회된다")
	void 지하철_출구를_붙인다() {
		Place place = this.newPlace();

		place.assignSubwayExit("2호선 강남역 3번 출구");

		assertThat(place.getSubwayExit()).isEqualTo("2호선 강남역 3번 출구");
	}

	@Test
	@DisplayName("🔴 빈 값으로는 붙일 수 없다 — 모르면 이 메서드를 안 부르는 것이 계약이다")
	void 빈_값은_거부한다() {
		Place place = this.newPlace();

		assertThatThrownBy(() -> place.assignSubwayExit("")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> place.assignSubwayExit("   ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> place.assignSubwayExit(null)).isInstanceOf(IllegalArgumentException.class);
	}
}
