package com.gabolle.backend.itinerary.domain;

import java.time.Instant;

/**
 * 일정 진행 중 일어난 일. 덮어쓰지 않고 쌓는다 — 상태 한 줄만 두면 건너뛴 곳과 아직 안 간
 * 곳을 가를 수 없다.
 *
 * <p>정차지는 항목 행 id 가 아니라 {@code itemKey} 로 가리킨다. 일정을 고칠 때마다 항목이
 * 통째로 복사되므로({@link ItineraryRevision}) 행 id 로는 판이 바뀌는 순간 기록이 어느
 * 정차지의 것인지 알 수 없게 된다.
 *
 * @param itemKey {@link Type#START}·{@link Type#PAUSE} 는 정차지가 없는 사건이라 {@code null}
 * @param occurredAt 그 일이 일어난 시각. 신호가 늦게 올라올 수 있어 기록한 시각과 다르다
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
