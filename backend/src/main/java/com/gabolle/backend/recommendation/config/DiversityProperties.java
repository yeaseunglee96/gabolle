package com.gabolle.backend.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 다양성 재정렬 설정. 기본값이 켜짐이고, {@code enabled=false} 로 두면 점수 순서가 그대로
 * 나간다. 어떤 값으로 돌았는지는 후보 행마다 {@code score_components.diversity} 에 남는다.
 *
 * 카테고리·지역은 0.05 씩이다. 앞에 같은 것이 하나 있을 때 그만큼만 깎으므로 점수 차가 그보다
 * 큰 후보는 뒤집지 못한다. 상위가 한 카테고리로 몰리는 것은 그 후보들의 점수가 서로 비슷해서
 * 생기는 일이라, 큰 점수 차까지 뒤집으면 덜 맞는 곳을 위로 올리게 된다.
 *
 * <h2>🔴 음식은 0.10 — 카테고리보다 크다</h2>
 *
 * 두 가지가 다르다.
 *
 * <ul>
 * <li><b>고른 갈래가 상위를 채우는 것은 정상이다.</b> 「맛집」을 고른 사람에게 음식점이 많이
 *     나오는 건 맞는 동작이다. 그런데 그 갈래를 고르면 관심 항({@code weights.interest()},
 *     기본 0.20)이 음식점 전부에 똑같이 붙어서, 0.05 로는 갈래 쏠림을 되돌릴 수도 없고
 *     되돌려서도 안 된다.</li>
 * <li><b>같은 음식이 거듭되는 것은 정상이 아니다.</b> 그리고 하루에 밥집 자리가
 *     둘뿐이므로({@code ItineraryDraftService.mealsPerDay}) <b>두 번째에서 이미 갈라져야</b>
 *     한다. 세 번째까지 기다릴 자리가 없다.</li>
 * </ul>
 *
 * 같은 동네 음식점들의 점수 차는 대개 거리 항(0.30 × 반경 안의 차이)에서 오고 5km 반경에서
 * 수백 미터 차이는 0.05 아래다. 0.10 이면 다른 음식이 후보에 하나라도 있을 때 두 번째 자리를
 * 그쪽에 넘긴다.
 *
 * @param categoryPenalty 앞에 이미 뽑힌 같은 카테고리 하나당 깎는 점수
 * @param localityPenalty 앞에 이미 뽑힌 같은 지역 칸 하나당 깎는 점수. 칸의 크기(대략 1km)는
 *     설정이 아니다 — 정밀 좌표를 로그에 안 남기기 위한 굵기라 되돌릴 수 있으면 안 된다
 * @param cuisinePenalty 앞에 이미 뽑힌 같은 음식 표식 하나당 깎는 점수. 표식이 둘인 가게는
 *     둘 다 세어지므로 한 번에 두 번 깎일 수 있다
 */
@ConfigurationProperties(prefix = "gabolle.recommendation.diversity")
public record DiversityProperties(
		Boolean enabled,
		Double categoryPenalty,
		Double localityPenalty,
		Double cuisinePenalty) {

	public DiversityProperties {
		enabled = (enabled == null) ? Boolean.TRUE : enabled;
		categoryPenalty = nonNegative(categoryPenalty, 0.05, "category-penalty");
		localityPenalty = nonNegative(localityPenalty, 0.05, "locality-penalty");
		cuisinePenalty = nonNegative(cuisinePenalty, 0.10, "cuisine-penalty");
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
