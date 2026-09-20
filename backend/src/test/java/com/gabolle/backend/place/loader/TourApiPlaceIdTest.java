package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 두 적재기의 장소 id 가 겹치지 않는지 못 박는다. 겹치면 서로 다른 장소가 한 행이 되어 이름과
 * 좌표가 덮이고 거리 점수가 엉뚱한 자리에서 계산되는데, 아무것도 빨개지지 않고 적재는
 * "건너뜀 1" 로 성공한 것처럼 보인다. 겹치지 않는 근거는 앞머리({@code SBIZ} 대
 * {@code TOURAPI})뿐이다.
 */
class TourApiPlaceIdTest {

	@Test
	@DisplayName("같은 식별자 문자열이어도 두 적재기의 장소 id 는 다르다")
	void sameKeyStringStillGivesDifferentIds() {
		String key = "129156";

		assertThat(TourApiPlaceLoader.placeIdOf(key))
				.as("앞머리가 달라야 겹치지 않는다")
				.isNotEqualTo(SbizPlaceLoader.placeIdOf(key));
	}

	@Test
	@DisplayName("🔴 실제 값 범위에서 한 번도 겹치지 않는다 — 상가업소번호 모양과 contentid 모양을 함께 넣어 본다")
	void noCollisionAcrossRealisticKeys() {
		Set<UUID> ids = new HashSet<>();
		int count = 0;

		// contentid 는 6~7자리 숫자다.
		for (int i = 100000; i < 106000; i++) {
			assertThat(ids.add(TourApiPlaceLoader.placeIdOf(String.valueOf(i)))).isTrue();
			count++;
		}
		// 상가업소번호는 MA + 숫자·영문이 섞인 20자다.
		for (int i = 0; i < 6000; i++) {
			String storeId = "MA0101202511A00%05d".formatted(i);
			assertThat(ids.add(SbizPlaceLoader.placeIdOf(storeId)))
					.as("상가업소 id 가 관광공사 id 와 겹쳤다: %s", storeId)
					.isTrue();
			count++;
		}
		assertThat(ids).hasSize(count);
	}

	@Test
	@DisplayName("같은 식별자로 두 번 계산하면 같은 값이다 — 두 번 돌려도 행이 안 느는 근거다")
	void idIsStable() {
		assertThat(TourApiPlaceLoader.placeIdOf("1942299"))
				.isEqualTo(TourApiPlaceLoader.placeIdOf("1942299"));
	}

	@Test
	@DisplayName("피처 id 도 갈래마다 다르다 — 한 장소에 표식이 둘 이상 붙어도 서로 안 덮는다")
	void featureIdsDifferPerKey() {
		UUID interest = TourApiPlaceLoader.featureIdOf("1", "INTEREST_TAG", "NATURE_WALK");
		UUID other = TourApiPlaceLoader.featureIdOf("1", "INTEREST_TAG", "SEA_BEACH");

		assertThat(interest).isNotEqualTo(other);
	}
}
