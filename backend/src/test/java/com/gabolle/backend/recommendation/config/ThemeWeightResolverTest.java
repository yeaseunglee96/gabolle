package com.gabolle.backend.recommendation.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.coursetheme.CourseThemeProperties;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties.Weights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 테마 배수가 가중치를 어떻게 바꾸는가 — S15P21E201-106 · 452.
 *
 * <p>컨테이너 없이 돈다. 이 계산이 설정과 인자만 보기 때문이다.
 */
class ThemeWeightResolverTest {

	private static final Weights BASE = new Weights(0.30, 0.20, 0.15, 0.15, 0.10, 0.10);

	private static CourseThemeProperties propertiesOf(CourseThemeProperties.Theme... themes) {
		CourseThemeProperties properties = new CourseThemeProperties();
		properties.setThemes(List.of(themes));
		return properties;
	}

	private static CourseThemeProperties.Theme theme(String code, Map<String, Double> multipliers) {
		CourseThemeProperties.Theme theme = new CourseThemeProperties.Theme();
		theme.setCode(code);
		theme.setLabel(code);
		theme.setWeightMultipliers(multipliers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(multipliers));
		return theme;
	}

	@Test
	@DisplayName("테마를 안 골랐으면 가중치가 그대로다")
	void withoutAThemeTheWeightsAreUntouched() {
		ThemeWeightResolver resolver = new ThemeWeightResolver(propertiesOf(theme("ZERO_WON", Map.of())));

		ThemeWeightResolver.Resolution resolution = resolver.resolve(null, BASE);

		assertThat(resolution.weights()).isEqualTo(BASE);
		assertThat(resolution.themeApplied()).isFalse();
		assertThat(resolution.themeCode()).isNull();
	}

	@Test
	@DisplayName("🔴 배수를 안 적은 테마는 계산을 한 글자도 안 바꾼다 — 지금 설정이 전부 이 상태다")
	void aThemeWithoutMultipliersChangesNothing() {
		ThemeWeightResolver resolver = new ThemeWeightResolver(propertiesOf(theme("ZERO_WON", Map.of())));

		ThemeWeightResolver.Resolution resolution = resolver.resolve("ZERO_WON", BASE);

		assertThat(resolution.weights()).isEqualTo(BASE);
		assertThat(resolution.themeApplied()).isTrue();
	}

	@Test
	@DisplayName("적은 축만 곱해지고 나머지는 그대로다")
	void onlyTheListedAxesAreScaled() {
		ThemeWeightResolver resolver = new ThemeWeightResolver(
				propertiesOf(theme("ZERO_WON", Map.of("distance", 2.0, "popularity", 0.0))));

		Weights weights = resolver.resolve("ZERO_WON", BASE).weights();

		assertThat(weights.distance()).isEqualTo(0.60);
		assertThat(weights.popularity()).isEqualTo(0.0);
		assertThat(weights.interest()).isEqualTo(BASE.interest());
		assertThat(weights.atmosphere()).isEqualTo(BASE.atmosphere());
		assertThat(weights.cuisine()).isEqualTo(BASE.cuisine());
		assertThat(weights.preferenceAlignment()).isEqualTo(BASE.preferenceAlignment());
	}

	@Test
	@DisplayName("테마가 다르면 같은 장소의 가중치가 달라진다 — 106 완료 기준")
	void differentThemesGiveDifferentWeights() {
		ThemeWeightResolver resolver = new ThemeWeightResolver(
				propertiesOf(theme("ZERO_WON", Map.of("distance", 2.0)), theme("SPLURGE", Map.of("distance", 0.5))));

		double cheap = resolver.resolve("ZERO_WON", BASE).weights().distance();
		double splurge = resolver.resolve("SPLURGE", BASE).weights().distance();

		assertThat(cheap).isNotEqualTo(splurge);
	}

	@Test
	@DisplayName("설정에 없는 테마 코드면 기본 가중치로 돌아가고, 돌아갔다는 것을 남긴다")
	void anUnknownThemeFallsBackAndSaysSo() {
		ThemeWeightResolver resolver = new ThemeWeightResolver(propertiesOf(theme("ZERO_WON", Map.of())));

		ThemeWeightResolver.Resolution resolution = resolver.resolve("SINCE_DELETED", BASE);

		assertThat(resolution.weights()).isEqualTo(BASE);
		assertThat(resolution.themeApplied()).isFalse();
		assertThat(resolution.themeCode()).isEqualTo("SINCE_DELETED");
	}

	@Test
	@DisplayName("유명 관광지 감점은 안 적은 것과 0 을 구분해서 넘긴다")
	void touristPenaltyKeepsTheDifferenceBetweenUnsetAndZero() {
		CourseThemeProperties.Theme unset = theme("UNSET", Map.of());
		CourseThemeProperties.Theme zero = theme("ZERO", Map.of());
		zero.setTouristPenalty(0.0);
		ThemeWeightResolver resolver = new ThemeWeightResolver(propertiesOf(unset, zero));

		assertThat(resolver.resolve("UNSET", BASE).touristPenalty()).isNull();
		assertThat(resolver.resolve("ZERO", BASE).touristPenalty()).isEqualTo(0.0);
	}

	@Test
	@DisplayName("🔴 모르는 축 이름은 기동을 막는다 — 오타가 조용히 무시되면 안 된다")
	void anUnknownAxisNameFailsAtStartup() {
		CourseThemeProperties properties = propertiesOf(theme("ZERO_WON", Map.of("distnace", 2.0)));

		assertThatThrownBy(() -> new ThemeWeightResolver(properties))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("distnace")
				.hasMessageContaining("ZERO_WON");
	}

	@Test
	@DisplayName("음수 배수는 기동을 막는다 — 축의 뜻이 뒤집힌다")
	void aNegativeMultiplierFailsAtStartup() {
		CourseThemeProperties properties = propertiesOf(theme("ZERO_WON", Map.of("distance", -1.0)));

		assertThatThrownBy(() -> new ThemeWeightResolver(properties))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("distance");
	}

	@Test
	@DisplayName("같은 입력이면 같은 결과다")
	void theSameInputGivesTheSameResult() {
		ThemeWeightResolver resolver = new ThemeWeightResolver(
				propertiesOf(theme("ZERO_WON", Map.of("distance", 1.5))));

		assertThat(resolver.resolve("ZERO_WON", BASE).weights())
				.isEqualTo(resolver.resolve("ZERO_WON", BASE).weights());
	}

}
