package com.gabolle.backend.coursetheme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 코스 테마의 설정.
 *
 * 테마는 만들면서 계속 손보는 값이라 코드가 아니라 설정에 둔다. 다만 고친 값이 바로 반영되지는
 * 않는다 — 서버를 다시 시작해야 읽는다. 재시작 없이 바뀌게 하려면 설정을 다시 읽는 장치가 따로
 * 필요하다.
 *
 * 테마 목록은 {@code GET /api/v1/course-categories} 한 곳에서만 나온다. 화면이 따로 갖고
 * 있으면 서버와 갈라진다.
 */
@Component
@ConfigurationProperties(prefix = "gabolle.course-theme")
public class CourseThemeProperties {

	/**
	 * 테마 목록. 설정 파일의 순서가 그대로 화면의 순서가 된다 — 화면이 다시 정렬하지 않아도
	 * 되도록 서버가 순서까지 정해서 준다.
	 */
	private List<Theme> themes = new ArrayList<>();

	public List<Theme> getThemes() {
		return this.themes;
	}

	public void setThemes(List<Theme> themes) {
		this.themes = themes;
	}

	/** 테마 하나. 값을 담기만 하고 점수 계산은 하지 않는다. */
	public static class Theme {

		/** 화면과 서버가 함께 쓰는 코드. 대문자와 밑줄만 쓴다. */
		private String code;

		/** 사람이 읽는 이름. 화면에 그대로 나간다. */
		private String label;

		/**
		 * 가산점을 줄 장소의 성격. 예: {@code FREE}(무료), {@code NATURE}(자연).
		 * 여러 개를 줄 수 있고, 비어 있으면 특정 성격을 밀지 않는다는 뜻이다.
		 */
		private List<String> boostedFeatures = new ArrayList<>();

		/** 점수 계산에서 무엇을 더 중시하는가. 예: {@code COST}(비용), {@code DISTANCE}(거리). */
		private String scoreEmphasis;

		/** 하루에 도는 방문지 수. 테마의 "속도" 를 이 숫자로 나타낸다. */
		private Integer placesPerDay;

		/**
		 * 유명 관광지 감점. 0 이면 감점 없음, 1 에 가까울수록 세게 깎는다.
		 * {@code Double} 인 것은 안 적은 것과 0 을 구분하기 위해서다 — 안 적었으면 "이 테마는
		 * 그 축을 안 쓴다" 이고, 0 은 "쓰지만 감점하지 않는다" 다.
		 */
		private Double touristPenalty;

		/**
		 * 점수 축마다 곱하는 배수. 키는 {@code BaselineEngineProperties.Weights} 의 축 이름 여섯
		 * 중 하나다({@code distance}·{@code interest}·{@code atmosphere}·{@code cuisine}·
		 * {@code preferenceAlignment}·{@code popularity}). 안 적은 축은 1.0 이다.
		 *
		 * 기본값을 비워 두는 것이 이 칸의 설계다 — 어느 테마가 어느 축을 몇 배로 봐야 하는지를
		 * 말해 주는 실측이 아직 없고, 값이 비면 계산은 지금까지와 한 글자도 다르지 않게 돈다.
		 *
		 * 모르는 축 이름을 적으면 {@code ThemeWeightResolver} 가 기동을 막는다.
		 */
		private Map<String, Double> weightMultipliers = new LinkedHashMap<>();

		public String getCode() {
			return this.code;
		}

		public void setCode(String code) {
			this.code = code;
		}

		public String getLabel() {
			return this.label;
		}

		public void setLabel(String label) {
			this.label = label;
		}

		public List<String> getBoostedFeatures() {
			return this.boostedFeatures;
		}

		public void setBoostedFeatures(List<String> boostedFeatures) {
			this.boostedFeatures = boostedFeatures;
		}

		public String getScoreEmphasis() {
			return this.scoreEmphasis;
		}

		public void setScoreEmphasis(String scoreEmphasis) {
			this.scoreEmphasis = scoreEmphasis;
		}

		public Integer getPlacesPerDay() {
			return this.placesPerDay;
		}

		public void setPlacesPerDay(Integer placesPerDay) {
			this.placesPerDay = placesPerDay;
		}

		public Double getTouristPenalty() {
			return this.touristPenalty;
		}

		public void setTouristPenalty(Double touristPenalty) {
			this.touristPenalty = touristPenalty;
		}

		public Map<String, Double> getWeightMultipliers() {
			return this.weightMultipliers;
		}

		public void setWeightMultipliers(Map<String, Double> weightMultipliers) {
			this.weightMultipliers = weightMultipliers;
		}
	}
}
