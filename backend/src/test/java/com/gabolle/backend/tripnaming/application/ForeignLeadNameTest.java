package com.gabolle.backend.tripnaming.application;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 영어·일본어·중국어 이름 틀의 맨 앞에 한글 장소 이름이 오지 않는다(S15P21E201-1995). */
class ForeignLeadNameTest {

	@Test
	@DisplayName("🔴 첫 장소가 한글뿐이면 다국어 이름이 있는 장소를 앞세운다 — 개수는 그대로")
	void foreignNameLeads() {
		List<String> names = TripNameSuggestionService.leadWithForeignName(
				List.of("젤라또조이", "Gwangalli Beach", "카페오뜨"));
		assertThat(names).containsExactly("Gwangalli Beach", "젤라또조이", "카페오뜨");
	}

	@Test
	@DisplayName("🔴 모두 한글이면 「Busan」을 앞세운다")
	void allHangulFallsBackToBusan() {
		List<String> names = TripNameSuggestionService.leadWithForeignName(List.of("젤라또조이", "카페오뜨"));
		assertThat(names.get(0)).isEqualTo("Busan");
		assertThat(names).hasSize(2);
	}

	@Test
	@DisplayName("첫 장소에 이미 다국어 이름이 있으면 그대로")
	void unchangedWhenFirstIsForeign() {
		List<String> names = List.of("Haeundae Beach", "젤라또조이");
		assertThat(TripNameSuggestionService.leadWithForeignName(names)).isSameAs(names);
	}
}
