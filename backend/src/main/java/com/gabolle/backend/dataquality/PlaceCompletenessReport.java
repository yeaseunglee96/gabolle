package com.gabolle.backend.dataquality;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 장소 데이터의 칼럼별 채움률 기준선. 비율이 아니라 개수와 전체를 담는다 — 비율만 적으면
 * 다음에 다시 쟀을 때 "채워서 늘었다" 와 "모수가 늘어서 줄었다" 를 구분할 수 없다.
 *
 * <p>{@link #featureTypeFilled} 에는 지금 실제로 존재하는 종류만 담는다. 없는 칼럼을 지어내
 * 0 으로 채우면 "쟀는데 0" 과 "잴 곳이 없다" 가 구분되지 않는다.
 */
public record PlaceCompletenessReport(
		OffsetDateTime measuredAt,
		long placeTotal,
		long nameEnFilled,

		/**
		 * {@code place_feature.feature_type} 별 채움 현황. place_feature 는 하나의 사실이 한
		 * 행인 표라 칼럼이라는 것이 없어서, 종류마다 그 사실을 가진 장소가 몇 곳인지를 센다.
		 */
		Map<String, FillCount> featureTypeFilled) {

	public record FillCount(long filled, long total) {
	}
}
