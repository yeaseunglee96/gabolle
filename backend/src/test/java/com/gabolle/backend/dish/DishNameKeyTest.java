package com.gabolle.backend.dish;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.dish.domain.DishNameKey;

import static org.assertj.core.api.Assertions.assertThat;

/** 같은 음식을 같은 열쇠로 모으는가. 열쇠가 어긋나면 10초 넘게 걸리는 그림을 두 번 만든다. */
class DishNameKeyTest {

	@Test
	@DisplayName("앞뒤 공백과 가운데 여러 칸을 같은 열쇠로 모은다")
	void whitespaceDoesNotSplitTheSameDish() {
		assertThat(DishNameKey.of("  돼지국밥 ")).isEqualTo(DishNameKey.of("돼지국밥"));
		assertThat(DishNameKey.of("해물  파전")).isEqualTo(DishNameKey.of("해물 파전"));
	}

	@Test
	@DisplayName("대소문자가 달라도 같은 열쇠다 — 영어 메뉴판이 섞여 들어온다")
	void caseDoesNotSplitTheSameDish() {
		assertThat(DishNameKey.of("Bibimbap")).isEqualTo(DishNameKey.of("bibimbap"));
	}

	@Test
	@DisplayName("전각으로 적힌 것도 같은 열쇠다")
	void fullWidthFormsAreFolded() {
		assertThat(DishNameKey.of("ＫＩＭＣＨＩ")).isEqualTo(DishNameKey.of("kimchi"));
	}

	@Test
	@DisplayName("빈 이름은 빈 열쇠다 — 부르는 쪽이 이것으로 거절한다")
	void emptyNameGivesEmptyKey() {
		assertThat(DishNameKey.of(null)).isEmpty();
		assertThat(DishNameKey.of("   ")).isEmpty();
	}

	/**
	 * 다른 음식이 같은 열쇠로 모이면 엉뚱한 그림이 붙는다. 공백을 지우지 않고 한 칸으로 모으는
	 * 이유가 이것이다 — 지우면 뜻이 다른 이름들도 섞일 길이 열린다.
	 */
	@Test
	@DisplayName("🔴 다른 음식은 다른 열쇠로 남는다")
	void differentDishesStayDifferent() {
		assertThat(DishNameKey.of("돼지국밥")).isNotEqualTo(DishNameKey.of("소고기국밥"));
		assertThat(DishNameKey.of("비빔냉면")).isNotEqualTo(DishNameKey.of("물냉면"));
	}
}
