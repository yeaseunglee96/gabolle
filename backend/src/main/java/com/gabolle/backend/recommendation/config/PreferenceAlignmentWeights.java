package com.gabolle.backend.recommendation.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 점수형 취향 다섯 차원이 {@code weights.preference-alignment} 를 나누는 <b>비율</b>
 * (S15P21E201-547).
 *
 * <p>🔴 <b>무엇이 문제였나.</b> {@code BaselineCandidateScorer} 는 LOCALITY(골목·현지
 * 성향) · QUIETNESS(조용함) · TOURIST_PREFERENCE(관광지 성향) · SHADE_PREFERENCE(그늘) ·
 * SLOPE_PREFERENCE(경사) 다섯을 각각 0~1 로 재고, 그 다섯을 <b>단순 평균</b> 한 값에
 * {@code weights.preferenceAlignment}(기본 0.10) 하나를 곱해 총점에 더한다. 그래서
 * 다섯이 서로를 희석한다 — 사용자가 "조용한 곳" 을 가장 강하게 답해도 그 축이 총점에
 * 기여할 수 있는 최대치는 0.10 ÷ 5 = <b>0.02</b> 이고, 거리(0.30)나 인기(0.10)가 그것을
 * 덮는다. 물어본 것이 순위에 거의 안 나타나면 설문은 장식이 된다.
 *
 * <p>이 기록은 그 단순 평균을 <b>가중 평균</b>으로 바꾸기 위한 값을 담는다.
 * {@link #weightedAverage(Map)} 가 그 계산이다.
 *
 * <h2>여기 있는 값은 총점 가중치가 아니다</h2>
 *
 * <p>다섯 값은 <b>0.10 을 나누는 비율</b>이다. 전부 2배로 해도 결과가 같다.
 * {@link BaselineEngineProperties.Weights} 의 여섯(거리·관심·분위기·음식·취향정렬·인기)은
 * 반대로 <b>총점에서 차지하는 절대 몫</b>이라 값을 키우면 총점 구성 자체가 바뀐다.
 * 성질이 다른 숫자라서 한 기록에 섞지 않았다 — 섞어 두면 "합계가 1 이어야 하나" 를 물을
 * 때마다 어느 쪽을 말하는지가 흐려진다. 그래서 설정 키도 {@code …baseline.weights.*} 안이
 * 아니라 그 옆의 {@code …baseline.alignment.*} 다.
 *
 * <p>비율로 둔 덕에 얻는 것이 하나 더 있다. <b>차원이 늘거나 줄어도 취향 그룹이 총점에서
 * 차지하는 몫이 변하지 않는다.</b> 다섯을 최상위 가중치로 올려 두면 여섯째 차원을 추가하는
 * 순간 취향 전체의 비중이 조용히 커지고, 거리·인기와의 균형이 아무도 의도하지 않은 채
 * 바뀐다. 그룹 전체를 키우고 싶으면 {@code weights.preference-alignment} 한 줄만 만진다.
 *
 * <h2>🔴 기본값이 전부 1.0 인 이유</h2>
 *
 * <p>비율이 모두 같으면 가중평균은 단순평균과 <b>같은 값</b>이다. 즉 이 설정을 넣어도
 * 순위는 한 칸도 안 바뀐다. 장치를 넣는 커밋에서 동작까지 바꾸면, 나중에 순위가 달라졌을 때
 * 그것이 장치 탓인지 값 탓인지 가를 수 없다. 값을 정하는 것은 데이터 담당의 실험이고
 * ({@link BaselineEngineProperties.Weights} javadoc 이 같은 이유로 합계 1 을 강제하지 않는다),
 * 이 커밋은 <b>그 실험이 가능한 상태를 만드는 것까지만</b> 한다.
 *
 * <p>설정 예 — 조용함을 다른 넷보다 세 배로 본다.
 *
 * <pre>
 * gabolle.recommendation.baseline.alignment.quietness=3.0
 * </pre>
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
	 * 골목·현지 성향.
	 *
	 * <p>🔴 이 다섯 상수는 {@code BaselineCandidateScorer} 가 {@code user_place_code_map}
	 * 을 조회할 때 쓰는 취향 코드와 <b>같은 문자열</b>이다. 한쪽만 바뀌면 그 차원의 비율을
	 * 못 찾아 {@link #weightFor(String)} 이 예외를 낸다 — 조용히 0 이 되는 것보다 낫다.
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

		// 🔴 합계 1 은 강제하지 않지만 **전부 0** 은 거부한다. 비율만 쓰이므로 0.2 다섯 개와
		//    1.0 다섯 개는 같은 결과를 낸다 — 합계를 강제할 이유가 없다. 반면 전부 0 이면
		//    나눌 것이 없어서 weightedAverage 가 늘 "못 구했다" 를 돌려주고, 취향 정렬이
		//    통째로 사라진 채 아무 오류도 안 난다. 그 상태는 "취향을 안 쓴다" 는 뜻이 아니라
		//    설정 실수다. 정말 끄고 싶으면 weights.preference-alignment 를 0 으로 둔다 —
		//    그쪽은 총점 구성에 남으므로 껐다는 사실이 결과에 드러난다.
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
			// 🔴 음수를 받으면 그 차원이 조용히 뒤집힌다 — 취향이 맞을수록 점수가 깎이고,
			//    그러면서 이유 코드에는 PREF_ALIGNED_<차원> 이 그대로 붙는다. 결과가 나오고
			//    설명까지 붙는데 방향만 반대라, 이건 사람이 찾을 수 없는 종류의 오류다.
			throw new IllegalArgumentException("gabolle.recommendation.baseline.alignment."
					+ dimension.toLowerCase(Locale.ROOT).replace('_', '-') + " 은 0 이상이어야 한다: " + resolved);
		}
		return resolved;
	}

	/**
	 * 취향 코드 하나의 비율.
	 *
	 * <p>🔴 모르는 코드는 0 이 아니라 예외다. 0 을 돌려주면 차원을 새로 추가하고 여기
	 * 등록을 잊었을 때 그 차원이 <b>조용히 순위에서 사라진다</b> — 점수는 계산되고 결과도
	 * 나오는데 그 축만 없다. 아무 오류도 안 나므로 아무도 못 찾는다.
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
	 * 값이 구해진 차원들만 모아 <b>가중평균</b>을 낸다. 하나도 없으면 {@code null} —
	 * 호출부는 그 경우 취향 정렬 항을 총점에 더하지 않는다.
	 *
	 * <p>이것이 {@code BaselineCandidateScorer} 의 단순평균
	 * ({@code alignments.values().stream()...average()}) 을 대신할 계산이다.
	 *
	 * <p>🔴 <b>왜 있는 차원들로 다시 정규화하나.</b> 비율을 그대로 곱해서 더하면, 장소
	 * 표식이 비어 있어서 못 구한 차원만큼 취향 정렬의 기여가 줄어든다. 그건 <b>우리 데이터가
	 * 없다는 이유로 그 장소를 깎는 것</b>이다 — 취향이 안 맞아서 깎이는 것과 구별되지 않고,
	 * 표식이 덜 채워진 장소가 영원히 뒤로 밀린다. 그 편향은 데이터를 채우는 쪽과 순위를 보는
	 * 쪽 어느 화면에도 안 나타난다. 지금 쓰이는 단순평균도 <b>있는 개수로</b> 나누므로 같은
	 * 성질을 갖는다 — 그래서 이 계산은 기존 동작을 바꾸는 것이 아니라 유지하는 쪽이다.
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
			// 🔴 값이 구해진 차원들의 비율이 하필 전부 0 이다. 평균을 낼 근거가 없으니
			//    "못 구했다" 와 같게 다룬다 — 0.0 을 돌려주면 "전혀 안 맞는다" 로 읽히고,
			//    그건 그 축을 안 보기로 한 것과 정반대의 뜻이다.
			return null;
		}
		return weightedSum / weightTotal;
	}

	/**
	 * 실제로 쓰인 차원별 비율. {@code scoreComponents} 에 남겨 두면 "이 장소가 왜 이 순위인가"
	 * 를 설명하는 S15P21E201-548(추천 이유 코드)이 그것을 읽을 수 있다. 값이 구해진 차원만
	 * 담기므로, 결과에 남는 것은 이번 계산에 실제로 들어간 비율뿐이다.
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
