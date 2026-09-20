package com.gabolle.backend.recommendation.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 점수형 취향 다섯 차원이 {@code weights.preference-alignment} 를 나누는 비율.
 * {@link #weightedAverage(Map)} 가 그 계산이다.
 *
 * 여기 값은 총점 가중치가 아니라 비율이라 전부 2배로 해도 결과가 같다.
 * {@link BaselineEngineProperties.Weights} 의 여섯은 반대로 총점에서 차지하는 절대 몫이라,
 * 설정 키도 {@code …baseline.weights.*} 가 아니라 {@code …baseline.alignment.*} 다. 비율로
 * 두면 차원이 늘거나 줄어도 취향 그룹이 총점에서 차지하는 몫이 변하지 않고, 그룹 전체를
 * 키우려면 {@code weights.preference-alignment} 한 줄만 만진다. 기본값은 전부 1.0 이라
 * 가중평균이 단순평균과 같은 값이 되어 순위가 바뀌지 않는다.
 *
 * @param locality 골목·현지 성향 (LOCALITY)
 * @param quietness 조용함 (QUIETNESS)
 * @param touristPreference 관광지 성향 (TOURIST_PREFERENCE)
 * @param shadePreference 그늘 (SHADE_PREFERENCE)
 * @param slopePreference 경사 (SLOPE_PREFERENCE)
 */
@ConfigurationProperties(prefix = "gabolle.recommendation.baseline.alignment")
public record PreferenceAlignmentWeights(
		Double locality,
		Double quietness,
		Double touristPreference,
		Double shadePreference,
		Double slopePreference) {

	/**
	 * 골목·현지 성향. 이 다섯 상수는 {@code BaselineCandidateScorer} 가
	 * {@code user_place_code_map} 을 조회할 때 쓰는 취향 코드와 같은 문자열이다. 한쪽만 바뀌면
	 * {@link #weightFor(String)} 이 예외를 낸다 — 조용히 0 이 되는 것보다 낫다.
	 */
	public static final String LOCALITY = "LOCALITY";

	/** 조용함. */
	public static final String QUIETNESS = "QUIETNESS";

	/** 관광지 성향. */
	public static final String TOURIST_PREFERENCE = "TOURIST_PREFERENCE";

	/** 그늘. */
	public static final String SHADE_PREFERENCE = "SHADE_PREFERENCE";

	/** 경사. */
	public static final String SLOPE_PREFERENCE = "SLOPE_PREFERENCE";

	public PreferenceAlignmentWeights {
		locality = nonNegative(locality, LOCALITY);
		quietness = nonNegative(quietness, QUIETNESS);
		touristPreference = nonNegative(touristPreference, TOURIST_PREFERENCE);
		shadePreference = nonNegative(shadePreference, SHADE_PREFERENCE);
		slopePreference = nonNegative(slopePreference, SLOPE_PREFERENCE);

		// 합계 1 은 강제하지 않지만 전부 0 은 거부한다. 비율만 쓰이므로 합계는 상관없는데,
		// 전부 0 이면 weightedAverage 가 늘 "못 구했다" 를 돌려주고 취향 정렬이 통째로 사라진
		// 채 아무 오류도 안 난다. 정말 끄려면 weights.preference-alignment 를 0 으로 둔다 —
		// 그쪽은 총점 구성에 남으므로 껐다는 사실이 결과에 드러난다.
		double sum = locality + quietness + touristPreference + shadePreference + slopePreference;
		if (sum <= 0.0) {
			throw new IllegalArgumentException(
					"gabolle.recommendation.baseline.alignment 다섯 값이 모두 0 이다. "
							+ "취향 정렬을 끄려면 gabolle.recommendation.baseline.weights.preference-alignment "
							+ "를 0 으로 두십시오");
		}
	}

	private static double nonNegative(Double value, String dimension) {
		double resolved = (value == null) ? 1.0 : value;
		if (resolved < 0.0) {
			// 음수를 받으면 그 차원이 조용히 뒤집힌다 — 취향이 맞을수록 점수가 깎이는데
			// 이유 코드에는 PREF_ALIGNED_ 가 그대로 붙어, 방향만 반대인 결과가 나온다.
			throw new IllegalArgumentException("gabolle.recommendation.baseline.alignment."
					+ dimension.toLowerCase(Locale.ROOT).replace('_', '-') + " 은 0 이상이어야 한다: " + resolved);
		}
		return resolved;
	}

	/**
	 * 취향 코드 하나의 비율.
	 *
	 * <p>모르는 코드는 0 이 아니라 예외다. 0 을 돌려주면 차원을 새로 추가하고 등록을 잊었을
	 * 때 그 차원이 조용히 순위에서 사라진다 — 점수는 계산되고 결과도 나오는데 그 축만 없다.
	 *
	 * @param preferenceCode {@link #LOCALITY} 등 다섯 중 하나. 대소문자는 가리지 않는다
	 */
	public double weightFor(String preferenceCode) {
		String code = (preferenceCode == null) ? "" : preferenceCode.toUpperCase(Locale.ROOT);
		return switch (code) {
			case LOCALITY -> this.locality;
			case QUIETNESS -> this.quietness;
			case TOURIST_PREFERENCE -> this.touristPreference;
			case SHADE_PREFERENCE -> this.shadePreference;
			case SLOPE_PREFERENCE -> this.slopePreference;
			default -> throw new IllegalArgumentException("알 수 없는 취향 차원이다: " + preferenceCode
					+ ". 차원을 추가했다면 PreferenceAlignmentWeights 에도 등록해야 한다");
		};
	}

	/**
	 * 값이 구해진 차원들만 모아 가중평균을 낸다. 하나도 없으면 {@code null} — 호출부는 그
	 * 경우 취향 정렬 항을 총점에 더하지 않는다.
	 *
	 * <p>있는 차원들의 비율 합으로 다시 정규화한다. 비율을 그대로 곱해 더하면 장소 표식이
	 * 비어 있어 못 구한 차원만큼 기여가 줄어드는데, 그것은 데이터가 없다는 이유로 그 장소를
	 * 깎는 것이라 취향이 안 맞아서 깎이는 것과 구별되지 않는다.
	 *
	 * @param alignments 취향 코드 → 정렬도(0~1). 채점기가 값을 구한 차원만 담는다.
	 *     {@code null} 값이 든 항목은 못 구한 것으로 보고 건너뛴다
	 * @return 가중평균, 또는 낼 근거가 없으면 {@code null}
	 */
	public Double weightedAverage(Map<String, Double> alignments) {
		if (alignments == null || alignments.isEmpty()) {
			return null;
		}
		double weightedSum = 0.0;
		double weightTotal = 0.0;
		for (Map.Entry<String, Double> entry : alignments.entrySet()) {
			if (entry.getValue() == null) {
				continue;
			}
			double weight = weightFor(entry.getKey());
			weightedSum += weight * entry.getValue();
			weightTotal += weight;
		}
		if (weightTotal <= 0.0) {
			// 값이 구해진 차원들의 비율이 전부 0 이다. "못 구했다" 와 같게 다룬다 — 0.0 은
			// "전혀 안 맞는다" 로 읽히는데, 그 축을 안 보기로 한 것과 정반대의 뜻이다.
			return null;
		}
		return weightedSum / weightTotal;
	}

	/**
	 * 실제로 쓰인 차원별 비율. {@code scoreComponents} 에 남겨 두면 "이 장소가 왜 이 순위인가"
	 * 를 설명하는 쪽이 읽을 수 있다. 값이 구해진 차원만 담긴다.
	 *
	 * @param alignments {@link #weightedAverage(Map)} 에 넘긴 것과 같은 지도
	 */
	public Map<String, Double> weightsUsed(Map<String, Double> alignments) {
		Map<String, Double> used = new LinkedHashMap<>();
		if (alignments == null) {
			return used;
		}
		for (Map.Entry<String, Double> entry : alignments.entrySet()) {
			if (entry.getValue() == null) {
				continue;
			}
			used.put(entry.getKey(), weightFor(entry.getKey()));
		}
		return used;
	}
}
