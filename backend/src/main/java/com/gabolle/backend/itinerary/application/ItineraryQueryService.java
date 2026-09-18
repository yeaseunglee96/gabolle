package com.gabolle.backend.itinerary.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.ItineraryItemActualRepository;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryVersionSummaryResponse;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryVersionsResponse;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.recommendation.application.RecommendationCodes;
import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;

/**
 * 완성된 일정표 조회 — S15P21E201-604. {@link ItineraryQueryController} 가 부른다.
 *
 * <p>🔴 {@code @Profile({"db","dev"})} · {@code @ConditionalOnBean(TripQueryService.class)}.
 * {@code ItineraryDraftService} 가 같은 이유로 같은 조합을 쓴다 — 이 서비스도 {@link PlaceRepository}
 * ·{@link RecommendationJobRepository} 를 실제로 물어야 해서 {@code no-db} 프로필에는 대응하는
 * 구현이 없다. {@code @ConditionalOnBean} 은 그와 별개로 {@code ItinerarySliceApplication}
 * (일정 도메인만 스캔하는 테스트 전용 컨텍스트)이 {@code trip} 패키지를 스캔하지 않아
 * {@link TripQueryService} 빈이 없는 문제를 막는다 — {@code RecommendationJobRunner} 의
 * javadoc 이 같은 처리를 설명한다.
 */
@Service
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ItineraryQueryService {

	private final ItineraryRepository itineraryRepository;

	private final ItineraryAccess itineraryAccess;

	private final PlaceRepository placeRepository;

	private final RecommendationJobRepository recommendationJobRepository;

	private final ActorNames actorNames;

	/** S15P21E201-293 — 방문지의 실제 도착·출발 시각. 판이 아니라 일정에 매달려 있다. */
	private final ItineraryItemActualRepository actualRepository;

	public ItineraryQueryService(ItineraryRepository itineraryRepository, ItineraryAccess itineraryAccess,
			PlaceRepository placeRepository, RecommendationJobRepository recommendationJobRepository,
			ActorNames actorNames, ItineraryItemActualRepository actualRepository) {
		this.itineraryRepository = itineraryRepository;
		this.itineraryAccess = itineraryAccess;
		this.placeRepository = placeRepository;
		this.recommendationJobRepository = recommendationJobRepository;
		this.actorNames = actorNames;
		this.actualRepository = actualRepository;
	}

	/**
	 * @throws ItineraryQueryController.ItineraryNotFoundException 일정이 없거나, 있어도
	 *     요청자가 그 일정이 속한 여행의 회원이 아니다 — 둘 다 404 (FR-SEC-01 과 같은 논리)
	 * @throws IllegalStateException {@code itineraries.latest_version} 이 가리키는 판의
	 *     내용이 없다. 🔴 이때 {@code MAX(version)} 으로 조용히 대체하지 않는다 —
	 *     V20260903150000 이 경고한 어긋남을 감추는 것이기 때문이다. 500 으로 시끄럽게 실패한다
	 */
	@Transactional(readOnly = true)
	public ItineraryDetailResponse getDetail(String itineraryId, String requesterUserId) {
		// 🔴 S15P21E201-224 — 예전에는 여기서 itineraryRepository.findById 와
		// tripQueryService.get 을 각각 부르고 TripNotFoundException 을 손으로 잡아 404 로
		// 바꿨다. 그 판정을 ItineraryAccess 하나로 모았다 — 편집 경로(ItineraryEditController)
		// 도 같은 판정을 쓰는데 두 곳에 복사해 두면 언젠가 한 곳을 빠뜨린다.
		ItineraryAccess.Access access = this.itineraryAccess.requireMember(itineraryId, requesterUserId);
		Itinerary itinerary = access.itinerary();
		Trip trip = access.trip();

		int latestVersion = itinerary.latestVersion();
		ItineraryContent content = this.itineraryRepository.findContent(itineraryId, latestVersion)
				.orElseThrow(() -> new IllegalStateException(
						"일정의 최신 판(version=" + latestVersion + ") 내용이 없다: itineraryId=" + itineraryId));

		Map<UUID, Place> placesByPlaceId = lookupPlaces(content.items());

		Map<LegKey, ItineraryLeg> legsByKey = content.legs().stream()
				.collect(Collectors.toMap(leg -> new LegKey(leg.dayIndex(), leg.sequence()), leg -> leg));

		Map<Integer, List<ItineraryItem>> itemsByDay = content.items().stream()
				.collect(Collectors.groupingBy(ItineraryItem::dayIndex));

		// S15P21E201-293 — 실제 시각은 판이 아니라 일정에 매달려 있으므로 판 번호와 무관하게
		// 한 번에 읽는다. 편집으로 판이 바뀌어도 같은 item_key 의 기록이 그대로 붙는다.
		Map<String, ItineraryItemActual> actualsByItemKey = this.actualRepository.findByItineraryId(itineraryId)
				.stream()
				.collect(Collectors.toMap(ItineraryItemActual::itemKey, actual -> actual));

		List<ItineraryDetailResponse.Day> days = buildDays(trip, itemsByDay, legsByKey, placesByPlaceId,
				actualsByItemKey);

		Integer totalEstimatedCostKrw = sumOrNull(content.items().stream()
				.map(ItineraryItem::estimatedCostKrw));
		Integer totalWalkingMeters = sumOrNull(content.legs().stream()
				.map(ItineraryLeg::walkingMeters));

		FallbackMode fallbackMode = resolveFallbackMode(content.version());

		return new ItineraryDetailResponse(
				itineraryId,
				buildTitle(trip),
				latestVersion,
				days,
				totalEstimatedCostKrw,
				totalWalkingMeters,
				fallbackMode,
				access.role().name(),
				access.role().canEdit(),
				content.version().warningCodes(),
				// S15P21E201-1113 — 이미 손에 있는 값이다. 여행을 다시 조회하지 않는다.
				trip.tripId(),
				// S15P21E201-1158 — 이미 읽어 둔 항목에서 센다. DB 를 다시 묻지 않는다.
				accessibilityUnverifiedCount(content.items()));
	}

	/**
	 * 휠체어 접근을 <b>안 재 본</b> 항목이 몇 곳인가 — S15P21E201-1158.
	 *
	 * <p>🔴 항목 수를 센다. 경고 <b>건수</b>가 아니다. 한 항목에 같은 경고가 두 번 붙는 일은
	 * 지금 없지만, 화면이 사용자에게 말하는 것은 언제나 <b>"몇 곳"</b> 이라 세는 단위를 곳으로
	 * 못박는다.
	 *
	 * <p>문자열을 여기서 다시 적지 않고 {@link RecommendationCodes} 를 본다 — 값을 만드는 곳이
	 * 다른 갈래에 있어서, 두 벌이 되면 한쪽만 고쳐지는 날 <b>이 셈이 조용히 0 이 된다.</b>
	 */
	private int accessibilityUnverifiedCount(List<ItineraryItem> items) {
		return (int) items.stream()
				.filter(item -> item.warningCodes().contains(RecommendationCodes.WARNING_ACCESSIBILITY_UNVERIFIED))
				.count();
	}

	/**
	 * 🔴 S15P21E201-284 — 판 목록, API 명세 ITN-02. 되돌리기 화면이 "어느 판으로 돌아갈지"
	 * 고르는 목록이다. 권한 판정은 {@link #getDetail} 과 <b>똑같이</b>
	 * {@link ItineraryAccess#requireMember} — 회원이면 누구나(VIEWER 도) 볼 수 있고,
	 * 회원이 아니면 일정이 없는 것처럼 404 다.
	 *
	 * @throws ItineraryQueryController.ItineraryNotFoundException 일정이 없거나, 있어도
	 *     요청자가 그 일정이 속한 여행의 회원이 아니다
	 */
	@Transactional(readOnly = true)
	public ItineraryVersionsResponse listVersions(String itineraryId, String requesterUserId, Integer page,
			Integer size) {
		this.itineraryAccess.requireMember(itineraryId, requesterUserId);

		// 🔴 기본값을 여기서만 정한다 — 컨트롤러도 알고 있으면 둘이 어긋나는 날이 오고,
		//    그러면 안 주고 부른 첫 쪽과 page=0 으로 부른 쪽의 크기가 달라져 판이 겹치거나
		//    건너뛰어진다.
		int pageNumber = (page == null) ? 0 : Math.max(page, 0);
		int pageSize = (size == null) ? DEFAULT_VERSION_PAGE_SIZE
				: Math.min(Math.max(size, 1), MAX_VERSION_PAGE_SIZE);

		ItineraryRepository.VersionPage found = this.itineraryRepository.findVersions(itineraryId, pageNumber,
				pageSize);
		List<ItineraryVersion> versions = found.versions();

		// 2026-09-07 — 판마다 만든 사람의 표시 이름을 싣는다. 이름 조회는 한 번(IN 질의)이다.
		Map<String, String> names = this.actorNames.resolve(versions.stream().map(ItineraryVersion::createdBy).toList());
		List<ItineraryVersionSummaryResponse> items = versions.stream()
				.map(v -> ItineraryVersionSummaryResponse.of(v, names.get(v.createdBy())))
				.toList();

		return new ItineraryVersionsResponse(items, items.size(), found.hasMore());
	}

	/**
	 * 판 목록의 기본 쪽 크기 (S15P21E201-1011). 되돌리기 화면이 한 번에 보여 주는 것보다
	 * 넉넉하다 — 지금까지처럼 한 번만 부르는 화면은 보이는 동작이 사실상 달라지지 않는다.
	 */
	private static final int DEFAULT_VERSION_PAGE_SIZE = 50;

	/** 부르는 쪽이 아무리 크게 달라고 해도 여기까지. 상한이 없으면 파라미터 하나로 상한이 풀린다. */
	private static final int MAX_VERSION_PAGE_SIZE = 200;

	/**
	 * 여행 기간의 날짜를 전부 만든다. 🔴 항목이 0개인 날도 포함한다 — {@code itemsByDay} 에
	 * 없는 {@code dayIndex} 는 빈 {@code items} 로 채운다.
	 */
	private List<ItineraryDetailResponse.Day> buildDays(Trip trip, Map<Integer, List<ItineraryItem>> itemsByDay,
			Map<LegKey, ItineraryLeg> legsByKey, Map<UUID, Place> placesByPlaceId,
			Map<String, ItineraryItemActual> actualsByItemKey) {

		List<ItineraryDetailResponse.Day> days = new ArrayList<>(trip.days());
		LocalDate date = trip.startDate();
		for (int dayIndex = 0; dayIndex < trip.days(); dayIndex++) {
			List<ItineraryItem> itemsOfDay = itemsByDay.getOrDefault(dayIndex, List.of()).stream()
					.sorted((a, b) -> Integer.compare(a.sequence(), b.sequence()))
					.toList();

			List<ItineraryDetailResponse.Item> items = new ArrayList<>(itemsOfDay.size());
			for (ItineraryItem item : itemsOfDay) {
				items.add(toItemDto(item, legsByKey, placesByPlaceId, actualsByItemKey.get(item.itemKey())));
			}

			days.add(new ItineraryDetailResponse.Day(date.toString(), items));
			date = date.plusDays(1);
		}
		return days;
	}

	/**
	 * @param actual 그 방문지에 남은 실제 시각. 아직 안 갔거나 안 적었으면 {@code null} 이고,
	 *     그때도 응답의 두 칸은 <b>키가 있고 값이 {@code null}</b> 이다 — 없는 칸과 빈 칸은
	 *     화면에 다른 뜻이다({@link ItineraryDetailResponse.Item} 주석 참고)
	 */
	private ItineraryDetailResponse.Item toItemDto(ItineraryItem item, Map<LegKey, ItineraryLeg> legsByKey,
			Map<UUID, Place> placesByPlaceId, ItineraryItemActual actual) {

		Place place = placesByPlaceId.get(UUID.fromString(item.placeId()));
		if (place == null) {
			// FK 가 있는 한 있을 수 없는 상태다 — 조용히 넘기면 화면에 빈 제목이 뜬다.
			throw new IllegalStateException("일정 항목이 가리키는 place 를 찾을 수 없다: placeId=" + item.placeId());
		}

		ItineraryLeg incomingLeg = legsByKey.get(new LegKey(item.dayIndex(), item.sequence()));
		// 🔴 구간이 이 항목으로 들어오는 것이 맞는지 확인한다. 순서가 바뀐 판에서는 (날짜,순번)
		//    이 같아도 도착지가 다를 수 있고, 그때 남의 거리를 이 항목에 붙이면 안 된다.
		boolean incoming = incomingLeg != null && incomingLeg.toPlaceId().equals(item.placeId());
		Integer walkingMeters = incoming ? incomingLeg.walkingMeters() : null;
		Integer travelDurationMin = incoming ? incomingLeg.durationMin() : null;
		String travelDataStatus = (incoming && incomingLeg.dataStatus() != null)
				? incomingLeg.dataStatus().name()
				: null;
		// 🔴 S15P21E201-1109 — 요금도 같은 incoming 하나에 묶인다. 구간이 이 항목으로 들어오는
		//    것이 아니면 남의 요금이므로 비운다. 모르는 것을 0 으로 채우지 않는다.
		Integer travelFareKrw = incoming ? incomingLeg.fareKrw() : null;

		return new ItineraryDetailResponse.Item(
				item.itemKey(),
				startsAt(item),
				place.getNameKo(),
				null, // description — place 표에 설명 칸이 없다
				item.estimatedCostKrw(),
				walkingMeters,
				item.locked(),
				item.dataStatus().name(),
				actual == null ? null : seoulIso(actual.arrivedAt()),
				actual == null ? null : seoulIso(actual.departedAt()),
				// S15P21E201-744 — item.placeId() 를 그대로 쓴다. place 에서 다시 꺼내도 같은
				// 값이지만, 항목이 가리키는 값을 그대로 돌려주는 쪽이 의도가 분명하다.
				item.placeId(),
				travelDurationMin,
				travelDataStatus,
				travelFareKrw,
				// S15P21E201-1158 — 저장돼 있던 값을 그대로 공개한다. ItineraryItem 이 생성자에서
				// 이미 빈 목록으로 정규화하므로(null 이 안 나온다) 여기서 다시 감싸지 않는다.
				item.warningCodes());
	}

	/** {@code visit_date} + {@code start_time} 을 ISO-8601 로 합친다. 시간대는 항상 Asia/Seoul 이다(API-03). */
	private String startsAt(ItineraryItem item) {
		if (item.startTime() == null) {
			return null;
		}
		ZonedDateTime zoned = ZonedDateTime.of(item.visitDate(), item.startTime(), ZoneId.of("Asia/Seoul"));
		return zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	/**
	 * 실제 시각을 계획 시각({@link #startsAt})과 <b>같은 형식</b>으로 적는다 — 화면이 두 값을
	 * 같은 파서로 읽고 나란히 보여준다(API-03 이 정한 Asia/Seoul).
	 */
	private static String seoulIso(Instant instant) {
		if (instant == null) {
			return null;
		}
		return instant.atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	/**
	 * 🔴 {@code itineraries} 표에 제목 칸이 없다. 여행 기간으로 지어낸 값이다 — 지어냈다는
	 * 사실을 여기 남긴다. 나중에 사용자가 직접 붙인 제목 칸이 생기면 이 자리를 그것으로
	 * 바꿔야 한다.
	 */
	private String buildTitle(Trip trip) {
		return trip.startDate() + " ~ " + trip.finishDate() + " 여행 일정";
	}

	/**
	 * 이 판을 만든 추천 요청의 fallback_mode. {@code source_request_id} 가 없으면(사용자가
	 * 손으로 만든 판) {@code null} 이다.
	 */
	private FallbackMode resolveFallbackMode(ItineraryVersion version) {
		String sourceRequestId = version.sourceRequestId();
		if (sourceRequestId == null) {
			return null;
		}
		return this.recommendationJobRepository.findByRequestId(UUID.fromString(sourceRequestId))
				.map(RecommendationJob::getFallbackMode)
				.orElse(null);
	}

	private Map<UUID, Place> lookupPlaces(List<ItineraryItem> items) {
		if (items.isEmpty()) {
			return Map.of();
		}
		List<UUID> placeIds = items.stream()
				.map(item -> UUID.fromString(item.placeId()))
				.distinct()
				.toList();
		Map<UUID, Place> result = new HashMap<>();
		for (Place place : this.placeRepository.findByPlaceIdIn(placeIds)) {
			result.put(place.getPlaceId(), place);
		}
		return result;
	}

	/** 하나도 값이 없으면 {@code 0} 이 아니라 {@code null} — "안 걸었다"·"공짜"와 "안 쟀다"는 다르다. */
	private static Integer sumOrNull(Stream<Integer> values) {
		int[] sum = { 0 };
		boolean[] hasAny = { false };
		values.forEach(value -> {
			if (value != null) {
				sum[0] += value;
				hasAny[0] = true;
			}
		});
		return hasAny[0] ? sum[0] : null;
	}

	private record LegKey(int dayIndex, int sequence) {
	}
}
