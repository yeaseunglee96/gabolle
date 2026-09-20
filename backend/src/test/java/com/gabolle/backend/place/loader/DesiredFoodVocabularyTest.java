package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DesiredFoodVocabularyTest {

	@Test
	@DisplayName("상호명에 복국이 있으면 BOKGUK 이 붙는다")
	void 복국을_찾는다() {
		assertThat(DesiredFoodVocabulary.desiredFoodTags("금수복국")).containsExactly("BOKGUK");
		assertThat(DesiredFoodVocabulary.desiredFoodTags("초원복국")).containsExactly("BOKGUK");
	}

	@Test
	@DisplayName("복국이 없으면 빈 집합이다")
	void 없으면_빈다() {
		assertThat(DesiredFoodVocabulary.desiredFoodTags("어느 한정식")).isEmpty();
		assertThat(DesiredFoodVocabulary.desiredFoodTags(null)).isEmpty();
		assertThat(DesiredFoodVocabulary.desiredFoodTags("")).isEmpty();
	}

	@Test
	@DisplayName("🔴 아직 없는 5종(밀면·돼지국밥·씨앗호떡·회해산물·동래파전·부산어묵·낙곱새)은 이 클래스가 붙이지 않는다")
	void 아직_없는_것은_안_붙는다() {
		// MILMYEON·PORK_SOUP·SEAFOOD 는 AppFoodVocabulary 몫이라 여기서 중복하지 않고,
		// 나머지 넷은 이름으로 걸리는 가게가 거의 없어 아예 코드에 없다.
		assertThat(DesiredFoodVocabulary.desiredFoodTags("부경밀면")).isEmpty();
		assertThat(DesiredFoodVocabulary.desiredFoodTags("소문난돼지국밥")).isEmpty();
		assertThat(DesiredFoodVocabulary.desiredFoodTags("어느 씨앗호떡")).isEmpty();
		assertThat(DesiredFoodVocabulary.desiredFoodTags("동래파전집")).isEmpty();
		assertThat(DesiredFoodVocabulary.desiredFoodTags("삼진어묵")).isEmpty();
		assertThat(DesiredFoodVocabulary.desiredFoodTags("어느 낙곱새")).isEmpty();
	}
}
