package com.gabolle.backend.itinerary.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryExclusion;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.application.port.TravelTime;
import com.gabolle.backend.itinerary.application.port.TravelTimePort;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.ItineraryWarningCodes;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.GeoDistance;
import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftPort;
import com.gabolle.backend.recommendation.application.port.ItineraryHandle;
import com.gabolle.backend.recommendation.application.port.ItineraryPublishConflictException;
import com.gabolle.backend.recommendation.application.port.ItineraryRevisionCommand;
import com.gabolle.backend.recommendation.application.port.ItineraryRevisionDraft;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 추천이 순위 매긴 장소를 실제 일정(항목·구간)으로 조립하고 저장한다 — S15P21E201-604.
 *
 * <p>🔴 클래스에 {@code @Transactional} 을 달지 않는다. {@link #persist} 는
 * {@code RecommendationRecorder.recordWithItinerary} 의 트랜잭션 안에서 불려야 하고,
 * 여기서 새 트랜잭션을 열면(또는 프록시가 트랜잭션 없이 부르면) 일정 저장과 Job·후보
 * 저장이 나뉘어 "일정만 생기고 Job 은 실패로 남는" 상태가 생길 수 있다.
 */
@Service
@Profile({ "db", "dev" })
public class ItineraryDraftService implements ItineraryDraftPort {

    private final TripRepository tripRepository;

    private final PlaceRepository placeRepository;

    private final ItineraryRepository itineraryRepository;

    private final Clock clock;

    /** 하루에 배정할 최대 항목 수. 프리셋·설정이 없으면 4 — 이 값 자체가 제품 결정은 아니다. */
    private final int maxItemsPerDay;

    /**
     * 🔴 S15P21E201-179 — 구간의 이동시간을 실제로 물어보는 문. {@code ObjectProvider} 로 받아
     * <b>없어도 뜨게</b> 한다. 이 서비스는 경로 계층을 안 스캔하는 슬라이스 컨텍스트에서도
     * 만들어지는데, 필수 의존성으로 두면 그 컨텍스트가 통째로 안 뜬다. 없으면 예전처럼
     * 직선거리로만 채우고 그 사실을 구간에 적는다.
     */
    private final ObjectProvider<TravelTimePort> travelTime;

    public ItineraryDraftService(TripRepository tripRepository, PlaceRepository placeRepository,
            ItineraryRepository itineraryRepository, Clock clock,
            @Value("${gabolle.itinerary.max-items-per-day:4}") int maxItemsPerDay,
            ObjectProvider<TravelTimePort> travelTime) {
        this.tripRepository = tripRepository;
        this.placeRepository = placeRepository;
        this.itineraryRepository = itineraryRepository;
        this.clock = clock;
        this.maxItemsPerDay = maxItemsPerDay;
        this.travelTime = travelTime;
    }

    /**
     * 🔴 순수 계산 + 읽기만 — DB 에 아무것도 쓰지 않는다. {@code RecommendationService
     * .continueJob} 이 트랜잭션 밖에서 부른다.
     */
    @Override
    public ItineraryDraft assemble(ItineraryDraftCommand command) {
        if (command.places().isEmpty()) {
            // 🔴 RecommendationService 가 returnedCount == 0 인 경우를 이미 NO_FEASIBLE_RESULT 로
            //    걸러 준다 — 여기 오면 그 방어선이 뚫린 것이다.
            throw new IllegalStateException("일정으로 조립할 장소가 없다: requestId=" + command.requestId());
        }

        Trip trip = this.tripRepository.findById(command.tripId())
                .orElseThrow(() -> new IllegalStateException("여행을 찾을 수 없다: " + command.tripId()));

        int days = trip.days();
        List<List<ItineraryDraftCommand.PlannedPlace>> byDay = distributeByDay(command.places(), days);

        List<ItineraryDraft.DraftItem> items = new ArrayList<>();
        // 구간을 만들 때 필요한, 날짜별 "그 날 다녀올 장소" 원본 순서.
        List<List<UUID>> placeIdsByDay = new ArrayList<>();

        for (int dayIndex = 0; dayIndex < byDay.size(); dayIndex++) {
            List<ItineraryDraftCommand.PlannedPlace> dayPlaces = byDay.get(dayIndex);
            LocalDate visitDate = trip.startDate().plusDays(dayIndex);
            List<UUID> placeIdsToday = new ArrayList<>(dayPlaces.size());

            for (int i = 0; i < dayPlaces.size(); i++) {
                ItineraryDraftCommand.PlannedPlace place = dayPlaces.get(i);
                placeIdsToday.add(place.placeId());

                // 🔴 시각은 여행이 실제 시각을 들고 있을 때만 배정한다. 프리셋
                //    ("MORNING_TO_EVENING")을 시각으로 바꾸는 규칙은 아직 확정되지 않았고
                //    (V20260904010000 마이그레이션 주석), 없는 규칙을 여기서 지어내면
                //    그 값이 계약이 된다. 없으면 시각을 비우고 UNKNOWN 으로 표시한다.
                Slot slot = slotFor(trip, i, dayPlaces.size());
                items.add(new ItineraryDraft.DraftItem(
                        dayIndex, visitDate, i + 1, place.placeId(),
                        UUID.randomUUID(), slot.start(), slot.end(), slot.stayMinutes(),
                        slot.dataStatus(), place.reasonCodes(), place.warningCodes()));
            }
            placeIdsByDay.add(placeIdsToday);
        }

        List<ItineraryDraft.DraftLeg> legs = buildLegs(trip, placeIdsByDay);

        return new ItineraryDraft(command.tripId(), command.userId(), command.requestId(),
                command.modelVersion(), command.featureVersion(), command.ontologyVersion(),
                command.policyVersion(), command.datasetVersion(), items, legs);
    }

    /**
     * 순위대로 날짜에 배분한다. 하루가 {@link #maxItemsPerDay} 를 채우면 다음 날로 넘긴다 —
     * 남는 후보를 버리지 않는다. 여행 마지막 날까지 다 찬 뒤에는(총 후보가 날짜 수 ×
     * 하루 최대치보다 많을 때) 더 넘길 날이 없으므로 그 이후는 전부 마지막 날에 쌓인다 —
     * 실제로는 topK 가 이 상황을 사실상 막는다.
     */
    private List<List<ItineraryDraftCommand.PlannedPlace>> distributeByDay(
            List<ItineraryDraftCommand.PlannedPlace> places, int days) {

        List<List<ItineraryDraftCommand.PlannedPlace>> byDay = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            byDay.add(new ArrayList<>());
        }

        int day = 0;
        for (ItineraryDraftCommand.PlannedPlace place : places) {
            while (day < days - 1 && byDay.get(day).size() >= this.maxItemsPerDay) {
                day++;
            }
            byDay.get(day).add(place);
        }
        return byDay;
    }

    /**
     * 한 항목이 차지할 시간 칸. 여행이 활동 시간대를 안 정했으면 세 값이 전부 비어 있고
     * {@code dataStatus} 가 {@code UNKNOWN} 이다.
     */
    private record Slot(LocalTime start, LocalTime end, Integer stayMinutes, String dataStatus) {

        static Slot unknown() {
            return new Slot(null, null, null, "UNKNOWN");
        }
    }

    /**
     * 하루의 활동 시간대를 그 날 항목 수로 균등하게 나눈다.
     *
     * <p>🔴 {@code ESTIMATED} 다. {@code VERIFIED} 가 아니다 — 이 시각은 장소의 영업시간이나
     * 실제 이동 소요를 본 것이 아니라 사용자가 정한 활동 시간대를 항목 수로 나눈 것뿐이다.
     * 확인한 사실과 추정을 같은 등급으로 적으면 화면이 둘을 구분해 보여줄 수 없다.
     *
     * <p>칸이 1분도 안 나올 만큼 항목이 많으면 시각을 배정하지 않는다. 시작과 끝이 같은
     * 칸은 {@code ck_itinerary_item_time_order}(끝이 시작보다 뒤여야 한다)에 걸린다.
     */
    private static Slot slotFor(Trip trip, int index, int countToday) {
        LocalTime windowStart = trip.timeWindowStart();
        LocalTime windowEnd = trip.timeWindowEnd();
        if (windowStart == null || windowEnd == null || !windowEnd.isAfter(windowStart) || countToday <= 0) {
            return Slot.unknown();
        }
        long windowMinutes = Duration.between(windowStart, windowEnd).toMinutes();
        long slotMinutes = windowMinutes / countToday;
        if (slotMinutes < 1) {
            return Slot.unknown();
        }
        LocalTime start = windowStart.plusMinutes(slotMinutes * index);
        LocalTime end = start.plusMinutes(slotMinutes);
        return new Slot(start, end, (int) slotMinutes, "ESTIMATED");
    }

    /**
     * 날짜별 구간 — 연속한 두 항목 사이. 각 날의 첫 구간은 여행 출발지에서 출발한다
     * ({@code Trip.originLat/Lng} — "매일 여기서 일정이 시작된다").
     */
    private List<ItineraryDraft.DraftLeg> buildLegs(Trip trip, List<List<UUID>> placeIdsByDay) {
        // 여행이 고른 이동수단의 첫 값을 쓴다. 아직 안 고른 여행이면 WALK 로 떨어진다 —
        // 지어낸 값이 아니라 "정보가 없을 때의 기본값" 이고, 그 선택이 걷기 거리 계산에
        // 그대로 이어진다({@link #walkingMetersFor}).
        String[] modes = trip.travelModes();
        String travelMode = (modes == null || modes.length == 0) ? "WALK" : modes[0];

        Map<UUID, Place> placesById = lookupPlaces(placeIdsByDay);

        List<ItineraryDraft.DraftLeg> legs = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < placeIdsByDay.size(); dayIndex++) {
            List<UUID> dayPlaceIds = placeIdsByDay.get(dayIndex);

            for (int i = 0; i < dayPlaceIds.size(); i++) {
                UUID toPlaceId = dayPlaceIds.get(i);
                UUID fromPlaceId = (i == 0) ? null : dayPlaceIds.get(i - 1);

                Double fromLat;
                Double fromLng;
                if (fromPlaceId == null) {
                    fromLat = trip.originLat();
                    fromLng = trip.originLng();
                }
                else {
                    Place from = placesById.get(fromPlaceId);
                    fromLat = (from != null && from.hasCoordinates()) ? from.getLat() : null;
                    fromLng = (from != null && from.hasCoordinates()) ? from.getLng() : null;
                }

                Place to = placesById.get(toPlaceId);
                Double toLat = (to != null && to.hasCoordinates()) ? to.getLat() : null;
                Double toLng = (to != null && to.hasCoordinates()) ? to.getLng() : null;

                // 🔴 S15P21E201-179 — 실제 경로를 물어본다. 못 받으면 그쪽이 직선거리로
                //    어림잡아 돌려주고 그 사실을 함께 알려 준다. 여기서 예외를 잡을 일이
                //    없다 — 그 문은 실패를 예외로 알리지 않는다(TravelTimePort 주석).
                TravelTime measured = measure(fromLat, fromLng, toLat, toLng, travelMode);

                Integer distanceM = measured.distanceM();
                if (distanceM == null && fromLat != null && fromLng != null && toLat != null && toLng != null) {
                    // 경로 계층이 아예 없는 컨텍스트다. 예전처럼 직선거리라도 적는다.
                    distanceM = (int) Math.round(GeoDistance.meters(fromLat, fromLng, toLat, toLng));
                }
                Integer walkingMeters = walkingMetersFor(travelMode, distanceM);

                legs.add(new ItineraryDraft.DraftLeg(dayIndex, i + 1,
                        fromPlaceId, toPlaceId, travelMode, distanceM, measured.durationMin(),
                        walkingMeters, measured.dataStatus()));
            }
        }
        return legs;
    }

    /**
     * 구간 하나의 실제 이동 거리·시간. 경로 계층이 없는 컨텍스트에서는 잴 수 없음으로 답한다.
     *
     * <p>🔴 <b>여기서 예외를 삼키지 않는다.</b> 포트가 실패를 예외로 알리지 않기로 약속했고,
     * 그 약속이 깨지면 조용히 넘기는 대신 시끄럽게 실패하는 편이 낫다 — 조용히 넘기면 모든
     * 구간이 이유 없이 비어 나가고 아무도 이유를 못 찾는다.
     */
    private TravelTime measure(Double fromLat, Double fromLng, Double toLat, Double toLng, String travelMode) {
        TravelTimePort port = this.travelTime.getIfAvailable();
        if (port == null) {
            return TravelTime.unknown();
        }
        return port.between(fromLat, fromLng, toLat, toLng, travelMode);
    }

    /**
     * 🔴 대중교통 구간에 직선거리를 "걸은 거리"로 적지 않는다 — 모드가 {@code WALK} 일 때만
     * 채운다. 지하철 구간에 직선거리를 넣으면 "지하철로 이만큼 걸었다"처럼 읽혀 틀린 답이
     * 된다.
     *
     * <p>지금은 {@link #buildLegs} 가 {@code travelMode} 로 항상 {@code "WALK"} 만
     * 넘긴다(Trip 도메인이 아직 이동수단을 노출하지 않는다) — 그래서 이 규칙의 대중교통
     * 갈래는 지금 실제 호출 경로로는 확인할 수 없다. {@code ItineraryDraftServiceTest} 가
     * 이 메서드를 직접 불러 그 갈래를 확인한다({@code itinerary} 패키지에 있어 {@code public}
     * 이어야 닿는다).
     */
    public static Integer walkingMetersFor(String travelMode, Integer distanceM) {
        return "WALK".equals(travelMode) ? distanceM : null;
    }

    private Map<UUID, Place> lookupPlaces(List<List<UUID>> placeIdsByDay) {
        List<UUID> all = new ArrayList<>();
        for (List<UUID> dayPlaceIds : placeIdsByDay) {
            all.addAll(dayPlaceIds);
        }
        Map<UUID, Place> byId = new HashMap<>();
        for (Place place : this.placeRepository.findByPlaceIdIn(all)) {
            byId.put(place.getPlaceId(), place);
        }
        return byId;
    }

    /**
     * 🔴 바깥 트랜잭션 안에서 불린다 — {@code RecommendationRecorder.recordWithItinerary}.
     * 여기서 순서를 뒤집지 않는다: 일정(itineraries·itinerary_versions·항목·구간)을 먼저
     * 만들어야, 그 뒤에 Job 에 {@code itinerary_id} 를 붙이는 것(attachItinerary)이
     * 의미가 있다.
     */
    @Override
    public ItineraryHandle persist(ItineraryDraft draft) {
        String itineraryId = UUID.randomUUID().toString();
        String itineraryVersionId = UUID.randomUUID().toString();
        Instant now = this.clock.instant();
        String requestIdString = draft.requestId().toString();

        Itinerary itinerary = new Itinerary(itineraryId, draft.tripId(), 1);
        ItineraryVersion.Versions versions = new ItineraryVersion.Versions(
                draft.modelVersion(), draft.featureVersion(), draft.ontologyVersion(),
                draft.policyVersion(), draft.datasetVersion());
        ItineraryVersion firstVersion = new ItineraryVersion(itineraryVersionId, itineraryId, 1, null,
                ItineraryVersion.Operation.CREATE, draft.userId(), requestIdString, versions, now,
                requestIdString);

        List<ItineraryItem> items = new ArrayList<>(draft.items().size());
        for (ItineraryDraft.DraftItem draftItem : draft.items()) {
            items.add(new ItineraryItem(
                    UUID.randomUUID().toString(), itineraryVersionId, draftItem.itemKey().toString(),
                    draftItem.dayIndex(), draftItem.visitDate(), draftItem.sequence(),
                    draftItem.placeId().toString(), draftItem.startTime(), draftItem.endTime(),
                    draftItem.stayMinutes(), false, null,
                    ItineraryItem.DataStatus.valueOf(draftItem.dataStatus()),
                    draftItem.reasonCodes(), draftItem.warningCodes(), requestIdString, now));
        }

        List<ItineraryLeg> legs = new ArrayList<>(draft.legs().size());
        for (ItineraryDraft.DraftLeg draftLeg : draft.legs()) {
            legs.add(new ItineraryLeg(
                    UUID.randomUUID().toString(), itineraryVersionId, draftLeg.dayIndex(), draftLeg.sequence(),
                    draftLeg.fromPlaceId() != null ? draftLeg.fromPlaceId().toString() : null,
                    draftLeg.toPlaceId().toString(), draftLeg.travelMode(), draftLeg.distanceM(),
                    draftLeg.durationMin(), draftLeg.walkingMeters(), null, null,
                    draftLeg.dataStatus(), now));
        }

        // 🔴 2026-09-06 (S15P21E201-662) — 판과 내용을 한 번에 넘긴다. 예전에는 create 로
        //    판을 먼저 만들고 saveContent 로 내용을 나중에 넣었는데, 그 두 걸음 사이가
        //    "판은 있는데 내용이 없는" 상태였다. 저장소 인터페이스에서 그 걸음을 없앴다.
        this.itineraryRepository.create(itinerary, firstVersion, items, legs);

        return new ItineraryHandle(itineraryId, 1);
    }

    // ------------------------------------------------------------------
    // S15P21E201-249 — 있는 판의 하루만 다시 채운다
    // ------------------------------------------------------------------

    /**
     * {@link #revise} 가 만들고 {@link #publish} 가 받는 초안. 추천 계층에는
     * {@link ItineraryRevisionDraft} 라는 겉면만 보인다 — 안에 든 것은 전부 일정 도메인 타입이다.
     *
     * <p>{@code newVersionId} 를 여기서 미리 정하는 이유 — 항목·구간·제외 행의 부모 키가
     * 그 값이라 초안을 만드는 시점에 이미 필요하다. 게시가 실패하면 그 id 는 그냥 버려진다.
     */
    record RevisionDraft(
            String itineraryId,
            int baseVersion,
            String newVersionId,
            String userId,
            String requestId,
            ItineraryVersion.Operation operation,
            ItineraryVersion.Versions versions,
            List<ItineraryItem> items,
            List<ItineraryLeg> legs,
            List<ItineraryExclusion> exclusions,
            List<String> warningCodes,
            int keptCount,
            int filledCount) implements ItineraryRevisionDraft {
    }

    /**
     * 🔴 순수 계산 + 읽기 — {@link #assemble} 과 같은 자리에서(트랜잭션 밖) 불린다.
     *
     * <h2>무엇을 보존하고 무엇을 채우나</h2>
     * <ul>
     *   <li><b>다른 날은 그대로 복사한다.</b> {@link ItineraryRevision#copyOf} 가 항목·구간·제외
     *       목록을 {@code item_key} 를 유지한 채 새 판으로 옮긴다 — 고정 편집과 같은 복사 규칙이다</li>
     *   <li><b>그 날의 고정 항목은 남긴다.</b> {@code ITEM_REMOVE} 는 지정한 항목 하나만 빼고 나머지를
     *       전부 남긴다(고정 여부와 무관) — 사용자가 뺀 것은 그 하나다. {@code ITINERARY_RECALCULATE}
     *       는 고정 항목만 남기고 나머지를 비운다. 기준 항목({@code itemKey})이 주어지면 그 항목보다
     *       앞선 자리(이미 다녀온 곳)도 남긴다</li>
     *   <li><b>빈 자리는 순위 풀에서 채운다.</b> 그 날 원래 있던 항목 수를 목표로 한다 — 하루의
     *       크기를 재계산이 바꾸지 않는다. 원래 비어 있던 날만 {@link #maxItemsPerDay} 를 목표로 한다.
     *       제외된 장소와 이 일정에 이미 있는 장소는 풀에서 뺀다</li>
     *   <li><b>모자라면 비워 둔다.</b> 조건을 완화해 억지로 채우지 않는다(요구사항 3.2). 그 사실은 판
     *       경고({@link ItineraryWarningCodes})로 남는다 — 항목 행이 없어 항목 경고에는 적을 곳이 없다</li>
     * </ul>
     *
     * <h2>제외 목록은 판에 매달린다</h2>
     * 바탕 판의 제외 목록을 복사한 위에 이번 제외를 더한다. 그래서 "재계산을 몇 번 해도 뺀 장소가
     * 다시 안 나온다" 는 별도 조회 없이 복사 한 가지로 보장되고, 되돌리기가 제외까지 되돌린다.
     *
     * @throws IllegalStateException 바탕 판의 내용이 없다, 여행을 못 찾았다, dayIndex 가 여행 밖이다
     * @throws ItineraryRevision.ItemNotFoundException 기준 항목이 그 날에 없다
     */
    @Override
    public ItineraryRevisionDraft revise(ItineraryRevisionCommand command) {
        ItineraryContent base = this.itineraryRepository.findContent(command.itineraryId(), command.baseVersion())
                .orElseThrow(() -> new IllegalStateException("바탕 판의 내용이 없다: itineraryId="
                        + command.itineraryId() + ", version=" + command.baseVersion()));
        Itinerary itinerary = this.itineraryRepository.findById(command.itineraryId())
                .orElseThrow(() -> new IllegalStateException("일정을 찾을 수 없다: " + command.itineraryId()));
        Trip trip = this.tripRepository.findById(itinerary.tripId())
                .orElseThrow(() -> new IllegalStateException("여행을 찾을 수 없다: " + itinerary.tripId()));
        boolean remove = command.jobType() == JobType.ITEM_REMOVE;

        // 뺄 항목이 어느 날에 있는지는 요청이 아니라 바탕 판이 안다. 요청이 dayIndex 를 같이 줬는데
        // 판과 다르면 그 요청은 다른 판을 보고 만든 것이다 — 조용히 판을 따르지 않고 거절한다.
        int dayIndex = command.dayIndex();
        if (remove) {
            ItineraryItem target = base.items().stream()
                    .filter((item) -> item.itemKey().equals(command.itemKey()))
                    .findFirst()
                    .orElseThrow(() -> new ItineraryRevision.ItemNotFoundException(command.itemKey()));
            if (dayIndex >= 0 && dayIndex != target.dayIndex()) {
                throw new IllegalStateException("항목 " + command.itemKey() + " 은 " + target.dayIndex()
                        + "일차에 있는데 요청은 " + dayIndex + "일차라고 한다");
            }
            dayIndex = target.dayIndex();
        }
        if (dayIndex < 0 || dayIndex >= trip.days()) {
            throw new IllegalStateException("dayIndex " + dayIndex + " 는 " + trip.days() + "일짜리 여행 밖이다");
        }
        final int day = dayIndex;

        String newVersionId = UUID.randomUUID().toString();
        Instant now = this.clock.instant();
        String requestIdString = command.requestId().toString();

        // 1. 다른 날·제외 목록은 통째로 복사한다. 그 날 항목은 아래서 새로 만든다.
        ItineraryRevision.Draft copied = ItineraryRevision.copyOf(base, newVersionId, now);

        List<ItineraryItem> dayItems = base.items().stream()
                .filter((item) -> item.dayIndex() == day)
                .sorted(Comparator.comparingInt(ItineraryItem::sequence))
                .toList();

        // 2. 그 날에서 무엇을 남기나.
        List<ItineraryItem> kept = new ArrayList<>();
        ItineraryItem removedItem = null;
        if (remove) {
            for (ItineraryItem item : dayItems) {
                if (item.itemKey().equals(command.itemKey())) {
                    removedItem = item;
                }
                else {
                    kept.add(item);
                }
            }
            if (removedItem == null) {
                throw new ItineraryRevision.ItemNotFoundException(command.itemKey());
            }
        }
        else {
            int cutSequence = 0;
            if (command.itemKey() != null) {
                cutSequence = dayItems.stream()
                        .filter((item) -> item.itemKey().equals(command.itemKey()))
                        .mapToInt(ItineraryItem::sequence)
                        .findFirst()
                        .orElseThrow(() -> new ItineraryRevision.ItemNotFoundException(command.itemKey()));
            }
            for (ItineraryItem item : dayItems) {
                if (item.locked() || item.sequence() < cutSequence) {
                    kept.add(item);
                }
            }
        }

        // 3. 제외 목록 — 바탕 판 것 + 이번에 뺀 것 + 요청이 따로 준 것. 한 판에 같은 장소는 한 번만
        //    (uq_itinerary_excluded). 뺀 항목의 장소가 요청의 newlyExcludedPlaceIds 에도 들어 있는 것이
        //    보통이라(ItineraryRecalculationService 가 그렇게 채운다) 여기서 걸러야 한다.
        List<ItineraryExclusion> exclusions = new ArrayList<>(copied.exclusions());
        Set<String> excludedPlaceIds = new HashSet<>();
        for (ItineraryExclusion exclusion : exclusions) {
            excludedPlaceIds.add(exclusion.placeId());
        }
        if (removedItem != null && excludedPlaceIds.add(removedItem.placeId())) {
            exclusions.add(new ItineraryExclusion(UUID.randomUUID().toString(), newVersionId, removedItem.placeId(),
                    removedItem.itemKey(), command.userId(), ItineraryExclusion.REASON_USER_REMOVED,
                    command.operationalReason(), now));
        }
        for (UUID placeId : command.newlyExcludedPlaceIds()) {
            if (excludedPlaceIds.add(placeId.toString())) {
                exclusions.add(new ItineraryExclusion(UUID.randomUUID().toString(), newVersionId, placeId.toString(),
                        null, command.userId(), ItineraryExclusion.REASON_USER_REMOVED, command.operationalReason(),
                        now));
            }
        }

        // 4. 풀에서 뺄 장소 — 제외된 것과 이 일정에 이미 있는 것(다른 날 + 남긴 것).
        Set<String> unavailable = new HashSet<>(excludedPlaceIds);
        for (ItineraryItem item : copied.items()) {
            if (item.dayIndex() != dayIndex) {
                unavailable.add(item.placeId());
            }
        }
        for (ItineraryItem item : kept) {
            unavailable.add(item.placeId());
        }

        // 5. 채운다. 목표는 그 날 원래 항목 수 — 재계산이 하루의 크기를 바꾸지 않는다.
        int target = dayItems.isEmpty() ? this.maxItemsPerDay : dayItems.size();
        int vacancies = Math.max(0, target - kept.size());
        List<ItineraryDraftCommand.PlannedPlace> fills = new ArrayList<>(vacancies);
        for (ItineraryDraftCommand.PlannedPlace candidate : command.rankedPool()) {
            if (fills.size() >= vacancies) {
                break;
            }
            String placeId = candidate.placeId().toString();
            if (unavailable.add(placeId)) {
                fills.add(candidate);
            }
        }

        // 6. 그 날 항목을 다시 만든다 — 남긴 것 먼저(원래 순서), 그 뒤에 채운 것(순위 순서).
        //    시각은 그 날 항목 수로 다시 나눈다(slotFor) — 고정 항목의 시각도 함께 움직인다.
        int countToday = kept.size() + fills.size();
        LocalDate visitDate = trip.startDate().plusDays(dayIndex);
        List<ItineraryItem> dayResult = new ArrayList<>(countToday);
        boolean lockedTimeMoved = false;
        int sequence = 1;
        for (ItineraryItem item : kept) {
            Slot slot = slotFor(trip, sequence - 1, countToday);
            if (item.locked() && item.startTime() != null && !item.startTime().equals(slot.start())) {
                lockedTimeMoved = true;
            }
            dayResult.add(new ItineraryItem(UUID.randomUUID().toString(), newVersionId, item.itemKey(),
                    dayIndex, visitDate, sequence, item.placeId(), slot.start(), slot.end(), slot.stayMinutes(),
                    item.locked(), item.estimatedCostKrw(), ItineraryItem.DataStatus.valueOf(slot.dataStatus()),
                    item.reasonCodes(), item.warningCodes(), item.sourceRequestId(), now));
            sequence++;
        }
        for (ItineraryDraftCommand.PlannedPlace fill : fills) {
            Slot slot = slotFor(trip, sequence - 1, countToday);
            dayResult.add(new ItineraryItem(UUID.randomUUID().toString(), newVersionId, UUID.randomUUID().toString(),
                    dayIndex, visitDate, sequence, fill.placeId().toString(), slot.start(), slot.end(),
                    slot.stayMinutes(), false, null, ItineraryItem.DataStatus.valueOf(slot.dataStatus()),
                    fill.reasonCodes(), fill.warningCodes(), requestIdString, now));
            sequence++;
        }

        // 7. 판 경고.
        List<String> warningCodes = new ArrayList<>();
        if (vacancies > 0 && fills.isEmpty()) {
            warningCodes.add(ItineraryWarningCodes.RECALC_NO_CANDIDATE);
        }
        else if (fills.size() < vacancies) {
            warningCodes.add(ItineraryWarningCodes.RECALC_DAY_PARTIALLY_FILLED);
        }
        if (lockedTimeMoved) {
            warningCodes.add(ItineraryWarningCodes.RECALC_TIMES_RESHUFFLED);
        }

        // 8. 전체 항목 = 다른 날 복사본 + 그 날 새 항목. 구간은 전부 다시 만든다 — 그 날 구간만
        //    바꾸면 되지만, 복사한 구간과 새 구간의 만드는 규칙이 갈릴 자리를 남기지 않는다.
        List<ItineraryItem> items = new ArrayList<>();
        for (ItineraryItem item : copied.items()) {
            if (item.dayIndex() != dayIndex) {
                items.add(item);
            }
        }
        items.addAll(dayResult);
        items.sort(Comparator.comparingInt(ItineraryItem::dayIndex).thenComparingInt(ItineraryItem::sequence));

        List<List<UUID>> placeIdsByDay = new ArrayList<>(trip.days());
        for (int d = 0; d < trip.days(); d++) {
            placeIdsByDay.add(new ArrayList<>());
        }
        for (ItineraryItem item : items) {
            if (item.dayIndex() < trip.days()) {
                placeIdsByDay.get(item.dayIndex()).add(UUID.fromString(item.placeId()));
            }
        }
        List<ItineraryLeg> legs = new ArrayList<>();
        for (ItineraryDraft.DraftLeg draftLeg : buildLegs(trip, placeIdsByDay)) {
            legs.add(new ItineraryLeg(
                    UUID.randomUUID().toString(), newVersionId, draftLeg.dayIndex(), draftLeg.sequence(),
                    draftLeg.fromPlaceId() != null ? draftLeg.fromPlaceId().toString() : null,
                    draftLeg.toPlaceId().toString(), draftLeg.travelMode(), draftLeg.distanceM(),
                    draftLeg.durationMin(), draftLeg.walkingMeters(), null, null,
                    draftLeg.dataStatus(), now));
        }

        ItineraryVersion.Versions versions = new ItineraryVersion.Versions(
                command.modelVersion(), command.featureVersion(), command.ontologyVersion(),
                command.policyVersion(), command.datasetVersion());
        ItineraryVersion.Operation operation = remove
                ? ItineraryVersion.Operation.REMOVE_ITEM
                : ItineraryVersion.Operation.REGENERATE_DAY;

        return new RevisionDraft(command.itineraryId(), command.baseVersion(), newVersionId, command.userId(),
                requestIdString, operation, versions, items, legs, exclusions, warningCodes,
                kept.size(), fills.size());
    }

    /**
     * 🔴 바깥 트랜잭션 안 — {@code RecommendationRecorder.recordWithItineraryRevision}.
     *
     * <p>FR-ITN-09 의 CAS(**내가 읽은 뒤로 바뀐 게 없을 때만 쓴다**)는 저장소가 한다 —
     * {@code appendVersion} 의 판 번호 UNIQUE 와 포인터 조건부 UPDATE. 여기서는 그 실패
     * ({@link StaleItineraryVersionException})를 포트 예외로 바꿔 던질 뿐이다. 바깥 트랜잭션이
     * 되돌려져 이전 판이 그대로 최신으로 남는다.
     */
    @Override
    public ItineraryHandle publish(ItineraryRevisionDraft draft) {
        if (!(draft instanceof RevisionDraft revision)) {
            throw new IllegalArgumentException("이 초안은 ItineraryDraftService.revise 가 만든 것이 아니다: "
                    + (draft == null ? "null" : draft.getClass().getName()));
        }
        int newVersion = revision.baseVersion() + 1;
        ItineraryVersion version = new ItineraryVersion(revision.newVersionId(), revision.itineraryId(), newVersion,
                revision.baseVersion(), revision.operation(), revision.userId(), revision.requestId(),
                revision.versions(), this.clock.instant(), revision.requestId(), revision.warningCodes());
        try {
            this.itineraryRepository.appendVersion(version, revision.items(), revision.legs(), revision.exclusions());
        }
        catch (StaleItineraryVersionException ex) {
            throw new ItineraryPublishConflictException(revision.itineraryId(), revision.baseVersion(),
                    ex.latestVersion());
        }
        return new ItineraryHandle(revision.itineraryId(), newVersion);
    }
}
