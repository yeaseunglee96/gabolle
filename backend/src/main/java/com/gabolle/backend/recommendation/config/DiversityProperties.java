package com.gabolle.backend.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 다양성 재정렬 설정. 기본값이 켜짐이고, {@code enabled=false} 로 두면 점수 순서가 그대로
 * 나간다. 어떤 값으로 돌았는지는 후보 행마다 {@code score_components.diversity} 에 남는다.
 *
 * 기본값(카테고리 0.05 + 지역 0.05)이면 앞에 같은 것이 하나 있을 때 최대 0.10 을 깎으므로,
 * 점수 차가 0.10 보다 큰 후보는 뒤집지 못한다. 상위가 한 카테고리로 몰리는 것은 그 후보들의
 * 점수가 서로 비슷해서 생기는 일이라, 큰 점수 차까지 뒤집으면 덜 맞는 곳을 위로 올리게 된다.
 *
 * @param categoryPenalty 앞에 이미 뽑힌 같은 카테고리 하나당 깎는 점수
 * @param localityPenalty 앞에 이미 뽑힌 같은 지역 칸 하나당 깎는 점수. 칸의 크기(대략 1km)는
 *     설정이 아니다 — 정밀 좌표를 로그에 안 남기기 위한 굵기라 되돌릴 수 있으면 안 된다
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
			// 음수면 "같은 것을 더 뽑아라" 가 된다. 다양성 재정렬이 다양성을 줄이는데
			// 이름과 로그는 그대로라 찾기 어려운 오류가 된다.
			throw new IllegalArgumentException(
					"gabolle.recommendation.diversity." + key + " 는 0 이상이어야 한다: " + resolved);
		}
		return resolved;
	}
}
