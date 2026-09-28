package com.gabolle.backend.place.loader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 세미콜론 이름을 한 이름으로 — 운영(2026-09-25) 다섯 곳 그대로 (S15P21E201-1637). */
class PlaceNamesTest {

	@Test
	@DisplayName("앞 이름을 쓴다")
	void takesTheFirstName() {
		assertThat(PlaceNames.primary("남나리전복;엠아이알오")).isEqualTo("남나리전복");
		assertThat(PlaceNames.primary("오즈;Oddz")).isEqualTo("오즈");
		assertThat(PlaceNames.primary("선모텔;코리아나모텔")).isEqualTo("선모텔");
	}

	@Test
	@DisplayName("🔴 앞 이름이 한 글자면 뒤 이름 — 「구;경포횟집」의 「구」는 머리말(舊)이지 이름이 아니다")
	void aOneLetterPrefixIsNotAName() {
		assertThat(PlaceNames.primary("구;경포횟집")).isEqualTo("경포횟집");
		assertThat(PlaceNames.primary(" 구 ; 경포횟집 ")).isEqualTo("경포횟집");
	}

	@Test
	@DisplayName("세미콜론이 없으면 그대로, 빈 조각은 건너뛴다")
	void leavesOrdinaryNamesAlone() {
		assertThat(PlaceNames.primary("해운대해수욕장")).isEqualTo("해운대해수욕장");
		assertThat(PlaceNames.primary(null)).isNull();
		assertThat(PlaceNames.primary(";광안리")).isEqualTo("광안리");
	}
}
