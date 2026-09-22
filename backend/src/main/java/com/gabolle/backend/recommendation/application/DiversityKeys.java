package com.gabolle.backend.recommendation.application;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.gabolle.backend.recommendation.adapter.EngineCandidate;

/**
 * 다양성 재정렬이 "같은 것" 으로 보는 세 축.
 *
 * 키를 못 구하면 {@code null}(음식 종류는 빈 집합)을 돌려주고 부르는 쪽이 비교 대상에서 뺀다 —
 * 못 구한 것들을 한 덩어리로 묶으면 표식이 덜 채워진 장소가 서로를 깎아 다양성 이름으로 뒤로
 * 밀린다. 🔴 <b>「모른다」는 「같다」가 아니다.</b>
 */
final class DiversityKeys {

	/** {@code feature_values} 에서 카테고리를 읽는 이름 — 채점기가 넣는다. */
	static final String CATEGORY_FEATURE = "category";

	/** 굵은 구역 번호 — 채점기가 좌표를 두 자리에서 잘라 만든 정수 쌍이다. */
	static final String LOCALITY_FEATURE = "localityBucket";

	/**
	 * 음식 종류 표식 — 채점기가 넣는다. {@link #CATEGORY_FEATURE} 보다 <b>가는 축</b>이다.
	 *
	 * <p>카테고리만으로는 돼지국밥집과 칼국수집이 둘 다 {@code FOOD} 라 서로 구분되지 않는다.
	 * 그래서 「맛집」을 고르면 상위가 한 음식으로 채워졌다 (S15P21E201-1450 — 실서버에서 7곳 중
	 * 6곳이 돼지국밥).
	 */
	static final String CUISINE_FEATURE = "cuisine";

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

	/**
	 * 음식 종류들. 없으면 <b>빈 집합</b>이고, 한 가게가 둘 이상 가질 수 있어 하나가 아니다 —
	 * {@code AppFoodVocabulary.cuisineTags} 가 소분류와 상호명을 둘 다 보므로 「카페」이면서
	 * 상호명에 「돼지국밥」이 든 가게는 둘을 받는다.
	 *
	 * <p>음식점 대부분은 <b>비어 있다.</b> 지금 어휘가 넷뿐이라(회·카페/디저트·돼지국밥·밀면)
	 * 한식 일반이나 중식에는 표식이 안 붙는다. 그런 가게끼리는 이 축으로 안 깎인다 — 빈 것을
	 * 한 덩어리로 묶으면 한식집과 중식집이 서로를 깎는다. 어휘를 넓히는 것은 자료 쪽 일이고
	 * 이 코드는 안 바뀐다.
	 */
	static Set<String> cuisinesOf(EngineCandidate candidate) {
		Object raw = value(candidate, CUISINE_FEATURE);
		if (!(raw instanceof Collection<?> values)) {
			return Set.of();
		}
		Set<String> out = new LinkedHashSet<>();
		for (Object item : values) {
			if (item == null) {
				continue;
			}
			String text = item.toString().trim();
			if (!text.isEmpty()) {
				out.add(text.toUpperCase(Locale.ROOT));
			}
		}
		return out;
	}

	private static Object value(EngineCandidate candidate, String key) {
		Map<String, Object> features = candidate.featureValues();
		return (features == null) ? null : features.get(key);
	}

}
