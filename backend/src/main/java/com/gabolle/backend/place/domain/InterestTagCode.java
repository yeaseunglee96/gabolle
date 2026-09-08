package com.gabolle.backend.place.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 로컬 탐색 아코디언의 여덟 갈래 (S15P21E201-473).
 *
 * <p>{@code place_feature.feature_type = 'INTEREST_TAG'} 의 {@code feature_key} 로 쓰는 코드값이다.
 * 장소가 어느 갈래인지는 <b>장소에 붙은 표식으로 판정한다</b> — 이름이나 카테고리 문자열을 코드에
 * 나열하지 않는다. "남포동 야시장" 이라는 이름에 "야시장" 이 들어 있다는 이유로 갈래를 정하면,
 * 이름에 그 말이 없는 야시장은 영원히 안 나온다.
 *
 * <h2>🔴 왜 DB CHECK 가 아니라 여기 있는가</h2>
 * {@code place_feature} 의 안쪽 코드값에는 일부러 CHECK 를 걸지 않았다(마이그레이션
 * {@code V20260904020000} 의 주석). 적재하는 쪽이 새 태그를 시험해 볼 수 있어야 하고, 코드값을
 * DB 제약으로 굳히면 태그 하나 늘릴 때마다 마이그레이션이 필요해진다.
 *
 * <p>대신 <b>화면이 그려야 하는 여덟 갈래는 여기서 고정한다.</b> 아코디언은 데이터가 없는 갈래도
 * 접힌 줄로 보여야 하므로, 갈래 목록이 데이터에 따라 늘고 줄면 안 된다. 그래서 조회 응답은 이
 * 여덟 개를 <b>항상 전부</b> 돌려주고 건수만 0 이 된다 — 그러지 않으면 화면이 갈래 목록을 자기
 * 코드에 박아야 하고, 그건 서버와 화면 양쪽에 같은 목록이 두 벌 생기는 것이다.
 */
public enum InterestTagCode {

	FESTIVAL("축제"),
	NIGHT_MARKET("야시장"),
	TRADITIONAL_MARKET("전통시장"),
	ACTIVITY("액티비티"),
	WALK("산책"),
	NATURE("자연"),
	NIGHT_VIEW("야경"),
	SOUVENIR_SHOP("기념품샵");

	/** {@code place_feature.feature_type} 값. 이 갈래들이 사는 자리다. */
	public static final String FEATURE_TYPE = "INTEREST_TAG";

	private final String labelKo;

	InterestTagCode(String labelKo) {
		this.labelKo = labelKo;
	}

	/**
	 * 화면에 보여줄 한국어 이름. 🔴 서버가 이것까지 주는 이유는 갈래가 늘거나 이름이 바뀔 때
	 * 앱을 다시 배포하지 않아도 되게 하려는 것이다 — 배포된 앱은 즉시 못 고친다.
	 */
	public String labelKo() {
		return this.labelKo;
	}

	/** 화면에 그려야 하는 순서. 선언 순서를 그대로 쓴다. */
	public static List<InterestTagCode> displayOrder() {
		return List.of(values());
	}

	/**
	 * 모르는 코드는 빈 값으로 답한다 — 예외를 던지지 않는다. 적재 쪽이 여기 없는 태그를 붙여
	 * 뒀을 수 있고, 그건 조회가 실패할 이유가 아니라 아코디언에 안 보일 이유다.
	 */
	public static Optional<InterestTagCode> from(String code) {
		if (code == null || code.isBlank()) {
			return Optional.empty();
		}
		String normalized = code.trim().toUpperCase();
		return Arrays.stream(values()).filter(it -> it.name().equals(normalized)).findFirst();
	}
}
