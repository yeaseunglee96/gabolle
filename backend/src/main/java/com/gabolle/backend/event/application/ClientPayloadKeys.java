package com.gabolle.backend.event.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 앱이 보낸 {@code payload} 의 키를 서버 어휘로 옮긴다.
 *
 * <h2>왜 필요한가</h2>
 *
 * 같은 {@code place_like} 이벤트가 키 두 모양으로 쌓이고 있다 — 서버({@code SavedPlaceService})는
 * {@code placeId}, 앱은 {@code place_id}. 그런데 payload 에서 장소를 꺼내는 유일한 자리인
 * {@code recommendation_exposure} 뷰는 {@code payload ->> 'placeId'} 로만 찾는다
 * (S15P21E201-1481).
 *
 * <h2>🔴 지금 당장 새는 것은 아니다 — 그래서 더 위험하다</h2>
 *
 * 그 뷰는 {@code RECOMMENDATION_IMPRESSION} 만 보는데, <b>앱은 그 이벤트를 아직 안 보낸다</b>
 * (S15P21E201-544). 지금 두 모양으로 쌓이는 {@code place_like} 는 아직 아무도 안 읽는다.
 *
 * <p>그래서 -544 가 나가는 날, 앱이 {@code place_id} 로 보내면 <b>그 노출 이벤트의 장소 칸이
 * 뷰에서 통째로 빈다.</b> 오류가 아니라 빈 칸이라 아무 데서도 안 드러난다. 그 뷰는
 * S15P21E201-546 이 <i>「분석 쿼리는 event_outbox 를 직접 읽지 않고 이 뷰를 읽는다」</i>고 못 박은
 * 유일한 조인 자리다. 행동을 취향 성분으로 귀속시키는 작업도 같은 키를 읽는다.
 *
 * <h2>왜 앱이 아니라 여기서 고치나</h2>
 *
 * 🔴 <b>앱스토어 배포라 강제 업데이트가 없다.</b> 앱을 고쳐도 옛 판을 쓰는 사람은 한참 동안
 * {@code place_id} 를 계속 보낸다. 서버가 어차피 두 키를 다 받아야 한다면, 받는 자리에서
 * 한 벌로 만드는 것이 「두 벌인 채로 읽는 쪽마다 분기하는 것」보다 낫다 — 분기는 언젠가
 * 한 곳이 빠지고, 빠진 것은 「행이 적게 나온다」로만 나타난다.
 *
 * <p>그래서 {@code Producer.CLIENT} 경로에서만 옮긴다. 서버가 만드는 이벤트는 이미 맞는
 * 어휘로 만들므로 손대지 않는다 — 거기까지 옮기면 이 표가 「고치는 곳」이 아니라
 * 「서버 코드가 아무 이름이나 써도 되는 곳」이 된다.
 *
 * <h2>안 하는 것</h2>
 *
 * <ul>
 * <li><b>중첩된 값은 안 본다.</b> 이벤트 payload 는 평평한 한 겹이고, 깊이 들어가면 어디까지
 *     옮긴 것인지 아무도 모르게 된다</li>
 * <li><b>표에 없는 키는 그대로 둔다.</b> 모르는 키를 추측해서 고치지 않는다 —
 *     {@code surface} 처럼 snake_case 가 아닌 것도 많고, 그건 틀린 것이 아니다</li>
 * <li><b>camelCase 로 «자동 변환»하지 않는다.</b> 규칙으로 돌리면 앱이 새 키를 하나 더할 때마다
 *     우리가 모르는 이름이 조용히 생긴다. 옮길 것은 여기 적힌 것뿐이다</li>
 * </ul>
 */
final class ClientPayloadKeys {

	/**
	 * 앱이 쓰는 이름 → 서버 이름.
	 *
	 * <p>여기에 줄을 더하는 것은 <b>앱이 이미 그 이름으로 보내고 있다</b>는 뜻이다. 앞으로 보낼
	 * 이름을 미리 적지 않는다 — 안 오는 이름이 표에 있으면 다음 사람이 그것을 계약으로 읽는다.
	 */
	private static final Map<String, String> ALIASES = Map.of(
			// frontend/src/analytics/appEvents.ts — place_like 의 payload
			"place_id", "placeId");

	private ClientPayloadKeys() {
	}

	/**
	 * @param payload 앱이 보낸 그대로. {@code null} 이면 {@code null}
	 * @return 키를 옮긴 새 맵. 순서는 들어온 순서를 지킨다
	 * @throws ConflictingKeysException 한 payload 에 옛 이름과 새 이름이 <b>둘 다</b> 있고 값이 다르다
	 */
	static Map<String, Object> canonical(Map<String, Object> payload) {
		if (payload == null || payload.isEmpty()) {
			return payload;
		}
		Map<String, Object> moved = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : payload.entrySet()) {
			String key = ALIASES.getOrDefault(entry.getKey(), entry.getKey());
			Object previous = moved.put(key, entry.getValue());
			// 🔴 둘 다 온 경우. 어느 쪽이 참인지 우리가 정할 수 없다. 하나를 조용히 버리는 것이
			//    애초에 이 버그를 만든 방식이라, 여기서는 거절한다. 값이 같으면 다툼이 아니다.
			if (previous != null && !Objects.equals(previous, entry.getValue())) {
				throw new ConflictingKeysException(key);
			}
		}
		return moved;
	}

	/** 같은 뜻의 키가 둘 다 왔고 값이 다르다 — 400 으로 답할 자리다. */
	static class ConflictingKeysException extends IllegalArgumentException {

		ConflictingKeysException(String canonicalKey) {
			super("같은 값을 가리키는 키가 둘 다 왔습니다: " + canonicalKey
					+ ". 어느 쪽이 맞는지 서버가 정할 수 없으니 하나만 보내 주세요.");
		}
	}
}
