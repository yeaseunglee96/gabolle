package com.gabolle.backend.itinerary.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.ItineraryItemActualRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRun;
import com.gabolle.backend.itinerary.domain.ItineraryRunPing;
import com.gabolle.backend.itinerary.domain.ItineraryRunRepository;
import com.gabolle.backend.itinerary.domain.ItineraryStopEvent;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.GeoDistance;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;

/**
 * 일정 진행 — 지금 어느 단계인가.
 *
 * <p>도착 시각은 여기서 새로 만들지 않는다. {@link ItineraryActualTimeService} 가 쓰는 표에
 * 적고, 이 서비스는 그 위에 「자동인가 손인가」와 「몇 번째를 향하고 있나」만 얹는다.
 *
 * <p>시각은 {@link Clock} 에서만 읽는다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(ItineraryAccess.class)
public class ItineraryRunService {

	private final ItineraryAccess access;

	private final ItineraryRepository itineraries;

	private final ItineraryRunRepository runs;

	private final ItineraryItemActualRepository actuals;

	/** 다음 정차지의 좌표를 찾을 때만 쓴다. 일정 항목에는 좌표가 없고 장소 식별자만 있다. */
	private final PlaceRepository places;

	private final Clock clock;

	/**
	 * 도착으로 보는 반경(m). 직선거리로 잰다 — 길찾기 업체를 부르지 않는다.
	 * <p>
	 * 🔴 실측이 아니라 정한 값이다. 도시의 GPS 오차가 보통 수십 미터라 그보다 넉넉해야 하고,
	 * 너무 넓히면 옆 건물을 지나가기만 해도 도착으로 찍힌다. 실제로 걸어 보고 고칠 값이다.
	 */
	static final double ARRIVAL_RADIUS_M = 100;

	public ItineraryRunService(ItineraryAccess access, ItineraryRepository itineraries, ItineraryRunRepository runs,
			ItineraryItemActualRepository actuals, PlaceRepository places, Clock clock) {
		this.access = access;
		this.itineraries = itineraries;
		this.runs = runs;
		this.actuals = actuals;
		this.places = places;
		this.clock = clock;
	}

	/**
	 * 기기가 올린 위치 한 점.
	 *
	 * @param recordedAt 기기가 찍은 시각. 서버가 받은 시각으로 대신하지 않는다 — 배치로 모아
	 *     올리면 옛 점이 전부 「방금」이 되어 궤적의 차례가 무너진다
	 */
	public record LocationPoint(double lat, double lng, Instant recordedAt) {
	}

	/** 진행 상태와 정차지마다 일어난 일. 화면이 한 번에 읽는다. */
	public record View(ItineraryRun run, List<Stop> stops) {
	}

	/**
	 * 정차지 하나의 지금. {@code arrivedAt} 과 {@code skipped} 는 한 칸으로 합치지 않는다 —
	 * 건너뛴 곳은 다녀온 곳이 아니다.
	 *
	 * @param arrivedHow {@code auto} 또는 {@code manual}. 안 갔으면 {@code null}
	 */
	public record Stop(String itemKey, int index, Instant arrivedAt, String arrivedHow, boolean skipped) {
	}

	@Transactional(readOnly = true)
	public View get(String itineraryId, String userId) {
		ItineraryAccess.Access granted = this.access.requireMember(itineraryId, userId);
		return view(itineraryId, granted.itinerary().tripId(), stopKeys(itineraryId));
	}

	/** 출발 — 위치 추적이 시작된다. */
	@Transactional
	public View start(String itineraryId, String userId) {
		return apply(itineraryId, userId, ItineraryStopEvent.Type.START, null, null);
	}

	/** 중지 — 추적과 자동 도착 기록을 멈춘다. 다녀온 것은 그대로 남는다. */
	@Transactional
	public View pause(String itineraryId, String userId) {
		return apply(itineraryId, userId, ItineraryStopEvent.Type.PAUSE, null, null);
	}

	/**
	 * 도착했다.
	 *
	 * @param how {@code auto} 면 GPS 가 알아챈 것, {@code manual} 이면 사람이 찍은 것.
	 *     둘을 가르는 것은 나중에 자동 판정이 얼마나 맞았는지를 재기 위해서다
	 */
	@Transactional
	public View arrive(String itineraryId, String userId, String itemKey, String how) {
		ItineraryStopEvent.Type type = "manual".equalsIgnoreCase(how)
				? ItineraryStopEvent.Type.ARRIVE_MANUAL
				: ItineraryStopEvent.Type.ARRIVE_AUTO;
		return apply(itineraryId, userId, type, itemKey, Instant.now(this.clock));
	}

	/** 건너뛴다 — 이 정차지를 빼고 다음으로. */
	@Transactional
	public View skip(String itineraryId, String userId, String itemKey) {
		return apply(itineraryId, userId, ItineraryStopEvent.Type.SKIP, itemKey, null);
	}

	/**
	 * 기기가 모아 보낸 위치를 받는다. 궤적을 쌓고, 마지막 위치를 갈아 끼우고, 다음 정차지
	 * 반경 안에 들었으면 도착을 찍는다.
	 *
	 * <p><b>왜 배치인가.</b> 초당 한 번씩 올리면 걷는 두 시간에 7,200번이 온다. 기기가
	 * 10~30초에 한 점씩 모아 한 번에 보낸다 — 그래서 {@code recordedAt} 이 필요하고, 서버가
	 * 받은 시각으로 대신할 수 없다.
	 *
	 * <p>🔴 <b>달리는 중일 때만 받는다</b>({@link ItineraryRun#acceptsLocation()}). 멈춘
	 * 여행의 위치를 받아 두면 사용자가 「멈췄다」고 믿는 동안 기록이 쌓인다. 배터리 문제가
	 * 아니라 약속의 문제다.
	 *
	 * <p>판정은 <b>배치에서 가장 최근 점</b>으로만 한다. 중간 점들로 지난 도착을 소급해 찍지
	 * 않는다 — 한 배치에 여러 정차지를 한꺼번에 통과시키면 순서가 실제와 달라질 수 있고,
	 * 그 판단은 궤적을 제대로 분석할 때 하는 일이다. 중간 점들은 궤적에 그대로 남는다.
	 */
	@Transactional
	public View recordLocations(String itineraryId, String userId, List<LocationPoint> points) {
		ItineraryAccess.Access granted = this.access.requireEditor(itineraryId, userId);
		String tripId = granted.itinerary().tripId();
		List<String> keys = stopKeys(itineraryId);
		Instant now = Instant.now(this.clock);

		ItineraryRun run = this.runs.find(itineraryId)
				.orElseGet(() -> ItineraryRun.planned(itineraryId, tripId, now));
		if (!run.acceptsLocation()) {
			throw new NotRunningException(itineraryId, run.status());
		}
		if (points == null || points.isEmpty()) {
			// 빈 배치는 오류가 아니다. 망이 끊겼다 돌아온 기기가 빈 묶음을 한 번 보낸다.
			return view(itineraryId, tripId, keys);
		}

		List<ItineraryRunPing> pings = new ArrayList<>(points.size());
		for (LocationPoint point : points) {
			pings.add(new ItineraryRunPing(UUID.randomUUID().toString(), itineraryId, point.lat(), point.lng(),
					point.recordedAt(), now));
		}
		this.runs.saveAllPings(pings);

		LocationPoint latest = points.stream()
				.max(Comparator.comparing(LocationPoint::recordedAt))
				.orElseThrow();
		ItineraryRun located = this.runs.upsert(run.withLocation(latest.lat(), latest.lng(), latest.recordedAt(), now));

		String reached = stopReachedBy(itineraryId, keys, run, latest);
		if (reached == null) {
			// 아직 안 닿았거나, 좌표를 몰라 판정을 보류했다. 지어내지 않는다.
			return viewOf(located, itineraryId, keys);
		}
		return apply(itineraryId, userId, ItineraryStopEvent.Type.ARRIVE_AUTO, reached, latest.recordedAt());
	}

	/**
	 * 지금 향하고 있는 정차지에 닿았나. 닿았으면 그 정차지의 열쇠, 아니면 {@code null}.
	 *
	 * <p>좌표를 모르는 장소는 <b>판정하지 않는다.</b> 모름을 「멀다」로 치면 그 장소는 영영
	 * 자동으로 도착이 안 찍히고, 「가깝다」로 치면 근처에 가지도 않았는데 지나간 것이 된다.
	 * 둘 다 틀리므로 사람이 손으로 찍을 때까지 기다린다.
	 */
	private String stopReachedBy(String itineraryId, List<String> keys, ItineraryRun run, LocationPoint at) {
		Map<String, Stop> stops = stopsByKey(itineraryId, keys);
		String nextKey = null;
		for (int i = run.currentStopIndex(); i < keys.size(); i++) {
			Stop stop = stops.get(keys.get(i));
			if (stop != null && (stop.arrivedAt() != null || stop.skipped())) {
				continue;
			}
			nextKey = keys.get(i);
			break;
		}
		if (nextKey == null) {
			return null;
		}

		Optional<Place> place = placeOfStop(itineraryId, nextKey);
		if (place.isEmpty() || !place.get().hasCoordinates()) {
			return null;
		}
		double meters = GeoDistance.meters(at.lat(), at.lng(), place.get().getLat(), place.get().getLng());
		return (meters <= ARRIVAL_RADIUS_M) ? nextKey : null;
	}

	/** 그 정차지가 가리키는 장소. 일정 항목에는 장소 식별자만 있고 좌표는 장소 쪽에 있다. */
	private Optional<Place> placeOfStop(String itineraryId, String itemKey) {
		var itinerary = this.itineraries.findById(itineraryId)
				.orElseThrow(() -> new ItineraryQueryController.ItineraryNotFoundException(itineraryId));
		ItineraryContent content = this.itineraries.findContent(itineraryId, itinerary.latestVersion())
				.orElseThrow(() -> new ItineraryQueryController.ItineraryNotFoundException(itineraryId));
		for (ItineraryItem item : content.items()) {
			if (item.itemKey().equals(itemKey) && item.placeId() != null) {
				return this.places.findByPlaceIdIn(List.of(UUID.fromString(item.placeId()))).stream().findFirst();
			}
		}
		return Optional.empty();
	}

	/**
	 * 다 돌았다고 사람이 선언한다. 남은 정차지가 있어도 끝낸다 — 안 간 곳은 도착 기록이
	 * 없는 것으로 이미 구분되고, 「안 간 곳이 남았으니 못 끝낸다」로 막으면 여행이 영영 안 끝난다.
	 */
	@Transactional
	public View complete(String itineraryId, String userId) {
		ItineraryAccess.Access granted = this.access.requireEditor(itineraryId, userId);
		String tripId = granted.itinerary().tripId();
		List<String> keys = stopKeys(itineraryId);
		Instant now = Instant.now(this.clock);

		ItineraryRun run = this.runs.find(itineraryId)
				.orElseGet(() -> ItineraryRun.planned(itineraryId, tripId, now));
		if (run.status() == ItineraryRun.Status.PLANNED) {
			throw new NotRunningException(itineraryId, run.status());
		}
		return viewOf(this.runs.upsert(run.complete(now)), itineraryId, keys);
	}

	private View apply(String itineraryId, String userId, ItineraryStopEvent.Type type, String itemKey,
			Instant arrivedAt) {
		// 편집 권한을 요구한다. 보기 전용으로 초대된 사람은 진행 기록을 남길 수 없다.
		ItineraryAccess.Access granted = this.access.requireEditor(itineraryId, userId);
		String tripId = granted.itinerary().tripId();
		List<String> keys = stopKeys(itineraryId);
		Instant now = Instant.now(this.clock);

		if (type.needsStop() && !keys.contains(itemKey)) {
			throw new UnknownStopException(itineraryId, itemKey);
		}

		ItineraryRun run = this.runs.find(itineraryId)
				.orElseGet(() -> ItineraryRun.planned(itineraryId, tripId, now));

		// 달리는 중이 아니면 정차지 사건을 안 받는다.
		if (type.settlesStop() && run.status() != ItineraryRun.Status.RUNNING) {
			throw new NotRunningException(itineraryId, run.status());
		}

		ItineraryRun next = switch (type) {
			case START -> run.start(now);
			case PAUSE -> run.pause(now);
			default -> run;
		};

		if (type.settlesStop()) {
			// 같은 정차지를 두 번 찍어도 한 번만 센다. 재시도와 늦게 온 신호가 정상 경로라,
			// 막지 않으면 「몇 번째」가 실제보다 앞서 간다.
			Map<String, Stop> before = stopsByKey(itineraryId, keys);
			Stop already = before.get(itemKey);
			if (already != null && (already.arrivedAt() != null || already.skipped())) {
				// 아무것도 안 바꿨다. 방금 읽은 값이 곧 지금 값이다.
				return viewOf(run, itineraryId, keys);
			}
			record(itineraryId, itemKey, type, now, arrivedAt, userId);
			next = next.advance(settledIndexes(itineraryId, keys), keys.size(), now);
		}
		else {
			appendEvent(itineraryId, null, type, now, userId);
		}

		// 🔴 저장한 뒤에 다시 읽지 않는다. 덮어쓰기가 원시 SQL 이라 같은 트랜잭션의 영속성
		// 컨텍스트에 이미 올라온 엔티티가 안 바뀌고, 다시 읽으면 바꾸기 전 값이 나온다 —
		// 「도착을 눌렀는데 아직 그 정차지를 향하는 중」이 된다. 실제 PostgreSQL 로 확인했다
		// (ItineraryRunProgressIntegrationTest). 우리가 방금 만든 값이 가장 정확하다.
		return viewOf(this.runs.upsert(next), itineraryId, keys);
	}

	private void record(String itineraryId, String itemKey, ItineraryStopEvent.Type type, Instant now,
			Instant arrivedAt, String userId) {
		appendEvent(itineraryId, itemKey, type, now, userId);
		if (!type.isArrival()) {
			return;
		}
		// 도착 시각은 따로 들지 않고 이미 있는 표에 적는다.
		this.actuals.upsert(new ItineraryItemActual(itineraryId, itemKey, arrivedAt == null ? now : arrivedAt, null,
				userId, now));
	}

	private void appendEvent(String itineraryId, String itemKey, ItineraryStopEvent.Type type, Instant now,
			String userId) {
		this.runs.append(new ItineraryStopEvent(UUID.randomUUID().toString(), itineraryId, itemKey, type, now, userId,
				now));
	}

	/**
	 * 그 일정의 정차지를 순서대로. 최신 판을 읽는다 — 진행 상태는 판 체인 밖에 있어서
	 * 일정을 고친 뒤에도 「몇 번째」는 새 판의 순서로 읽혀야 한다.
	 */
	private List<String> stopKeys(String itineraryId) {
		var itinerary = this.itineraries.findById(itineraryId)
				.orElseThrow(() -> new ItineraryQueryController.ItineraryNotFoundException(itineraryId));
		ItineraryContent content = this.itineraries.findContent(itineraryId, itinerary.latestVersion())
				.orElseThrow(() -> new ItineraryQueryController.ItineraryNotFoundException(itineraryId));
		List<ItineraryItem> items = new ArrayList<>(content.items());
		items.sort(Comparator.comparingInt(ItineraryItem::dayIndex).thenComparingInt(ItineraryItem::sequence));
		return items.stream().map(ItineraryItem::itemKey).toList();
	}

	private Map<String, Stop> stopsByKey(String itineraryId, List<String> keys) {
		Map<String, Instant> arrived = new LinkedHashMap<>();
		for (ItineraryItemActual actual : this.actuals.findByItineraryId(itineraryId)) {
			if (actual.arrivedAt() != null) {
				arrived.put(actual.itemKey(), actual.arrivedAt());
			}
		}
		Map<String, String> how = new LinkedHashMap<>();
		Map<String, Boolean> skipped = new LinkedHashMap<>();
		for (ItineraryStopEvent event : this.runs.findEvents(itineraryId)) {
			if (event.itemKey() == null) {
				continue;
			}
			if (event.type() == ItineraryStopEvent.Type.SKIP) {
				skipped.put(event.itemKey(), true);
			}
			else if (event.type().isArrival()) {
				how.put(event.itemKey(), event.type() == ItineraryStopEvent.Type.ARRIVE_MANUAL ? "manual" : "auto");
			}
		}
		Map<String, Stop> stops = new LinkedHashMap<>();
		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			stops.put(key, new Stop(key, i, arrived.get(key), how.get(key), skipped.getOrDefault(key, false)));
		}
		return stops;
	}

	private List<Integer> settledIndexes(String itineraryId, List<String> keys) {
		List<Integer> settled = new ArrayList<>();
		for (Stop stop : stopsByKey(itineraryId, keys).values()) {
			if (stop.arrivedAt() != null || stop.skipped()) {
				settled.add(stop.index());
			}
		}
		return settled;
	}

	private View view(String itineraryId, String tripId, List<String> keys) {
		ItineraryRun run = this.runs.find(itineraryId)
				.orElseGet(() -> ItineraryRun.planned(itineraryId, tripId, Instant.now(this.clock)));
		return new View(run, List.copyOf(stopsByKey(itineraryId, keys).values()));
	}

	/**
	 * 방금 저장한 값을 그대로 실어 보낸다.
	 *
	 * <p>저장 직후에 다시 읽지 않는 이유는, 덮어쓰기가 원시 SQL 이라 같은 트랜잭션 안의
	 * 영속성 컨텍스트에 이미 들어 있는 엔티티가 <b>안 바뀌기</b> 때문이다. 다시 읽으면 방금
	 * 바꾼 값이 아니라 바꾸기 전 값이 나올 수 있고, 그러면 화면은 「완료를 눌렀는데 아직
	 * 달리는 중」을 본다. 우리가 방금 만든 값이 가장 정확하다.
	 */
	private View viewOf(ItineraryRun run, String itineraryId, List<String> keys) {
		return new View(run, List.copyOf(stopsByKey(itineraryId, keys).values()));
	}

	/** 그 일정에 없는 정차지를 가리켰다 — 400. */
	public static class UnknownStopException extends RuntimeException {

		public UnknownStopException(String itineraryId, String itemKey) {
			super("이 일정에 없는 정차지예요: " + itemKey + " (" + itineraryId + ")");
		}
	}

	/** 달리고 있지 않은데 도착·건너뛰기를 보냈다 — 409. */
	public static class NotRunningException extends RuntimeException {

		private final ItineraryRun.Status status;

		public NotRunningException(String itineraryId, ItineraryRun.Status status) {
			super("출발한 뒤에 기록할 수 있어요. 지금은 " + status + " 예요. (" + itineraryId + ")");
			this.status = status;
		}

		public ItineraryRun.Status status() {
			return this.status;
		}
	}
}
