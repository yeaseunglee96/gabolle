package com.gabolle.backend.event.application;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 앱이 보낸 키를 서버 어휘로 옮기는 자리 (S15P21E201-1481). */
class ClientPayloadKeysTest {

	@Test
	@DisplayName("🔴 place_id 가 placeId 로 옮겨진다 — 읽는 쪽(recommendation_exposure 뷰)이 찾는 이름이다")
	void movesPlaceIdToCamelCase() {
		Map<String, Object> moved = ClientPayloadKeys.canonical(
				Map.of("place_id", "haeundae-1"));

		assertThat(moved).containsEntry("placeId", "haeundae-1").doesNotContainKey("place_id");
	}

	@Test
	@DisplayName("표에 없는 키는 그대로 둔다 — snake_case 라고 다 틀린 것이 아니다")
	void leavesUnknownKeysAlone() {
		Map<String, Object> moved = ClientPayloadKeys.canonical(
				Map.of("place_id", "seomyeon-1", "surface", "home"));

		assertThat(moved).containsEntry("placeId", "seomyeon-1").containsEntry("surface", "home");
	}

	@Test
	@DisplayName("서버가 이미 맞게 보낸 것은 안 건드린다")
	void alreadyCanonicalPassesThrough() {
		Map<String, Object> moved = ClientPayloadKeys.canonical(Map.of("placeId", "nampo-1"));

		assertThat(moved).containsExactlyEntriesOf(Map.of("placeId", "nampo-1"));
	}

	@Test
	@DisplayName("🔴 둘 다 왔는데 값이 다르면 거절한다 — 하나를 조용히 버리는 것이 이 버그를 만든 방식이다")
	void rejectsWhenBothKeysDisagree() {
		Map<String, Object> both = new LinkedHashMap<>();
		both.put("place_id", "haeundae-1");
		both.put("placeId", "nampo-1");

		assertThatThrownBy(() -> ClientPayloadKeys.canonical(both))
				.isInstanceOf(ClientPayloadKeys.ConflictingKeysException.class)
				.isInstanceOf(IllegalArgumentException.class);   // 400 으로 번역되는 갈래여야 한다
	}

	@Test
	@DisplayName("둘 다 왔어도 값이 같으면 다툼이 아니다 — 옛 이름만 사라진다")
	void sameValueUnderBothKeysIsNotAConflict() {
		Map<String, Object> both = new LinkedHashMap<>();
		both.put("place_id", "haeundae-1");
		both.put("placeId", "haeundae-1");

		assertThat(ClientPayloadKeys.canonical(both))
				.containsExactlyEntriesOf(Map.of("placeId", "haeundae-1"));
	}

	@Test
	@DisplayName("들어온 순서를 지킨다 — 뒤죽박죽이면 같은 이벤트의 payload 가 실행마다 달라 보인다")
	void keepsInsertionOrder() {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("surface", "home");
		input.put("place_id", "seomyeon-1");
		input.put("rating", 5);

		assertThat(ClientPayloadKeys.canonical(input).keySet())
				.containsExactly("surface", "placeId", "rating");
	}

	@Test
	@DisplayName("빈 payload 와 없는 payload 는 그대로 나간다 — 여기서 모양을 지어내지 않는다")
	void emptyAndNullPassThrough() {
		assertThat(ClientPayloadKeys.canonical(null)).isNull();
		assertThat(ClientPayloadKeys.canonical(Map.of())).isEmpty();
	}

	@Test
	@DisplayName("중첩된 값 안쪽은 안 본다 — 한 겹만 옮긴다")
	void doesNotDescendIntoNestedValues() {
		Map<String, Object> nested = Map.of("detail", Map.of("place_id", "haeundae-1"));

		assertThat(ClientPayloadKeys.canonical(nested))
				.containsExactlyEntriesOf(Map.of("detail", Map.of("place_id", "haeundae-1")));
	}
}
