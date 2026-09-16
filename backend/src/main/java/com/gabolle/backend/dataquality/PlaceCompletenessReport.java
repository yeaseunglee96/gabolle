package com.gabolle.backend.dataquality;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 장소 데이터의 칼럼별 채움률 기준선 — S15P21E201-278.
 *
 * <p>🔴 <b>비율이 아니라 개수와 전체를 담는다.</b> 비율만 적으면 전체 행 수가 늘 때마다
 * 조용히 값이 바뀌어서, 다음에 다시 쟀을 때 "채워서 늘었다" 와 "모수가 늘어서 줄었다" 를
 * 구분할 수 없다.
 *
 * <p>🔴 티켓이 원래 들자고 한 칼럼 중 <b>실제로 없는 것들</b>: 영문 주소(place 표에
 * {@code address_en} 칸 자체가 없다 — {@code name_en} 만 있다), 카카오 평점·야경 사진·
 * 체험 시간·기념품 항목(아직 어떤 {@code place_feature.feature_type} 으로도 만들어지지
 * 않았다 — {@code ck_place_feature_type} 21종 어디에도 없다). 없는 칼럼을 지어내 0으로
 * 채우면 "쟀는데 0이다" 와 "잴 곳이 없다" 를 구분할 수 없게 된다. 그래서 여기서는
 * {@link #featureTypeFilled} 에 <b>지금 실제로 존재하는 종류만</b> 담는다.
 */
public record PlaceCompletenessReport(
		OffsetDateTime measuredAt,
		long placeTotal,
		long nameEnFilled,

		/**
		 * {@code place_feature.feature_type} 별 채움 현황 — 키는 종류 이름, 값은
		 * {@link FillCount}(evidence_status 가 UNKNOWN 이 아닌 place 의 수 / 장소 전체 수).
		 *
		 * <p>🔴 place_feature 는 EAV(하나의 사실 = 한 행) 표라 "칼럼" 이라는 게 없다. 대신
		 * 종류(feature_type)마다 그 종류의 사실을 가진 장소가 몇 곳인지를 센다 — 그것이
		 * 이 표에서 "칼럼이 채워졌다" 에 대응하는 값이다.
		 */
		Map<String, FillCount> featureTypeFilled) {

	public record FillCount(long filled, long total) {
	}
}
