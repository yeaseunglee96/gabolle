package com.gabolle.backend.place.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 탐색 갈래 가운데 <b>자료가 있을 때만 나타나는</b> 갈래 (S15P21E201-2051 맛집 · S15P21E201-2059 카페).
 *
 * <p>{@link InterestTagCode} 의 여덟 갈래는 자료가 없어도 접힌 줄로 항상 그려야 해서 응답이 늘 여덟을 전부 돌려준다.
 * 여기 있는 갈래는 반대다 — 적재가 돌기 전에는 응답에 아예 없고, 돌고 나면 {@code keys} 맨 뒤에 건수와 함께 붙는다.
 * 앱은 0곳이거나 없는 갈래를 목록에서 거르므로, 서버에 자료가 들어오기 전에도 앱을 먼저 배포할 수 있다.
 *
 * <p>같은 서랍({@code place_feature.feature_type = 'INTEREST_TAG'})에 살지만 여덟 갈래와 섞지 않는다. 섞으면
 * {@code displayOrder()} 가 아홉이 되어 「항상 여덟」을 기대하는 곳(화면·시험·일정 조립)이 조용히 달라진다.
 * 사전({@code place_feature_code})에는 둘 다 들어 있고, 자바 목록과 같은지는
 * {@code PlaceFeatureCodeMapTest} 가 잰다.
 *
 * <p>🔴 <b>선언 순서가 곧 {@code /facets} 에 나오는 순서다.</b> 맛집이 먼저, 카페가 바로 뒤다 — 화면의 칩 순서가 이 순서를 따른다.
 * 낱말 글자순으로 두면 {@code LOCAL_CAFE} 가 {@code LOCAL_RESTAURANT} 보다 앞서 카페가 맛집 앞에 선다.
 */
public enum ExtraInterestTagCode {

	/** 맛집 — 공공기관이 인증했거나 현지인이 추천한 식당. {@link CertifiedRestaurant}. */
	LOCAL_RESTAURANT("맛집"),

	/**
	 * 카페 — 맛집과 같은 틀(구 → 동)로 고르는 갈래 (S15P21E201-2059). 온보딩 취향의 {@code CATEGORY_TAG/CAFE_HEALING}
	 * (카페·힐링)과는 다른 서랍의 다른 낱말이다.
	 */
	LOCAL_CAFE("카페");

	private final String labelKo;

	ExtraInterestTagCode(String labelKo) {
		this.labelKo = labelKo;
	}

	/** 화면에 보여줄 한국어 이름. 서버가 주는 것은 {@link InterestTagCode#labelKo()} 와 같은 까닭이다. */
	public String labelKo() {
		return this.labelKo;
	}

	/** 모든 낱말, 선언 순서대로. */
	public static List<ExtraInterestTagCode> all() {
		return List.of(values());
	}

	/** 모르는 코드는 빈 값으로 답한다 — {@link InterestTagCode#from} 과 같은 규칙이다. */
	public static Optional<ExtraInterestTagCode> from(String code) {
		if (code == null || code.isBlank()) {
			return Optional.empty();
		}
		String normalized = code.trim().toUpperCase();
		return Arrays.stream(values()).filter(tag -> tag.name().equals(normalized)).findFirst();
	}
}
