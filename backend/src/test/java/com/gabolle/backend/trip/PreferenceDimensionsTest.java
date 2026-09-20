package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.domain.PreferenceDimensions;

/**
 * 앱 이름 → DB 어휘. 왼쪽 아홉 개는 {@code frontend/src/api/tripApi.ts} 의
 * {@code preference('…')} 호출과 글자 그대로 같아야 한다.
 */
class PreferenceDimensionsTest {

	private static final Map<String, String> APP_TO_DB = Map.of(
			"category", "CATEGORY",
			"atmosphere", "ATMOSPHERE",
			"locality", "LOCALITY",
			"quietness", "QUIETNESS",
			"touristPreference", "TOURIST_PREFERENCE",
			"foodPreference", "FOOD_PREFERENCE",
			"transport", "TRANSPORT",
			"slopePreference", "SLOPE_PREFERENCE",
			"shadePreference", "SHADE_PREFERENCE");

	@Test
	@DisplayName("앱이 보내는 아홉 이름이 전부 DB 어휘로 바뀐다")
	void appNamesMapToVocabulary() {
		APP_TO_DB.forEach((app, db) -> assertThat(PreferenceDimensions.normalize(app))
				.as(app).isEqualTo(db));
	}

	@Test
	@DisplayName("이미 어휘로 온 값은 그대로다 — 두 번 통과시켜도 같다(멱등)")
	void vocabularyPassesThroughUnchanged() {
		APP_TO_DB.values().forEach((db) -> {
			assertThat(PreferenceDimensions.normalize(db)).isEqualTo(db);
			assertThat(PreferenceDimensions.normalize(PreferenceDimensions.normalize(db))).isEqualTo(db);
		});
	}

	@Test
	@DisplayName("앞뒤 공백은 벗기고, 어휘는 대소문자를 가리지 않는다")
	void trimsAndUppercasesVocabulary() {
		assertThat(PreferenceDimensions.normalize(" category ")).isEqualTo("CATEGORY");
		assertThat(PreferenceDimensions.normalize("tourist_preference")).isEqualTo("TOURIST_PREFERENCE");
	}

	@Test
	@DisplayName("🔴 모르는 이름은 조용히 버리지 않고 거절한다 — 원문을 담는다")
	void unknownNameIsRejectedLoudly() {
		assertThatThrownBy(() -> PreferenceDimensions.normalize("vibe"))
				.isInstanceOf(PreferenceDimensions.UnknownPreferenceDimensionException.class)
				.satisfies((e) -> assertThat(((PreferenceDimensions.UnknownPreferenceDimensionException) e).raw())
						.isEqualTo("vibe"))
				.hasMessageContaining("vibe");
		assertThatThrownBy(() -> PreferenceDimensions.normalize(null))
				.isInstanceOf(PreferenceDimensions.UnknownPreferenceDimensionException.class);
		assertThatThrownBy(() -> PreferenceDimensions.normalize("   "))
				.isInstanceOf(PreferenceDimensions.UnknownPreferenceDimensionException.class);
	}

	@Test
	@DisplayName("transport 는 어휘로는 바뀌지만 스냅샷에 저장하지 않는 차원으로 표시된다")
	void transportIsFlagged() {
		assertThat(PreferenceDimensions.isTransport(PreferenceDimensions.normalize("transport"))).isTrue();
		assertThat(PreferenceDimensions.isTransport(PreferenceDimensions.normalize("category"))).isFalse();
	}
}
