package com.gabolle.backend.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 다양성 재정렬 설정 (S15P21E201-548).
 *
 * <p>🔴 <b>기본값이 켜짐이다.</b> S15P21E201-547 의 가중치 기록은 기본값을 중립으로 두어
 * 동작을 바꾸지 않았는데, 여기는 반대로 둔다 — 이 티켓의 완료 기준이 <i>"한 지역·카테고리의
 * 상위 결과 독점이 기준 이하로 내려간다"</i> 이고, 그것은 <b>동작이 바뀌어야 충족되는
 * 기준</b>이다. 장치만 넣고 끄면 티켓이 요구하는 것을 안 한 것이 된다.
 *
 * <p>대신 되돌리는 길을 남긴다 — {@code enabled=false} 면 점수 순서가 그대로 나간다.
 * 그리고 껐는지 켰는지와 어떤 값으로 돌았는지가 후보 행마다 기록되므로
 * ({@code score_components.diversity}) 순위가 왜 그런지는 언제든 되짚을 수 있다.
 *
 * <h2>🔴 벌점은 점수 차가 작을 때만 순서를 바꾼다</h2>
 *
 * <p>기본값(카테고리 0.05 + 지역 0.05)이면 앞에 같은 것이 하나 있을 때 최대 0.10 을
 * 깎는다. 즉 <b>점수 차가 0.10 보다 큰 후보를 뒤집지는 못한다.</b> 약해 보이지만 그것이
 * 맞는 동작이다 — 상위가 한 카테고리로 몰리는 것은 애초에 그 후보들의 점수가 서로 비슷해서
 * 생기는 일이고, 큰 점수 차를 다양성으로 뒤집으면 "덜 맞는 곳" 을 위로 올리는 셈이 된다.
 * 목록을 고르게 만드는 것과 추천을 망치는 것 사이의 선이 이 값이다.
 *
 * @param enabled 재정렬을 적용할 것인가
 * @param categoryPenalty 앞에 이미 뽑힌 <b>같은 카테고리</b> 하나당 깎는 점수
 * @param localityPenalty 앞에 이미 뽑힌 <b>같은 지역 칸</b> 하나당 깎는 점수.
 *     "같은 칸" 의 크기(대략 1km)는 설정이 아니다 — 채점기가 굵게 만들어 넘기고, 그
 *     굵기가 정밀 좌표를 로그에 안 남기기 위한 것이라 설정으로 되돌릴 수 있으면 안 된다
 */
@ConfigurationProperties(prefix = "gabolle.recommendation.diversity")
public record DiversityProperties(
		Boolean enabled,
		Double categoryPenalty,
		Double localityPenalty) {

	public DiversityProperties {
		enabled = (enabled == null) ? Boolean.TRUE : enabled;
		categoryPenalty = nonNegative(categoryPenalty, 0.05, "category-penalty");
		localityPenalty = nonNegative(localityPenalty, 0.05, "locality-penalty");
	}

	private static double nonNegative(Double value, double fallback, String key) {
		double resolved = (value == null) ? fallback : value;
		if (resolved < 0.0) {
			// 🔴 음수면 "같은 것을 더 뽑아라" 가 된다. 다양성 재정렬이 다양성을 줄이는데
			//    이름과 로그는 그대로라, 사람이 찾을 수 없는 종류의 오류다.
			throw new IllegalArgumentException(
					"gabolle.recommendation.diversity." + key + " 는 0 이상이어야 한다: " + resolved);
		}
		return resolved;
	}
}
