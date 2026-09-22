package com.gabolle.backend.preference.domain;

import java.util.List;
import java.util.Map;

/**
 * 행동 이벤트가 취향에 미치는 힘과, 그 이벤트를 <b>어떻게 세는가</b>.
 *
 * <h2>🔴 왜 도메인에 있나 (S15P21E201-1500)</h2>
 *
 * 예전에는 접는 배치({@code BehaviorTasteFolder}) 안에만 있었다. 이제 카프카 소비자도
 * <b>같은 값으로 같은 판정</b>을 해야 한다. 두 곳에 두면 반드시 어긋나고, 어긋나면
 * 「배치가 만든 값과 소비자가 만든 값이 다르다」가 <b>오류 없이</b> 생긴다 — 아무것도 안
 * 빨개지고 추천만 조용히 이상해진다.
 *
 * <p>그래서 값도 분류도 여기 한 벌만 둔다. 새 신호를 더할 때 고치는 자리도 여기 하나다.
 */
public final class TasteSignal {

	/**
	 * 이벤트 하나가 그 장소의 태그 쪽으로 미는 힘.
	 *
	 * <p>🔴 {@code place_view} 가 작은 것은 <b>「목록 맨 위」가 곧 취향이 되는 것을 막기</b>
	 * 위해서다. 사람은 위에 있는 것을 더 보고, 위에 있는 이유는 지금 추천이 그렇게 정했기
	 * 때문이다. 크게 주면 추천이 자기가 고른 것을 근거로 자기를 강화한다.
	 *
	 * <p>🔴 {@code itinerary_remove} 가 <b>약한</b> 부정인 것은 일정에서 빼는 이유가 「싫어서」만이
	 * 아니기 때문이다 — 문 닫았고, 비 오고, 시간이 없다. 운영 사유가 적힌 것은 아예 안 세고,
	 * 안 적힌 것도 확신하지 않는다.
	 *
	 * <p>여기 없는 취향 신호({@code itinerary_replace}·{@code route_skip})는 <b>아직 아무도 안
	 * 만들어서 payload 모양이 안 정해졌다.</b> 모양을 모르는 채로 기여값을 적으면 그것이 계약이
	 * 된다 — 만드는 쪽이 정해지면 그때 한 줄씩 더한다.
	 *
	 * <p>{@code place_like_removed}(하트 끔)도 여기 <b>없다.</b> 그건 미는 힘이 아니라
	 * <b>되돌리는</b> 것이라, 「직전에 무엇이었나」의 힘을 빼는 방식으로 다룬다
	 * (S15P21E201-1506).
	 */
	private static final Map<String, Double> CONTRIBUTION = Map.of(
			"place_like", 1.0,
			"place_visit", 0.5,
			"place_view", 0.1,
			"place_dislike", -1.0,
			"itinerary_remove", -0.5);

	/**
	 * <b>상태</b> 인 이벤트 — 켜짐/꺼짐이라 「마지막 것」이 곧 현재다.
	 *
	 * <p>같은 장소를 두 번 좋아할 수는 없다. 그래서 반복은 뜻이 없고, 이력에서 세는 것이
	 * 아니라 <b>마지막 하나</b>로 판정한다. 배치는 질의에서, 소비자는
	 * {@code user_place_taste_state} 표에서 그 「마지막」을 얻는다.
	 */
	private static final List<String> STATE_EVENTS =
			List.of("place_like", "place_like_removed", "place_dislike");

	/**
	 * 상태를 <b>끄는</b> 이벤트 — 마지막이 이것이면 기여가 없다. 하트를 아예 안 누른 것과 같다.
	 *
	 * <p>{@link #CONTRIBUTION} 에 {@code 0.0} 으로 넣지 <b>않는</b> 이유가 있다. 기여가 0 이어도
	 * 관측으로 세어지면 「끈 하트 두 개」가 뒷받침 수를 채워 성분을 만들어 낸다.
	 */
	private static final List<String> STATE_CLEARING_EVENTS = List.of("place_like_removed");

	/**
	 * <b>반복이 뜻을 가지는</b> 이벤트. 같은 장소를 두 번 본 것은 한 번 본 것과 다르고,
	 * 두 번 간 것은 한 번 간 것과 다르다. 상태가 아니므로 올 때마다 그대로 더한다.
	 */
	private static final List<String> REPEATABLE_EVENTS = List.of("place_view", "place_visit");

	/** payload 에 장소가 <b>여럿</b> 실리는 이벤트 — {@code place_ids} 배열. */
	private static final List<String> MANY_PLACE_EVENTS = List.of("itinerary_remove");

	/**
	 * 이보다 적게 관측된 <b>행동</b> 성분은 안 쓴다.
	 *
	 * <p>🔴 한 번 누른 것을 확신처럼 다루지 않는다. {@code UserTasteWeight} 가 {@code SURVEY} 가
	 * 아닌 성분에 {@code support=0} 을 DB 에서 거부하는 것과 같은 정신이다.
	 *
	 * <p>🔴 <b>거르는 자리가 둘이다</b> (S15P21E201-1500). 배치는 <b>만들 때</b> 거른다 — 전
	 * 이력을 세고 나서 모자란 것을 안 내보낸다. 소비자는 그럴 수가 없다. 관측이 하나씩 쌓이므로
	 * 첫 번째에서 이미 행을 만들고, 두 번째에 2 가 된다. 그래서 <b>읽을 때</b> 한 번 더 거른다
	 * ({@code TasteWeightComponent.merge}). 두 자리가 같은 값을 봐야 해서 여기 둔다.
	 */
	public static final int MIN_SUPPORT = 2;

	private TasteSignal() {
	}

	/**
	 * 이 이벤트가 미는 힘. 취향 신호가 아니거나 되돌리는 이벤트면 {@code null} 이다.
	 *
	 * <p>🔴 <b>0.0 이 아니라 {@code null} 이다.</b> 「힘이 0 이다」와 「셀 대상이 아니다」는
	 * 다르다. 0 으로 주면 부르는 쪽이 관측으로 세어 버린다.
	 */
	public static Double contributionOf(String eventType) {
		return CONTRIBUTION.get(eventType);
	}

	/** 켜짐/꺼짐 상태인가 — 마지막 하나로 판정해야 하는 이벤트인가. */
	public static boolean isState(String eventType) {
		return STATE_EVENTS.contains(eventType);
	}

	/** 상태를 끄는 이벤트인가. */
	public static boolean isStateClearing(String eventType) {
		return STATE_CLEARING_EVENTS.contains(eventType);
	}

	/** 반복이 뜻을 가지는가 — 올 때마다 더해야 하는 이벤트인가. */
	public static boolean isRepeatable(String eventType) {
		return REPEATABLE_EVENTS.contains(eventType);
	}

	/** 질의 파라미터로 넘길 쉼표 목록. */
	public static String stateEventsCsv() {
		return String.join(",", STATE_EVENTS);
	}

	public static String stateClearingEventsCsv() {
		return String.join(",", STATE_CLEARING_EVENTS);
	}

	public static String repeatableEventsCsv() {
		return String.join(",", REPEATABLE_EVENTS);
	}

	public static String manyPlaceEventsCsv() {
		return String.join(",", MANY_PLACE_EVENTS);
	}

}
