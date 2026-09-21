package com.gabolle.backend.batch.application;

import java.util.UUID;

/**
 * 한 사람을 접은 결과.
 *
 * @param tasteVectorId 결과로 현재가 된 판. {@link Action#UNCHANGED} 여도 기존 판을 가리킨다
 * @param weightCount 성분 몇 개를 적었는가. 건너뛴 차원은 행을 안 만들므로 세지 않는다
 * @param newEventCount 이 구간에서 새로 반영한 행동 수
 */
public record TasteVectorFoldOutcome(UUID userId, Action action, UUID tasteVectorId, int version, int weightCount,
		int newEventCount) {

	public enum Action {

		/** 설문이 바뀌었거나 새 행동이 있어 판을 새로 만들었다. */
		REBUILT,

		/**
		 * 성분은 그대로고 "어디까지 봤는가" 만 앞으로 옮겼다. 이것이 가장 흔한 결과여야
		 * 정상이다 — 날마다 REBUILT 가 나오면 접는 규칙이 흔들리고 있다는 뜻이다.
		 */
		WATERMARK_ADVANCED,

		/** 아무것도 안 했다. 이미 이 구간까지 본 판이 있다 — 같은 구간을 다시 돌린 경우다. */
		UNCHANGED,

		/**
		 * 접을 것이 없다. 설문도 안 냈고 행동도 없다. 이때 빈 벡터를 만들지 않는다 — 성분이
		 * 없는 벡터는 "취향이 없는 사람" 처럼 보이는데 사실은 "아직 안 물어본 사람" 이고,
		 * 한 번 섞으면 되돌릴 수 없다.
		 */
		NOTHING_TO_FOLD
	}
}
