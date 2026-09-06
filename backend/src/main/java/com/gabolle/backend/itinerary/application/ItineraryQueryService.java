package com.gabolle.backend.itinerary.application;

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
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryVersionSummaryResponse;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
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

	public ItineraryQueryService(ItineraryRepository itineraryRepository, ItineraryAccess itineraryAccess,
			PlaceRepository placeRepository, RecommendationJobRepository recommendationJobRepository) {
		this.itineraryRepository = itineraryRepository;
		this.itineraryAccess = itineraryAccess;
		this.placeRepository = placeRepository;
		this.recommendationJobRepository = recommendationJobRepository;
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

		List<ItineraryDetailResponse.Day> days = buildDays(trip, itemsByDay, legsByKey, placesByPlaceId);

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
				access.role().canEdit());
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
	public List<ItineraryVersionSummaryResponse> listVersions(String itineraryId, String requesterUserId) {
		this.itineraryAccess.requireMember(itineraryId, requesterUserId);
		return this.itineraryRepository.findVersions(itineraryId).stream()
				.map(ItineraryVersionSummaryResponse::of)
				.toList();
	}

	/**
	 * 여행 기간의 날짜를 전부 만든다. 🔴 항목이 0개인 날도 포함한다 — {@code itemsByDay} 에
	 * 없는 {@code dayIndex} 는 빈 {@code items} 로 채운다.
	 */
	private List<ItineraryDetailResponse.Day> buildDays(Trip trip, Map<Integer, List<ItineraryItem>> itemsByDay,
			Map<LegKey, ItineraryLeg> legsByKey, Map<UUID, Place> placesByPlaceId) {

		List<ItineraryDetailResponse.Day> days = new ArrayList<>(trip.days());
		LocalDate date = trip.startDate();
		for (int dayIndex = 0; dayIndex < trip.days(); dayIndex++) {
			List<ItineraryItem> itemsOfDay = itemsByDay.getOrDefault(dayIndex, List.of()).stream()
					.sorted((a, b) -> Integer.compare(a.sequence(), b.sequence()))
					.toList();

			List<ItineraryDetailResponse.Item> items = new ArrayList<>(itemsOfDay.size());
			for (ItineraryItem item : itemsOfDay) {
				items.add(toItemDto(item, legsByKey, placesByPlaceId));
			}

			days.add(new ItineraryDetailResponse.Day(date.toString(), items));
			date = date.plusDays(1);
		}
		return days;
	}

	private ItineraryDetailResponse.Item toItemDto(ItineraryItem item, Map<LegKey, ItineraryLeg> legsByKey,
			Map<UUID, Place> placesByPlaceId) {

		Place place = placesByPlaceId.get(UUID.fromString(item.placeId()));
		if (place == null) {
			// FK 가 있는 한 있을 수 없는 상태다 — 조용히 넘기면 화면에 빈 제목이 뜬다.
			throw new IllegalStateException("일정 항목이 가리키는 place 를 찾을 수 없다: placeId=" + item.placeId());
		}

		ItineraryLeg incomingLeg = legsByKey.get(new LegKey(item.dayIndex(), item.sequence()));
		Integer walkingMeters = (incomingLeg != null && incomingLeg.toPlaceId().equals(item.placeId()))
				? incomingLeg.walkingMeters()
				: null;

		return new ItineraryDetailResponse.Item(
				item.itemKey(),
				startsAt(item),
				place.getNameKo(),
				null, // description — place 표에 설명 칸이 없다
				item.estimatedCostKrw(),
				walkingMeters,
				item.locked(),
				item.dataStatus().name());
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
