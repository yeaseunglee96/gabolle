package com.gabolle.backend.recommendation.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.gabolle.backend.coursetheme.CourseThemeProperties;
import com.gabolle.backend.recommendation.config.BaselineEngineProperties.Weights;

/**
 * 코스 테마가 점수 가중치를 얼마나 밀거나 당기는가 — S15P21E201-106 · 452.
 *
 * <p>테마 하나의 {@code weight-multipliers} 를 기본 가중치에 곱해 <b>이번 요청에 쓸 가중치</b>
 * 를 낸다. DB 도 네트워크도 안 본다 — 설정과 인자만 보는 순수 계산이라 테스트가 컨테이너
 * 없이 돈다.
 *
 * <h2>🔴 지금은 아무것도 안 바꾼다. 그것이 맞다</h2>
 *
 * 설정에 배수를 적은 테마가 하나도 없으므로 이 클래스는 지금 <b>받은 가중치를 그대로
 * 돌려준다.</b> 계산 결과가 한 글자도 안 달라진다.
 *
 * <p>일부러 그렇게 뒀다. 106 은 <i>"가중치 배수의 실제 값은 코스 테마 설정 파일에 있다.
 * 여기서 새 숫자를 지어내지 않는다"</i> 고 못 박는데, 그 숫자가 아직 어디에도 없다. 지어내면
 * 근거 없는 순위가 되고, 근거 없는 순위는 <b>틀렸다는 것조차 알 수 없다.</b> 그래서 그릇을
 * 먼저 만들고 숫자는 비워 둔다 — 짝 비교 설문이 교환율을 내놓으면 설정에 적기만 하면 된다.
 *
 * <h2>왜 모르는 테마 코드에 죽지 않나</h2>
 *
 * 여행에 저장된 테마 코드가 나중에 설정에서 빠질 수 있다. 그때 여기서 예외를 던지면 <b>그
 * 테마로 만든 여행 전부가 추천을 못 받는다</b> — 테마는 순위를 기울이는 값이지 추천의 전제가
 * 아니다. 그래서 기본 가중치로 되돌아가고, 되돌아갔다는 사실을 {@link Resolution#themeApplied()}
 * 로 <b>남긴다.</b> 조용히 넘어가지 않는다.
 *
 * <p>대신 <b>모르는 축 이름은 기동을 막는다</b>(생성자). 오타는 사람이 고칠 수 있는 자리에서
 * 나야 하고, 조용히 무시되면 값을 적어 둔 사람은 반영됐다고 믿는다.
 */
@Component
public class ThemeWeightResolver {

	/**
	 * 배수를 걸 수 있는 축 — {@link Weights} 의 여섯과 정확히 같다.
	 *
	 * <p>🔴 {@code Weights} 에 축이 늘면 여기도 늘려야 한다. 두 벌이 되는 것을 알면서 두는
	 * 이유는, 레코드 구성요소를 이름으로 읽어 오면(리플렉션) 오타 검사가 <b>기동 시점이 아니라
	 * 첫 요청 시점</b>으로 밀리기 때문이다. 늦게 터지는 검사는 검사가 아니다.
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
	 *     — 🔴 아직 이 값을 실어 오는 길이 없다. 여행에 테마를 저장하는 자리가 없기 때문이다
	 *     (DB 칸도 없고 여행 생성 API 도 안 받는다). 그 배선이 452 의 남은 절반이다
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
	 * 푼 결과.
	 *
	 * @param weights 이번 요청에 쓸 가중치. 테마가 없거나 모르는 코드면 받은 것 그대로다
	 * @param touristPenalty 이 테마의 유명 관광지 감점. 테마를 못 찾았으면 {@code null} 이고,
	 *     {@code null} 과 {@code 0.0} 은 다른 뜻이다 — 안 적었으면 "이 테마는 그 축을 안 쓴다",
	 *     0 이면 "쓰지만 감점하지 않는다" 다 ({@code CourseThemeProperties.Theme} 참고)
	 * @param themeApplied 테마를 실제로 먹였나. {@code false} 인데 {@code themeCode} 가 있으면
	 *     <b>설정에 없는 코드</b>라는 뜻이다 — 부르는 쪽이 점수 근거에 남긴다
	 * @param themeCode 요청이 말한 테마 코드. 안 골랐으면 {@code null}
	 */
	public record Resolution(Weights weights, Double touristPenalty, boolean themeApplied, String themeCode) {
	}

}
