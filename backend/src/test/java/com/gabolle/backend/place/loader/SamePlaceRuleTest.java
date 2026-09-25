package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.loader.SamePlaceRule.Kind;

/**
 * 같은 곳 규칙 (S15P21E201-1620) — 장소 합치기(S15P21E201-1619)가 운영 중복을 고른 규칙과 같아야 한다. 예는 그때 운영 짝이다.
 */
class SamePlaceRuleTest {

	@Test
	@DisplayName("이름은 괄호 속·빈칸·부호를 빼고 소문자로 맞춘다 — 「파라다이스 호텔 부산」 = 「파라다이스호텔부산」")
	void nameKeyIgnoresSpacesPunctuationAndParentheses() {
		assertThat(SamePlaceRule.nameKey("파라다이스 호텔 부산")).isEqualTo(SamePlaceRule.nameKey("파라다이스호텔부산"));
		assertThat(SamePlaceRule.nameKey("흰여울문화마을(Huinnyeoul Culture Village)")).isEqualTo("흰여울문화마을");
		assertThat(SamePlaceRule.nameKey("Café·Blue-Moon")).isEqualTo("cafébluemoon");
		assertThat(SamePlaceRule.nameKey("광안　다이닝")).as("전각 빈칸도 빈칸이다").isEqualTo("광안다이닝");
		assertThat(SamePlaceRule.nameKey("(주)")).isEmpty();
	}

	@Test
	@DisplayName("🔴 이름이 같고 150m 안이면 같은 곳 — 송정3대국밥(상가·오픈스트리트맵 19m)")
	void sameNameWithin150mIsTheSamePlace() {
		assertThat(SamePlaceRule.judge("송정3대국밥", "FOOD", "송정 3대 국밥", "FOOD", 19)).isEqualTo(Kind.SAME);
		assertThat(SamePlaceRule.judge("금수사", "CULTURE_TEMPLE", "금수사", "CULTURE_TEMPLE", 150)).isEqualTo(Kind.SAME);
	}

	@Test
	@DisplayName("🔴 흔한 이름이 150m 밖이면 다른 가게로 본다 — 막지 않는다")
	void commonNameBeyond150mIsNotBlocked() {
		assertThat(SamePlaceRule.judge("해운대횟집", "FOOD", "해운대횟집", "FOOD", 151)).isNull();
		assertThat(SamePlaceRule.judge("해운대횟집", "FOOD", "해운대 횟집 본점", "FOOD", 10)).as("이름이 다르면 가까워도 아니다")
				.isNull();
	}

	@Test
	@DisplayName("🔴 체인은 30m 안만 같은 곳, 30~150m 는 사람 확인 — 다른 지점일 수 있다")
	void chainsAreSameOnlyWithin30m() {
		assertThat(SamePlaceRule.judge("스타벅스", "CAFE_HEALING", "스타벅스", "CAFE_HEALING", 30)).isEqualTo(Kind.SAME);
		assertThat(SamePlaceRule.judge("스타벅스", "CAFE_HEALING", "Starbucks", "CAFE_HEALING", 5))
				.as("영어 이름은 비교용 이름이 달라 같은 곳이 아니다 — 합치기와 같다").isNull();
		assertThat(SamePlaceRule.judge("스타벅스", "CAFE_HEALING", "스타벅스", "CAFE_HEALING", 31)).isEqualTo(Kind.REVIEW_CHAIN);
		assertThat(SamePlaceRule.judge("스타벅스", "CAFE_HEALING", "스타벅스", "CAFE_HEALING", 151)).isNull();
	}

	@Test
	@DisplayName("🔴 해변·산책로·공원은 150m~1km 가 사람 확인 — 송정해수욕장 두 줄이 381m 였다")
	void wideCategoriesGoToReviewUpTo1km() {
		assertThat(SamePlaceRule.judge("송정해수욕장", "SEA_BEACH", "송정해수욕장", "NATURE_WALK", 381))
				.isEqualTo(Kind.REVIEW_WIDE);
		assertThat(SamePlaceRule.judge("송정해수욕장", "SEA_BEACH", "송정해수욕장", "SEA_BEACH", 100)).isEqualTo(Kind.SAME);
		assertThat(SamePlaceRule.judge("송정해수욕장", "SEA_BEACH", "송정해수욕장", "SEA_BEACH", 1_001)).isNull();
		assertThat(SamePlaceRule.judge("해운대횟집", "FOOD", "해운대횟집", "FOOD", 381)).as("넓은 갈래가 아니면 안 본다").isNull();
	}

	@Test
	@DisplayName("빈 이름끼리는 같다고 하지 않는다")
	void emptyNamesNeverMatch() {
		assertThat(SamePlaceRule.judge("(주)", "FOOD", "(주)", "FOOD", 0)).isNull();
		assertThat(SamePlaceRule.judge(null, "FOOD", null, "FOOD", 0)).isNull();
	}

	@Test
	@DisplayName("🔴 갈래가 없는 장소도 멈추지 않고 판정한다 — 운영에 84곳 있고, 운영 사본 재적재에서 실제로 멈췄다")
	void aMissingCategoryDoesNotStopTheJudgement() {
		assertThat(SamePlaceRule.judge("해운대", null, "해운대", null, 300)).isNull();
		assertThat(SamePlaceRule.judge("해운대", null, "해운대", "SEA_BEACH", 300)).isEqualTo(Kind.REVIEW_WIDE);
		assertThat(SamePlaceRule.judge("해운대", null, "해운대", null, 100)).isEqualTo(Kind.SAME);
	}
}
