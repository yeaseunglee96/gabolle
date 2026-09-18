package com.gabolle.backend.itinerary.domain;

import java.time.Instant;
import java.util.List;

/**
 * 일정 하나의 진행 상태 — S15P21E201-1325 (시안 ⑤).
 *
 * <h2>🔴 도착 시각은 여기 없다</h2>
 * {@link ItineraryItemActual} 이 이미 그 자리다. 같은 사실을 두 곳에 담으면 둘이 어긋나는
 * 날이 오고, 그때 어느 쪽이 진짜인지 아무도 답할 수 없다. 이 클래스는 그 위에
 * <b>「달리고 있나」</b>와 <b>「몇 번째를 향하고 있나」</b>만 얹는다.
 *
 * <h2>🔴 상태 바꾸기는 전부 새 값을 돌려준다</h2>
 * 이 record 는 안 바뀐다. 「중지했는데 도착이 찍혔다」 같은 어긋남은 대개 <b>같은 객체를
 * 여기저기서 고쳐서</b> 생긴다 — 바꿀 수 없게 두면 그 자리가 아예 없다.
 *
 * @param currentStopIndex 지금 향하고 있는 정차지의 자리(0부터). 다 돌았으면 정차지 수와 같다
 */
public record ItineraryRun(
		String itineraryId,
		String tripId,
		Status status,
		int currentStopIndex,
		Instant startedAt,
		Instant updatedAt) {

	public enum Status {
		/** 아직 출발 안 했다. */
		PLANNED,
		/** 달리는 중 — 위치 추적과 자동 도착 기록이 이때만 돈다. */
		RUNNING,
		/** 멈춤. 다녀온 것은 그대로 남는다 — 중지는 되돌리기가 아니다. */
		PAUSED,
		/** 다 돌았다. */
		DONE
	}

	/** 아직 아무 일도 없었을 때. 표에 행이 없으면 이것으로 읽는다. */
	public static ItineraryRun planned(String itineraryId, String tripId, Instant now) {
		return new ItineraryRun(itineraryId, tripId, Status.PLANNED, 0, null, now);
	}

	/** 출발. 이미 달리고 있으면 아무 일도 안 한다 — 두 번 눌러도 처음 출발 시각을 지키기 위해. */
	public ItineraryRun start(Instant now) {
		if (this.status == Status.RUNNING) {
			return this;
		}
		return new ItineraryRun(this.itineraryId, this.tripId, Status.RUNNING, this.currentStopIndex,
				this.startedAt == null ? now : this.startedAt, now);
	}

	/**
	 * 중지.
	 *
	 * <p>🔴 달리는 중이 아닐 때는 아무 일도 안 한다. 특히 <b>다 돈 뒤에 중지로 되돌리지
	 * 않는다</b> — 끝난 일정이 다시 「멈춤」이 되면 화면에 「출발」이 살아난다.
	 */
	public ItineraryRun pause(Instant now) {
		if (this.status != Status.RUNNING) {
			return this;
		}
		return new ItineraryRun(this.itineraryId, this.tripId, Status.PAUSED, this.currentStopIndex, this.startedAt,
				now);
	}

	/**
	 * 한 정차지를 마쳤다(도착했거나 건너뛰었거나) — 다음으로 옮긴다.
	 *
	 * <p>🔴 <b>달리는 중일 때만</b> 움직인다. 멈춰 놓고 기록이 쌓이면 「멈췄다」가 거짓이 된다.
	 *
	 * <p>🔴 이미 마친 자리는 건너뛰며 앞으로 간다. 순서를 바꾸거나 늦게 올라온 신호 때문에
	 * 이미 다녀온 곳을 다시 가리키면, 화면이 「지금 가는 중」이라고 말하는 곳에 사용자는
	 * 이미 다녀와 있다.
	 *
	 * @param settled 이미 마친 정차지의 자리들
	 * @param stopCount 그 날의 정차지 수
	 */
	public ItineraryRun advance(List<Integer> settled, int stopCount, Instant now) {
		if (this.status != Status.RUNNING) {
			return this;
		}
		int next = this.currentStopIndex;
		while (next < stopCount && settled.contains(next)) {
			next += 1;
		}
		if (next >= stopCount) {
			return new ItineraryRun(this.itineraryId, this.tripId, Status.DONE, stopCount, this.startedAt, now);
		}
		return new ItineraryRun(this.itineraryId, this.tripId, Status.RUNNING, next, this.startedAt, now);
	}

	/** 위치를 올려도 되나 — 달리는 중일 때만이다(배터리·개인정보). */
	public boolean acceptsLocation() {
		return this.status == Status.RUNNING;
	}
}
