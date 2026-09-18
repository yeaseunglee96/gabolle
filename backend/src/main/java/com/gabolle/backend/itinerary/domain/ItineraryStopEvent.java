package com.gabolle.backend.itinerary.domain;

import java.time.Instant;

/**
 * 일정 진행 중 일어난 일 — S15P21E201-1325 (시안 ⑤).
 *
 * <h2>🔴 덮어쓰지 않고 쌓는다</h2>
 * 상태 한 줄만 두면 <b>「건너뛴 곳」과 「안 간 곳」을 가를 수 없다.</b> 나중에 「거기
 * 갔었나?」를 기억으로만 풀어야 하고, 그건 기록이 있는데 못 읽는 것과 같다.
 * 건너뛴 곳은 <b>안 간 곳</b>이다 — 다녀온 곳과 같은 모습으로 그리면 안 된다.
 *
 * <h2>🔴 정차지 이름은 {@code itemKey} 다</h2>
 * 일정은 고칠 때마다 항목을 통째로 복사한다({@link ItineraryRevision}). 항목 행 id 를
 * 적으면 한 번 고치는 순간 어제 남긴 기록이 <b>어느 정차지의 것인지 알 수 없게 된다.</b>
 *
 * @param itemKey {@link Type#START}·{@link Type#PAUSE} 는 정차지가 없는 사건이라 {@code null}
 * @param occurredAt 그 일이 일어난 시각. 기록한 시각과 다르다 — 신호가 늦게 올라올 수 있다
 */
public record ItineraryStopEvent(
		String eventId,
		String itineraryId,
		String itemKey,
		Type type,
		Instant occurredAt,
		String recordedBy,
		Instant createdAt) {

	public enum Type {
		START,
		PAUSE,
		/** GPS 가 알아챈 도착. 기본 경로다. */
		ARRIVE_AUTO,
		/** 사람이 직접 찍은 도착. GPS 가 약하거나 권한이 없을 때만 나오는 길이다. */
		ARRIVE_MANUAL,
		/** 이 정차지를 빼고 다음으로. 다녀온 것이 아니다. */
		SKIP;

		public boolean isArrival() {
			return this == ARRIVE_AUTO || this == ARRIVE_MANUAL;
		}

		/** 이 정차지를 「마쳤다」고 볼 사건인가 — 도착이든 건너뜀이든 다음으로 넘어간다. */
		public boolean settlesStop() {
			return isArrival() || this == SKIP;
		}

		public boolean needsStop() {
			return this != START && this != PAUSE;
		}
	}
}
