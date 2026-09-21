package com.gabolle.backend.recommendation.application;

import java.util.Locale;
import java.util.Map;

import com.gabolle.backend.recommendation.adapter.EngineCandidate;

/**
 * 다양성 재정렬이 "같은 것" 으로 보는 두 축.
 *
 * 키를 못 구하면 {@code null} 을 돌려주고 부르는 쪽이 비교 대상에서 뺀다 — 못 구한 것들을
 * 한 덩어리로 묶으면 표식이 덜 채워진 장소가 서로를 깎아 다양성 이름으로 뒤로 밀린다.
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
	 * 지역 칸. 행정구가 아니라 대략 1km 의 굵은 좌표 칸이고, 없으면 {@code null}.
	 *
	 * 굵게 만드는 일은 채점기({@code BaselineCandidateScorer.localityBucket})가 한다 —
	 * 정밀 좌표가 애초에 {@code feature_values} 에 들어가면 안 되기 때문이다.
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
