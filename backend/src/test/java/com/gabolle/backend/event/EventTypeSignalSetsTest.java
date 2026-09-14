package com.gabolle.backend.event;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.event.domain.EventType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 두 신호 목록이 서로 어긋나지 않는지 본다 — S15P21E201-549.
 *
 * <h2>🔴 이 검사가 막는 것</h2>
 *
 * {@link EventType} 에는 목록이 둘 있다.
 * <ul>
 * <li><b>행동 관찰</b>({@code isBehaviorSignal}) — 개인화를 끈 사람에게 <b>안 적을</b> 것</li>
 * <li><b>취향 신호</b>({@code isTasteSignal}) — 취향 벡터가 <b>셀</b> 것</li>
 * </ul>
 *
 * 세는 것은 안 모으는 것의 <b>부분집합</b>이어야 한다. 뒤집히면 <b>"세기는 하는데 껐어도
 * 모이는"</b> 종류가 생기고, 그건 어느 화면에도 오류로 나타나지 않는다 — 배치는 초록이고
 * 스위치는 꺼져 있는데 값만 자란다.
 *
 * <p>목록이 둘로 나뉜 것 자체는 의도다. 세는 쪽은 아직 좁고, 안 모으는 쪽은 넓어야 한다.
 * 그래서 "같은가" 가 아니라 "포함하는가" 를 잰다.
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

	/**
	 * 🔴 이 검사가 2026-09-11 에 실제로 있었던 고장을 막는다.
	 *
	 * <p>{@code TasteVectorFoldService} 가 {@code "PLACE_LIKE"} 처럼 <b>대문자</b>로 적은 목록을
	 * {@code event_outbox.event_type} 과 비교하고 있었다. 그 칸에 실제로 들어가는 값은
	 * {@link EventType#wireName()} 이 만드는 소문자라 <b>한 건도 안 맞았다.</b> 계측이 아직
	 * 없어 결과가 0 인 것과 구분이 안 돼서, 계측이 붙어도 아무도 몰랐을 것이다.
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
