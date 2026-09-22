package com.gabolle.backend.coursetheme;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.gabolle.backend.recommendation.config.BaselineEngineProperties.Weights;

/**
 * 코스 테마가 점수 가중치를 얼마나 밀거나 당기는가.
 *
 * 테마 하나의 {@code weight-multipliers} 를 기본 가중치에 곱해 이번 요청에 쓸 가중치를 낸다.
 * DB 도 네트워크도 안 본다.
 *
 * 설정에 배수를 적은 테마가 아직 하나도 없어서, 지금은 받은 가중치를 그대로 돌려준다. 숫자를
 * 여기서 지어내지 않는다 — 근거 없는 순위는 틀렸다는 것조차 알 수 없다.
 *
 * 모르는 테마 코드에는 죽지 않는다. 여행에 저장된 코드가 나중에 설정에서 빠질 수 있는데, 거기서
 * 예외를 던지면 그 테마로 만든 여행 전부가 추천을 못 받는다 — 기본 가중치로 되돌아가고,
 * 되돌아갔다는 사실을 {@link Resolution#themeApplied()} 로 남긴다. 반면 모르는 축 이름은
 * 생성자가 기동을 막는다 — 조용히 무시되면 값을 적어 둔 사람은 반영됐다고 믿는다.
 *
 * 이 클래스가 {@code recommendation} 이 아니라 여기 있는 것은, 생성자로
 * {@link CourseThemeProperties} 를 요구하는데 {@code recommendation} 을 스캔하는 테스트
 * 슬라이스 대부분이 {@code coursetheme} 는 안 스캔해 빈을 못 찾기 때문이다.
 */
@Component
public class ThemeWeightResolver {

	/**
	 * 배수를 걸 수 있는 축 — {@link Weights} 의 여섯과 정확히 같다. {@code Weights} 에 축이 늘면
	 * 여기도 늘려야 한다. 두 벌이 되는 것을 알면서 두는 이유는, 레코드 구성요소를 리플렉션으로
	 * 읽으면 오타 검사가 기동 시점이 아니라 첫 요청 시점으로 밀리기 때문이다.
	 */
	static final List<String> AXES = List.of("distance", "interest", "atmosphere", "cuisine", "preferenceAlignment",
			"popularity");

	private final Map<String, CourseThemeProperties.Theme> byCode;

	public ThemeWeightResolver(CourseThemeProperties properties) {
		Map<String, CourseThemeProperties.Theme> themes = new LinkedHashMap<>();
		for (CourseThemeProperties.Theme theme : properties.getThemes()) {
			validate(theme);
			themes.put(theme.getCode(), theme);
		}
		this.byCode = themes;
	}

	private static void validate(CourseThemeProperties.Theme theme) {
		Map<String, Double> multipliers = theme.getWeightMultipliers();
		if (multipliers == null) {
			return;
		}
		for (Map.Entry<String, Double> entry : multipliers.entrySet()) {
			if (!AXES.contains(entry.getKey())) {
				throw new IllegalArgumentException("gabolle.course-theme 테마 '" + theme.getCode()
						+ "' 의 weight-multipliers 에 모르는 축이 있다: '" + entry.getKey() + "'. 쓸 수 있는 축은 " + AXES);
			}
			Double value = entry.getValue();
			if (value == null || !Double.isFinite(value) || value < 0.0) {
				throw new IllegalArgumentException("gabolle.course-theme 테마 '" + theme.getCode() + "' 의 '"
						+ entry.getKey() + "' 배수는 0 이상의 유한한 수여야 한다 (지금: " + value + ")");
			}
		}
	}

	/**
	 * @param themeCode 이번 여행이 고른 테마. {@code null}·빈 문자열이면 테마를 안 골랐다는 뜻이다
	 * @param base 설정의 기본 가중치
	 */
	public Resolution resolve(String themeCode, Weights base) {
		if (themeCode == null || themeCode.isBlank()) {
			return new Resolution(base, null, false, null);
		}
		CourseThemeProperties.Theme theme = this.byCode.get(themeCode);
		if (theme == null) {
			return new Resolution(base, null, false, themeCode);
		}
		Map<String, Double> multipliers = theme.getWeightMultipliers() == null ? Map.of()
				: theme.getWeightMultipliers();
		Weights scaled = new Weights(scale(base.distance(), multipliers.get("distance")),
				scale(base.interest(), multipliers.get("interest")),
				scale(base.atmosphere(), multipliers.get("atmosphere")),
				scale(base.cuisine(), multipliers.get("cuisine")),
				scale(base.preferenceAlignment(), multipliers.get("preferenceAlignment")),
				scale(base.popularity(), multipliers.get("popularity")));
		return new Resolution(scaled, theme.getTouristPenalty(), true, themeCode);
	}

	private static double scale(double weight, Double multiplier) {
		return multiplier == null ? weight : weight * multiplier;
	}

	/**
	 * @param weights 이번 요청에 쓸 가중치. 테마가 없거나 모르는 코드면 받은 것 그대로다
	 * @param touristPenalty 이 테마의 유명 관광지 감점. 테마를 못 찾았으면 {@code null} 이고,
	 *        {@code null} 과 {@code 0.0} 은 다른 뜻이다 — 안 적었으면 "이 테마는 그 축을 안 쓴다",
	 *        0 이면 "쓰지만 감점하지 않는다" 다
	 * @param themeApplied 테마를 실제로 먹였나. {@code false} 인데 {@code themeCode} 가 있으면
	 *        설정에 없는 코드라는 뜻이다
	 * @param themeCode 요청이 말한 테마 코드. 안 골랐으면 {@code null}
	 */
	public record Resolution(Weights weights, Double touristPenalty, boolean themeApplied, String themeCode) {
	}

}
