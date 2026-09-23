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
import com.gabolle.backend.place.service.PlaceMenuPricePort;
import com.gabolle.backend.recommendation.application.RecommendationCodes;
import com.gabolle.backend.recommendation.domain.FallbackMode;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;

/**
 * 완성된 일정표 조회. {@link ItineraryQueryController} 가 부른다.
 *
 * <p>{@code @Profile} 은 {@link PlaceRepository}·{@link RecommendationJobRepository} 를 실제로
 * 물어야 해서 필요하고, {@code @ConditionalOnBean} 은 일정 도메인만 스캔하는 테스트 컨텍스트
 * ({@code ItinerarySliceApplication})에 {@link TripQueryService} 빈이 없어서 필요하다.
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

	/** 방문지의 실제 도착·출발 시각. 판이 아니라 일정에 매달려 있다. */
	private final ItineraryItemActualRepository actualRepository;

	private final PlaceMenuPricePort menuPrice;

	public ItineraryQueryService(ItineraryRepository itineraryRepository, ItineraryAccess itineraryAccess,
			PlaceRepository placeRepository, RecommendationJobRepository recommendationJobRepository,
			ActorNames actorNames, ItineraryItemActualRepository actualRepository,
			PlaceMenuPricePort menuPrice) {
		this.itineraryRepository = itineraryRepository;
		this.itineraryAccess = itineraryAccess;
		this.placeRepository = placeRepository;
		this.recommendationJobRepository = recommendationJobRepository;
		this.actorNames = actorNames;
		this.actualRepository = actualRepository;
		this.menuPrice = menuPrice;
	}

	/**
	 * @throws ItineraryQueryController.ItineraryNotFoundException 일정이 없거나, 있어도
	 *     요청자가 그 일정이 속한 여행의 회원이 아니다 — 둘 다 404 (FR-SEC-01 과 같은 논리)
	 * @throws IllegalStateException {@code itineraries.latest_version} 이 가리키는 판의
	 *     내용이 없다. {@code MAX(version)} 으로 대체하지 않고 500 으로 실패한다 — 대체하면
	 *     데이터가 어긋났다는 사실이 묻힌다
	 */
	@Transactional(readOnly = true)
	public ItineraryDetailResponse getDetail(String itineraryId, String requesterUserId) {
		// 권한 판정은 ItineraryAccess 한 곳에 모은다. 편집 경로도 같은 판정을 쓴다.
		ItineraryAccess.Access access = this.itineraryAccess.requireMember(itineraryId, requesterUserId);
		Itinerary itinerary = access.itinerary();
		Trip trip = access.trip();

		int latestVersion = itinerary.latestVersion();
		ItineraryContent content = this.itineraryRepository.findContent(itineraryId, latestVersion)
				.orElseThrow(() -> new IllegalStateException(
						"일정의 최신 판(version=" + latestVersion + ") 내용이 없다: itineraryId=" + itineraryId));

		// 실제 시각은 판이 아니라 일정에 매달려 있으므로 판 번호와 무관하게 한 번에 읽는다.
		// 편집으로 판이 바뀌어도 같은 item_key 의 기록이 그대로 붙는다.
		Map<String, ItineraryItemActual> actualsByItemKey = this.actualRepository.findByItineraryId(itineraryId)
				.stream()
				.collect(Collectors.toMap(ItineraryItemActual::itemKey, actual -> actual));

		return render(itineraryId, latestVersion, trip, content.items(), content.legs(), actualsByItemKey,
				resolveFallbackMode(content.version()), access.role().name(), access.role().canEdit(),
				content.version().warningCodes());
	}

	/**
	 * 아직 저장하지 않은 일정(추천 코스 2안·3안)을 저장된 일정과 <b>같은 모양</b>으로 그린다
	 * (S15P21E201-1454). 화면은 코스를 이 모양으로만 그리므로, 모양이 다르면 미리보기에서만 비용·이동
	 * 시간이 비는 식으로 어긋난다.
	 *
	 * <p>권한은 부르는 쪽({@link TripCourseService})이 여행 회원인지를 이미 본 뒤다. {@code canEdit}
	 * 은 언제나 거짓이다 — 아직 일정이 아니라서 고칠 판이 없다. 판 번호는 {@code 0} 이다.
	 *
	 * @param previewId 화면이 이 미리보기를 가리킬 이름. 일정 번호가 아니다 — 코스 번호다
	 */
	@Transactional(readOnly = true)
	public ItineraryDetailResponse preview(String previewId, Trip trip, TripMember.Role role,
			List<ItineraryItem> items, List<ItineraryLeg> legs, List<String> warningCodes) {
		return render(previewId, 0, trip, items, legs, Map.of(), null, role.name(), false, warningCodes);
	}

	private ItineraryDetailResponse render(String id, int version, Trip trip, List<ItineraryItem> contentItems,
			List<ItineraryLeg> contentLegs, Map<String, ItineraryItemActual> actualsByItemKey,
			FallbackMode fallbackMode, String myRole, boolean canEdit, List<String> warningCodes) {

		Map<UUID, Place> placesByPlaceId = lookupPlaces(contentItems);

		Map<LegKey, ItineraryLeg> legsByKey = contentLegs.stream()
				.collect(Collectors.toMap(leg -> new LegKey(leg.dayIndex(), leg.sequence()), leg -> leg));

		Map<Integer, List<ItineraryItem>> itemsByDay = contentItems.stream()
				.collect(Collectors.groupingBy(ItineraryItem::dayIndex));

		// 🔴 가격은 **읽을 때** 찾는다. 항목에 박아 두지 않는 것은 조사가 아직 도는 중이라
		// (`price-queue.mjs`) 오늘 만든 일정이 오늘 아는 것에 영원히 묶이기 때문이다.
		// 항목에 값이 이미 있으면 그것이 먼저다 — 나중에 박아 두기로 바뀌어도 여기는 안 고친다.
		Map<UUID, Integer> menuPriceByPlaceId = this.menuPrice.pricesOf(placesByPlaceId.keySet());

		List<ItineraryDetailResponse.Day> days = buildDays(trip, itemsByDay, legsByKey, placesByPlaceId,
				actualsByItemKey, menuPriceByPlaceId);

		Integer totalEstimatedCostKrw = sumOrNull(contentItems.stream()
				.map(item -> costOf(item, menuPriceByPlaceId)));
		Integer totalWalkingMeters = sumOrNull(contentLegs.stream()
				.map(ItineraryLeg::walkingMeters));

		return new ItineraryDetailResponse(
				id,
				// 이름은 Trip 이 정한다 — 사용자가 붙인 것이 있으면 그것, 없으면 기간.
				trip.displayTitle(),
				version,
				days,
				totalEstimatedCostKrw,
				totalWalkingMeters,
				fallbackMode,
				myRole,
				canEdit,
				warningCodes,
				trip.tripId(),
				// 이미 읽어 둔 항목에서 센다. DB 를 다시 묻지 않는다.
				accessibilityUnverifiedCount(contentItems),
				trip.partySize());
	}

	/**
	 * 휠체어 접근을 안 재 본 항목이 몇 곳인가. 세는 단위는 경고 건수가 아니라 항목 수다.
	 * 경고 코드 문자열은 {@link RecommendationCodes} 에서만 가져온다.
	 */
	private int accessibilityUnverifiedCount(List<ItineraryItem> items) {
		return (int) items.stream()
				.filter(item -> item.warningCodes().contains(RecommendationCodes.WARNING_ACCESSIBILITY_UNVERIFIED))
				.count();
	}

	/**
	 * 판 목록. 권한 판정은 {@link #getDetail} 과 같은 {@link ItineraryAccess#requireMember} 다 —
	 * 회원이면 VIEWER 도 볼 수 있고, 회원이 아니면 일정이 없는 것처럼 404 다.
	 *
	 * @throws ItineraryQueryController.ItineraryNotFoundException 일정이 없거나, 있어도
	 *     요청자가 그 일정이 속한 여행의 회원이 아니다
	 */
	@Transactional(readOnly = true)
	public ItineraryVersionsResponse listVersions(String itineraryId, String requesterUserId, Integer page,
			Integer size) {
		this.itineraryAccess.requireMember(itineraryId, requesterUserId);

		// 쪽 기본값은 여기서만 정한다. 컨트롤러가 따로 정하면 안 주고 부른 쪽과 page=0 으로
		// 부른 쪽의 크기가 달라져 판이 겹치거나 건너뛰어진다.
		int pageNumber = (page == null) ? 0 : Math.max(page, 0);
		int pageSize = (size == null) ? DEFAULT_VERSION_PAGE_SIZE
				: Math.min(Math.max(size, 1), MAX_VERSION_PAGE_SIZE);

		ItineraryRepository.VersionPage found = this.itineraryRepository.findVersions(itineraryId, pageNumber,
				pageSize);
		List<ItineraryVersion> versions = found.versions();

		// 판마다 만든 사람의 표시 이름을 싣는다. 이름 조회는 IN 질의 한 번이다.
		Map<String, String> names = this.actorNames.resolve(versions.stream().map(ItineraryVersion::createdBy).toList());
		List<ItineraryVersionSummaryResponse> items = versions.stream()
				.map(v -> ItineraryVersionSummaryResponse.of(v, names.get(v.createdBy())))
				.toList();

		return new ItineraryVersionsResponse(items, items.size(), found.hasMore());
	}

	/** 판 목록의 기본 쪽 크기. 되돌리기 화면이 한 번에 보여 주는 것보다 넉넉하다. */
	private static final int DEFAULT_VERSION_PAGE_SIZE = 50;

	/** 부르는 쪽이 아무리 크게 달라고 해도 여기까지. 상한이 없으면 파라미터 하나로 상한이 풀린다. */
	private static final int MAX_VERSION_PAGE_SIZE = 200;

	/**
	 * 여행 기간의 날짜를 전부 만든다. 항목이 0개인 날도 포함한다 — {@code itemsByDay} 에
	 * 없는 {@code dayIndex} 는 빈 {@code items} 로 채운다.
	 */
	private List<ItineraryDetailResponse.Day> buildDays(Trip trip, Map<Integer, List<ItineraryItem>> itemsByDay,
			Map<LegKey, ItineraryLeg> legsByKey, Map<UUID, Place> placesByPlaceId,
			Map<String, ItineraryItemActual> actualsByItemKey, Map<UUID, Integer> menuPriceByPlaceId) {

		List<ItineraryDetailResponse.Day> days = new ArrayList<>(trip.days());
		LocalDate date = trip.startDate();
		for (int dayIndex = 0; dayIndex < trip.days(); dayIndex++) {
			List<ItineraryItem> itemsOfDay = itemsByDay.getOrDefault(dayIndex, List.of()).stream()
					.sorted((a, b) -> Integer.compare(a.sequence(), b.sequence()))
					.toList();

			List<ItineraryDetailResponse.Item> items = new ArrayList<>(itemsOfDay.size());
			for (ItineraryItem item : itemsOfDay) {
				items.add(toItemDto(item, legsByKey, placesByPlaceId, actualsByItemKey.get(item.itemKey()),
						menuPriceByPlaceId));
			}

			days.add(new ItineraryDetailResponse.Day(date.toString(), items));
			date = date.plusDays(1);
		}
		return days;
	}

	/**
	 * @param actual 그 방문지에 남은 실제 시각. 아직 안 갔거나 안 적었으면 {@code null} 이고,
	 *     그때도 응답의 두 칸은 키가 있고 값만 {@code null} 이다
	 */
	private ItineraryDetailResponse.Item toItemDto(ItineraryItem item, Map<LegKey, ItineraryLeg> legsByKey,
			Map<UUID, Place> placesByPlaceId, ItineraryItemActual actual, Map<UUID, Integer> menuPriceByPlaceId) {

		Place place = placesByPlaceId.get(UUID.fromString(item.placeId()));
		if (place == null) {
			// FK 가 있는 한 있을 수 없는 상태다 — 조용히 넘기면 화면에 빈 제목이 뜬다.
			throw new IllegalStateException("일정 항목이 가리키는 place 를 찾을 수 없다: placeId=" + item.placeId());
		}

		ItineraryLeg incomingLeg = legsByKey.get(new LegKey(item.dayIndex(), item.sequence()));
		// 구간이 이 항목으로 들어오는 것이 맞는지 확인한다. 순서가 바뀐 판에서는 (날짜,순번)이
		// 같아도 도착지가 다를 수 있다.
		boolean incoming = incomingLeg != null && incomingLeg.toPlaceId().equals(item.placeId());
		Integer walkingMeters = incoming ? incomingLeg.walkingMeters() : null;
		Integer travelDurationMin = incoming ? incomingLeg.durationMin() : null;
		String travelDataStatus = (incoming && incomingLeg.dataStatus() != null)
				? incomingLeg.dataStatus().name()
				: null;
		// 요금도 같은 incoming 에 묶인다. 들어오는 구간이 아니면 남의 요금이므로 비운다 —
		// 모르는 것을 0 으로 채우지 않는다.
		Integer travelFareKrw = incoming ? incomingLeg.fareKrw() : null;
		// 선형도 같은 incoming 에 묶인다. 없으면 null 이고, 그때 출발·도착 두 점을 이어
		// 만들어 주지 않는다 — 그 직선을 화면이 「실제로 잰 길」로 그리게 된다.
		List<double[]> travelPath = incoming ? incomingLeg.path() : null;

		return new ItineraryDetailResponse.Item(
				item.itemKey(),
				startsAt(item),
				place.getNameKo(),
				null, // description — place 표에 설명 칸이 없다
				costOf(item, menuPriceByPlaceId),
				walkingMeters,
				item.locked(),
				item.dataStatus().name(),
				actual == null ? null : seoulIso(actual.arrivedAt()),
				actual == null ? null : seoulIso(actual.departedAt()),
				item.placeId(),
				travelDurationMin,
				travelDataStatus,
				travelFareKrw,
				travelPath,
				// ItineraryItem 이 생성자에서 빈 목록으로 정규화하므로 여기서 다시 감싸지 않는다.
				item.warningCodes(),
				// 모르면 null 이고 0 으로 채우지 않는다 — 위도 0·경도 0 은 기니만 한가운데라
				// 지도에 실제로 점이 찍힌다.
				place.getLat(),
				place.getLng());
	}

	/** {@code visit_date} + {@code start_time} 을 ISO-8601 로 합친다. 시간대는 항상 Asia/Seoul 이다(API-03). */
	private String startsAt(ItineraryItem item) {
		if (item.startTime() == null) {
			return null;
		}
		ZonedDateTime zoned = ZonedDateTime.of(item.visitDate(), item.startTime(), ZoneId.of("Asia/Seoul"));
		return zoned.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	/** 실제 시각을 계획 시각({@link #startsAt})과 같은 형식(Asia/Seoul)으로 적는다. */
	private static String seoulIso(Instant instant) {
		if (instant == null) {
			return null;
		}
		return instant.atZone(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
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
	/**
	 * 이 항목의 비용(원). 항목에 박힌 값이 있으면 그것, 없으면 그 장소의 대표 메뉴 값.
	 *
	 * <p>🔴 <b>둘 다 없으면 {@code null} 이다. {@code 0} 으로 바꾸지 않는다.</b> 0 은 화면에서
	 * <b>「무료」</b>로 그려지므로, 조사가 안 된 곳이 공짜인 것처럼 보이고 그 잘못이 합계에
	 * 섞여 들어간다. {@link #sumOrNull} 이 <b>값이 있는 칸만</b> 더하는 것도 같은 이유다 —
	 * 그래서 총합은 언제나 「적어도 이만큼」이다.
	 */
	private static Integer costOf(ItineraryItem item, Map<UUID, Integer> menuPriceByPlaceId) {
		if (item.estimatedCostKrw() != null) {
			return item.estimatedCostKrw();
		}
		return menuPriceByPlaceId.get(UUID.fromString(item.placeId()));
	}

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
