package com.gabolle.backend.event;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.event.domain.EventType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 두 신호 목록이 서로 어긋나지 않는지 본다.
 *
 * <p>{@link EventType} 에는 목록이 둘 있다 — 행동 관찰({@code isBehaviorSignal})은 개인화를
 * 끈 사람에게 안 적을 것이고, 취향 신호({@code isTasteSignal})는 취향 벡터가 셀 것이다.
 * 세는 쪽이 안 모으는 쪽의 부분집합이어야 한다. 뒤집히면 세기는 하는데 껐어도 모이는
 * 종류가 생기고, 그건 어느 화면에도 오류로 나타나지 않는다.
 *
 * <p>둘이 다른 것은 의도다. 그래서 같은가가 아니라 포함하는가를 잰다.
 */
class EventTypeSignalSetsTest {

	@Test
	@DisplayName("🔴 취향 신호는 전부 행동 관찰이다 — 세는데 안 막는 종류가 있으면 껐어도 쌓인다")
	void everyTasteSignalIsAlsoABehaviorSignal() {
		Set<EventType> countedButNotBlocked = Arrays.stream(EventType.values())
				.filter(EventType::isTasteSignal)
				.filter(type -> !type.isBehaviorSignal())
				.collect(Collectors.toSet());

		assertThat(countedButNotBlocked)
				.withFailMessage("""
						취향 벡터가 세는데 수집 차단 목록에는 없는 종류가 있습니다.
						개인화를 끈 사람의 이 이벤트는 계속 쌓이고, 배치는 그걸 셉니다.
						EventType 의 BEHAVIOR_SIGNALS 에 추가하거나 TASTE_SIGNALS 에서 빼 주세요.

						%s""".formatted(countedButNotBlocked))
				.isEmpty();
	}

	@Test
	@DisplayName("🔴 사람이 직접 넣은 것은 행동 관찰이 아니다 — 껐다는 것이 '내가 고른 것도 잊으라' 는 뜻은 아니다")
	void explicitInputIsNotABehaviorSignal() {
		assertThat(EventType.PREFERENCE_SET.isBehaviorSignal()).isFalse();
		assertThat(EventType.CONSTRAINT_SET.isBehaviorSignal()).isFalse();
		assertThat(EventType.TRIP_CREATED.isBehaviorSignal()).isFalse();
	}

	@Test
	@DisplayName("🔴 운영 기록은 행동 관찰이 아니다 — 끊으면 개인화를 끈 사람의 장애를 조사할 수 없다")
	void operationalRecordsAreNotBehaviorSignals() {
		assertThat(EventType.RECOMMENDATION_REQUESTED.isBehaviorSignal()).isFalse();
		assertThat(EventType.RECOMMENDATION_FAILED.isBehaviorSignal()).isFalse();
	}

	@Test
	@DisplayName("🔴 S15P21E201-1689 — 노출은 추천 품질을 재는 기록이다: 동의와 무관하게 적고, 취향을 세는 데는 안 쓴다")
	void impressionMeasuresQualityNotTaste() {
		assertThat(EventType.RECOMMENDATION_IMPRESSION.isBehaviorSignal()).isFalse();
		assertThat(EventType.tasteSignalWireNames()).doesNotContain("recommendation_impression");
	}

	/**
	 * {@code TasteVectorFoldService} 가 대문자 목록을 {@code event_outbox.event_type} 과
	 * 비교하던 고장을 막는다. 그 칸에 실제로 들어가는 값은 {@link EventType#wireName()} 이
	 * 만드는 소문자라 한 건도 안 맞았고, 계측이 없어 결과가 0 인 것과 구분되지 않았다.
	 */
	@Test
	@DisplayName("🔴 신호 목록의 문자열은 표에 실제로 들어가는 값과 같다 — 소문자다")
	void wireNamesAreLowerCaseAsStored() {
		assertThat(EventType.behaviorSignalWireNames()).allSatisfy(name -> assertThat(name)
				.isEqualTo(name.toLowerCase()));
		assertThat(EventType.tasteSignalWireNames()).contains("place_like", "place_view", "route_skip");
		assertThat(EventType.behaviorSignalWireNames()).containsAll(EventType.tasteSignalWireNames());
	}
}
