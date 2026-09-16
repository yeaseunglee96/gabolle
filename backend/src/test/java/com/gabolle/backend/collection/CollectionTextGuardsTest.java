package com.gabolle.backend.collection;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.collection.domain.Collection;
import com.gabolle.backend.collection.domain.CollectionItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 긴 입력이 <b>DB 까지 가지 않고</b> 도메인에서 멈추는가 — S15P21E201-1037.
 *
 * <p>그전에는 이 엔티티들이 「비었는가」만 보고 「얼마나 긴가」를 안 봤다. 열 폭을 넘는 값은
 * 자바 검사를 전부 통과한 뒤 PostgreSQL 에서 거부되고, 그 예외를 잡는 어드바이스가 없어
 * <b>500</b> 으로 나갔다. 부르는 쪽은 자기 입력이 문제라는 것을 모른 채 재시도한다.
 *
 * <p>DB 가 없어도 도는 시험이다. 제약이 실제로 막는지는
 * {@link EndpointGuardsPostgresTest} 가 진짜 PostgreSQL 위에서 따로 본다 — 이 둘은
 * 같은 것을 두 층에서 보는 것이지 겹치는 것이 아니다.
 */
class CollectionTextGuardsTest {

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-16T09:00:00Z");

	@Test
	@DisplayName("🔴 완료 기준 — 열 폭을 넘는 컬렉션 이름은 도메인이 거절한다 (DB 까지 안 간다)")
	void aTooLongCollectionNameIsRejectedBeforeItReachesTheDatabase() {
		String tooLong = "가".repeat(Collection.NAME_MAX_LENGTH + 1);

		assertThatThrownBy(() -> Collection.of(UUID.randomUUID(), UUID.randomUUID(), tooLong, null, NOW))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining(String.valueOf(Collection.NAME_MAX_LENGTH));
	}

	@Test
	@DisplayName("상한 딱 맞는 길이는 통과한다 — 한 글자 차이로 멀쩡한 이름을 막지 않는다")
	void aNameExactlyAtTheCeilingIsAccepted() {
		String exact = "가".repeat(Collection.NAME_MAX_LENGTH);

		assertThatCode(() -> Collection.of(UUID.randomUUID(), UUID.randomUUID(), exact, null, NOW))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 길이는 글자 수로 센다 — 이모지가 든 이름이 자바 길이 때문에 거부되면 안 된다")
	void lengthIsCountedInCharactersNotJavaCodeUnits() {
		// 이모지 하나는 자바 문자열에서 두 칸을 차지하지만 varchar 는 한 글자로 센다.
		String withEmoji = "🍜".repeat(Collection.NAME_MAX_LENGTH);

		assertThatCode(() -> Collection.of(UUID.randomUUID(), UUID.randomUUID(), withEmoji, null, NOW))
				.as("자바 길이로 쟀다면 여기서 절반에서 잘렸을 것이다")
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("이름에 줄바꿈을 넣을 수 없다 — 카드가 두 줄로 밀리면 목록 전체가 어긋난다")
	void aNameCannotContainLineBreaks() {
		assertThatThrownBy(() -> Collection.of(UUID.randomUUID(), UUID.randomUUID(), "부산\n카페", null, NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("설명은 여러 줄을 받는다 — 붙여넣은 메모가 통째로 거부되면 안 된다")
	void aDescriptionMayContainLineBreaks() {
		Collection collection = Collection.of(UUID.randomUUID(), UUID.randomUUID(), "부산 카페",
				"첫 줄\n둘째 줄", NOW);

		assertThat(collection.getDescription()).isEqualTo("첫 줄\n둘째 줄");
	}

	@Test
	@DisplayName("빈 설명은 null 이다 — 「안 적었다」와 「빈 칸을 적었다」를 같게 둔다")
	void aBlankDescriptionBecomesNull() {
		Collection collection = Collection.of(UUID.randomUUID(), UUID.randomUUID(), "부산 카페", "   ", NOW);

		assertThat(collection.getDescription()).isNull();
	}

	@Test
	@DisplayName("🔴 완료 기준 — 항목의 이름·지역·사진 주소·메모도 각자 열 폭에서 멈춘다")
	void collectionItemFieldsAreBoundedToTheirColumnWidths() {
		UUID id = UUID.randomUUID();
		UUID collectionId = UUID.randomUUID();

		assertThatThrownBy(() -> CollectionItem.ofCustom(id, collectionId,
				"가".repeat(CollectionItem.NAME_MAX_LENGTH + 1), null, null, null, null, null, 0, NOW))
				.as("이름").isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> CollectionItem.ofCustom(id, collectionId, "가게",
				"가".repeat(CollectionItem.LOCALITY_MAX_LENGTH + 1), null, null, null, null, 0, NOW))
				.as("지역").isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> CollectionItem.ofCustom(id, collectionId, "가게", null, null, null,
				"https://example.test/" + "a".repeat(CollectionItem.PHOTO_URL_MAX_LENGTH), null, 0, NOW))
				.as("사진 주소").isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> CollectionItem.ofCustom(id, collectionId, "가게", null, null, null, null,
				"가".repeat(CollectionItem.NOTE_MAX_LENGTH + 1), 0, NOW))
				.as("메모").isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("🔴 업서트로 들어가는 장소 항목의 메모도 같은 규칙을 지난다")
	void notesTakeTheSamePathWhetherTheyGoThroughTheEntityOrTheUpsert() {
		assertThat(CollectionItem.normalizedNote("  바닷가 옆  ")).isEqualTo("바닷가 옆");
		assertThat(CollectionItem.normalizedNote("   ")).isNull();

		assertThatThrownBy(() -> CollectionItem.normalizedNote("가".repeat(CollectionItem.NOTE_MAX_LENGTH + 1)))
				.as("엔티티를 거치지 않는 길이라고 규칙이 빠지면 거기서 다시 500 이 난다")
				.isInstanceOf(IllegalArgumentException.class);
	}
}
