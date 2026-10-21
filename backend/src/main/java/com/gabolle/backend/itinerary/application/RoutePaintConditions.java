package com.gabolle.backend.itinerary.application;

import com.gabolle.backend.preference.application.PreferenceJson;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 코스 지도가 경로 선을 무엇으로 칠할지 정하는 「사용자가 고른 조건」 — S15P21E201-1895.
 *
 * <p>추천 단계에서 사용자는 경사와 그늘을 물음 두 개로 답한다({@code SLOPE_PREFERENCE}: 「피하고 싶어요」/「상관없어요」,
 * {@code SHADE_PREFERENCE}: 「그늘길 우선」/「상관없어요」). 지도는 <b>고른 것만</b> 선에 칠한다 — 안 골랐는데 경사 색을
 * 칠하면 조건과 무관한 색이 되고, 그것이 전에 지도에 「경로와 상관없는 표시」가 뜬다는 지적을 받은 이유다.
 *
 * <p>값을 읽는 규칙은 새로 만들지 않고 채점기·배치가 쓰는 {@link PreferenceJson} 한 곳을 쓴다 — 읽는 규칙이 둘로
 * 갈리면 추천은 「경사 피함」으로 받았는데 지도는 「안 골랐음」으로 읽는 일이 생긴다. 그 규칙의 점수(0~1)를 두 갈래로
 * 가른다.
 *
 * <ul>
 * <li>경사: 「피하고 싶어요」는 0.0 이고 「상관없어요」는 {@code null}(축을 안 본다). 옛 앱의 5단계 슬라이더(1~5 →
 * 0~1)도 오므로 {@value #AVOID_AT_MOST} 이하를 「피한다」로 본다.
 * <li>그늘: 「그늘길 우선」은 1.0 이고 「상관없어요」는 {@code null}. 슬라이더 값은 {@value #PREFER_AT_LEAST} 이상을
 * 「우선」으로 본다.
 * </ul>
 *
 * <p>고르지 않았거나(SELECTED 가 아님) 못 읽으면 둘 다 {@code false} 다 — 모르는 것을 「골랐다」로 지어내지 않는다.
 *
 * @param slopeAvoid 경사를 피하고 싶다고 골랐나
 * @param shadePrefer 그늘길을 우선하고 싶다고 골랐나
 */
public record RoutePaintConditions(boolean slopeAvoid, boolean shadePrefer) {

	/** 아무 조건도 안 골랐다. */
	public static final RoutePaintConditions NONE = new RoutePaintConditions(false, false);

	/** 이 값 이하의 경사 점수는 「경사를 피한다」. 낱말 답(AVOID = 0.0)과 슬라이더 1·2단계(0.0·0.25)가 걸린다. */
	static final double AVOID_AT_MOST = 0.25;

	/** 이 값 이상의 그늘 점수는 「그늘길 우선」. 낱말 답(PREFER = 1.0)과 슬라이더 4·5단계(0.75·1.0)가 걸린다. */
	static final double PREFER_AT_LEAST = 0.75;

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	/**
	 * 여행의 가장 최신 선호 판에서 읽는다. 판이 없으면(선호를 하나도 안 남긴 여행) {@link #NONE}.
	 *
	 * @param snapshot {@code TripRepository.findLatestSnapshot} 의 결과. {@code null} 이어도 된다
	 */
	public static RoutePaintConditions from(PreferenceSnapshot snapshot) {
		if (snapshot == null) {
			return NONE;
		}
		Double slope = PreferenceJson.scoreFor(snapshot, "SLOPE_PREFERENCE", MAPPER);
		Double shade = PreferenceJson.scoreFor(snapshot, "SHADE_PREFERENCE", MAPPER);
		return new RoutePaintConditions(slope != null && slope <= AVOID_AT_MOST,
				shade != null && shade >= PREFER_AT_LEAST);
	}
}
