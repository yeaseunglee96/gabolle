package com.gabolle.backend.recommendation.application;

import java.util.Locale;
import java.util.Map;

import com.gabolle.backend.recommendation.adapter.EngineCandidate;

/**
 * 다양성 재정렬이 "같은 것" 으로 보는 두 축 (S15P21E201-548).
 *
 * <p>🔴 <b>키를 못 구하면 그 후보를 "혼자인 것" 으로 본다</b> — 다른 후보와 겹치지 않으므로
 * 아무것도 깎이지 않는다. 못 구한 것들을 한 덩어리로 묶으면 <b>정보가 없다는 이유로 서로를
 * 깎게 되고</b>, 표식이 덜 채워진 장소가 다양성 이름으로 뒤로 밀린다. 그 편향은 어느
 * 화면에도 안 나타난다. 그래서 키가 없으면 {@code null} 을 돌려주고, 부르는 쪽이 그것을
 * 비교 대상에서 뺀다.
 */
final class DiversityKeys {

	/** {@code feature_values} 에서 카테고리를 읽는 이름 — 채점기가 넣는다. */
	static final String CATEGORY_FEATURE = "category";

	/** 굵은 구역 번호 — 채점기가 좌표를 두 자리에서 잘라 만든 정수 쌍이다. */
	static final String LOCALITY_FEATURE = "localityBucket";

	private DiversityKeys() {
	}

	/** 카테고리. 없으면 {@code null}. */
	static String categoryOf(EngineCandidate candidate) {
		Object raw = value(candidate, CATEGORY_FEATURE);
		if (raw == null) {
			return null;
		}
		String text = raw.toString().trim();
		return text.isEmpty() ? null : text.toUpperCase(Locale.ROOT);
	}

	/**
	 * 지역 칸. 채점기가 넣어 둔 굵은 구역 번호를 그대로 쓴다. 없으면 {@code null}.
	 *
	 * <p>🔴 <b>행정구(구·군)가 아니라 굵은 좌표 칸이다.</b> {@code place} 표에 구 칸이 없고
	 * ({@code place_id} · 이름 · 카테고리 · 주소 · 좌표뿐), 주소는 값 목록이 없는 자유
	 * 문자열이라 거기서 구 이름을 뽑는 것은 파싱이다. 게다가 주소는 추천 후보 DTO
	 * ({@code PlaceCandidateResponse.Candidate})에 실려 오지 않고, 그 DTO 는 다른 담당의
	 * 자리라 여기서 칸을 늘리지 않았다.
	 *
	 * <p>칸이 목적에는 오히려 맞다 — 막으려는 것은 "한 동네가 상위를 독점하는 것" 이고,
	 * 부산의 구는 그 목적에 비해 넓다(해운대구 하나가 여러 생활권을 담는다). 대략 1km 다.
	 *
	 * <p>🔴 <b>여기서 좌표를 계산하지 않는다.</b> 굵게 만드는 일은 채점기가 하고
	 * ({@code BaselineCandidateScorer.localityBucket}), 그 이유는 정밀 좌표가 애초에
	 * {@code feature_values} 에 들어가지 않아야 하기 때문이다 — 들어간 뒤에 굵게 만들면
	 * 이미 저장돼 있다. 나중에 구 코드 칸이 생기면 채점기 쪽 한 곳만 바꾸면 된다.
	 */
	static String localityOf(EngineCandidate candidate) {
		Object raw = value(candidate, LOCALITY_FEATURE);
		if (raw == null) {
			return null;
		}
		String text = raw.toString().trim();
		return text.isEmpty() ? null : text;
	}

	private static Object value(EngineCandidate candidate, String key) {
		Map<String, Object> features = candidate.featureValues();
		return (features == null) ? null : features.get(key);
	}

}
