package com.gabolle.backend.itinerary.domain;

import java.time.Instant;
import java.util.List;

/**
 * 일정 하나의 진행 상태. 담는 것은 「달리고 있나」와 「몇 번째를 향하고 있나」뿐이고,
 * 도착 시각은 {@link ItineraryItemActual} 한 곳에만 둔다.
 *
 * <p>상태를 바꾸는 메서드는 전부 새 값을 돌려준다. 이 record 자체는 바뀌지 않는다.
 *
 * @param currentStopIndex 지금 향하고 있는 정차지의 자리(0부터). 다 돌았으면 정차지 수와 같다
 */
public record ItineraryRun(
		String itineraryId,
		String tripId,
		Status status,
		int currentStopIndex,
		Instant startedAt,
		Instant updatedAt,
		LastLocation lastLocation) {

	/**
	 * 마지막으로 받은 위치. 아직 못 받았으면 {@code null} 이다 — 「0,0 에 있다」가 아니다.
	 *
	 * <p>위도·경도를 따로 두지 않고 한 덩어리로 묶는다. 둘 중 하나만 있는 상태를 아예 만들 수
	 * 없게 하려는 것이고, 표의 CHECK({@code ck_itinerary_run_last_location})가 같은 말을 한다.
	 *
	 * @param at 기기가 그 점을 찍은 시각
	 */
	public record LastLocation(double lat, double lng, Instant at) {
	}

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
		return new ItineraryRun(itineraryId, tripId, Status.PLANNED, 0, null, now, null);
	}

	/** 출발. 이미 달리고 있으면 아무 일도 안 한다 — 두 번 눌러도 처음 출발 시각을 지키기 위해. */
	public ItineraryRun start(Instant now) {
		if (this.status == Status.RUNNING) {
			return this;
		}
		return new ItineraryRun(this.itineraryId, this.tripId, Status.RUNNING, this.currentStopIndex,
				this.startedAt == null ? now : this.startedAt, now, this.lastLocation);
	}

	/**
	 * 중지. 달리는 중이 아닐 때는 아무 일도 안 한다 — 특히 다 돈 뒤에 멈춤으로 되돌리지
	 * 않는다.
	 */
	public ItineraryRun pause(Instant now) {
		if (this.status != Status.RUNNING) {
			return this;
		}
		return new ItineraryRun(this.itineraryId, this.tripId, Status.PAUSED, this.currentStopIndex, this.startedAt,
				now, this.lastLocation);
	}

	/**
	 * 한 정차지를 마쳤을 때(도착했거나 건너뛰었거나) 다음으로 옮긴다. 달리는 중일 때만
	 * 움직이고, 이미 마친 자리는 건너뛰며 앞으로만 간다 — 뒤로 돌아가지 않는다.
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
			return new ItineraryRun(this.itineraryId, this.tripId, Status.DONE, stopCount, this.startedAt, now,
					this.lastLocation);
		}
		return new ItineraryRun(this.itineraryId, this.tripId, Status.RUNNING, next, this.startedAt, now,
				this.lastLocation);
	}

	/**
	 * 마지막 위치를 갈아 끼운다. 상태와 몇 번째는 안 건드린다 — 위치를 받은 것과 도착을
	 * 판정한 것은 다른 일이고, 판정은 {@code ItineraryRunService} 가 따로 한다.
	 */
	public ItineraryRun withLocation(double lat, double lng, Instant at, Instant now) {
		return new ItineraryRun(this.itineraryId, this.tripId, this.status, this.currentStopIndex, this.startedAt,
				now, new LastLocation(lat, lng, at));
	}

	/**
	 * 다 돌았다고 사람이 선언한다. 남은 정차지가 있어도 끝낸다 — 「안 간 곳이 남았으니 못
	 * 끝낸다」로 막으면 여행이 영영 안 끝나고, 안 간 곳은 {@link ItineraryItemActual} 에
	 * 기록이 없는 것으로 이미 구분된다.
	 *
	 * <p>아직 출발 안 했으면 아무 일도 안 한다 — 시작한 적 없는 여행을 끝낼 수는 없다.
	 */
	public ItineraryRun complete(Instant now) {
		if (this.status == Status.PLANNED || this.status == Status.DONE) {
			return this;
		}
		return new ItineraryRun(this.itineraryId, this.tripId, Status.DONE, this.currentStopIndex, this.startedAt,
				now, this.lastLocation);
	}

	/** 위치를 올려도 되나 — 달리는 중일 때만이다(배터리·개인정보). */
	public boolean acceptsLocation() {
		return this.status == Status.RUNNING;
	}
}
