package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PlaceFeatureLoader#placeIdOf}·{@link PlaceFeatureLoader#featureIdOf} 가 두 적재기의
 * 공식과 글자 하나까지 같은 값을 내는지 잰다. 한 글자라도 어긋나면 이미 적재된 장소를 "없는
 * 장소" 로 오판해, 예외 없이 조용히 {@code missingPlace} 로 세어진다.
 */
class PlaceFeatureLoaderIdTest {

	@Test
	@DisplayName("namespace=SBIZ 면 SbizPlaceLoader.placeIdOf 와 정확히 같다")
	void sbizNamespaceMatchesSbizLoader() {
		String storeId = "MA0101202511A0024557";

		assertThat(PlaceFeatureLoader.placeIdOf("SBIZ", storeId)).isEqualTo(SbizPlaceLoader.placeIdOf(storeId));
	}

	@Test
	@DisplayName("namespace=TOURAPI 면 TourApiPlaceLoader.placeIdOf 와 정확히 같다")
	void tourApiNamespaceMatchesTourApiLoader() {
		String contentId = "129156";

		assertThat(PlaceFeatureLoader.placeIdOf("TOURAPI", contentId))
				.isEqualTo(TourApiPlaceLoader.placeIdOf(contentId));
	}

	@Test
	@DisplayName("🔴 featureKey 가 null 이면 SbizPlaceLoader.featureIdOf 와 같다 — \"null\" 문자열이 그대로 이어붙는 것까지 같다")
	void featureIdMatchesSbizLoaderWithNullKey() {
		String storeId = "MA0101202511A0024557";

		assertThat(PlaceFeatureLoader.featureIdOf("SBIZ", storeId, "PRICE_LEVEL", null))
				.isEqualTo(SbizPlaceLoader.featureIdOf(storeId, "PRICE_LEVEL", null));
	}

	@Test
	@DisplayName("두 namespace 는 같은 storeId 문자열이어도 다른 placeId 를 낸다")
	void namespacesDoNotCollide() {
		String key = "129156";

		assertThat(PlaceFeatureLoader.placeIdOf("SBIZ", key)).isNotEqualTo(PlaceFeatureLoader.placeIdOf("TOURAPI", key));
	}
}
