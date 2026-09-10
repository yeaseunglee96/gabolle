package com.gabolle.backend.place.loader;

import java.util.List;

/**
 * 목록 근거 한 줄 — S15P21E201-826.
 *
 * @param storeId 상가업소번호. 장소 id 를 이 값에서 계산한다({@link SbizPlaceLoader#placeIdOf})
 * @param sourceCount 서로 다른 목록 몇 곳에 올랐는가
 * @param lists 그 목록 이름들. 점수의 근거를 사람이 되짚을 수 있게 함께 남긴다
 */
public record TruthSignalRow(String storeId, int sourceCount, List<String> lists) {

	/**
	 * 목록 수를 0~1 점수로 옮긴다.
	 *
	 * <p>다섯 곳 이상이면 1.0 이다. 실제 자료에서 여섯 곳이 최대이고 그 위는 한 곳뿐이라,
	 * 상한을 더 올려 봐야 가르는 것이 없으면서 한 곳 오른 가게의 점수만 낮아진다.
	 *
	 * <p>이 값은 <b>몇 개의 목록에 올랐는가</b>이지 측정된 인기도가 아니다. 그래서 증거
	 * 등급을 {@code ESTIMATED} 로 적는다.
	 */
	public double score() {
		return Math.min(1.0, this.sourceCount / 5.0);
	}
}
