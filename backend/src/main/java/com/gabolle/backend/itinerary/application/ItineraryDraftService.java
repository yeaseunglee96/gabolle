package com.gabolle.backend.itinerary.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.place.service.OpeningHoursFilterPort;
import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryExclusion;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.ItineraryWarningCodes;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
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

    private final ItineraryRepository itineraryRepository;

    private final Clock clock;

    /** 하루에 배정할 최대 항목 수. 프리셋·설정이 없으면 4 — 이 값 자체가 제품 결정은 아니다. */
    private final int maxItemsPerDay;

    private final int maxFoodPerDay;

    private final String foodCategory;

    /**
     * 구간(leg) 계산 — S15P21E201-755 뽑아내기. 생성과 편집(순서 바꾸기) 두 경로가 같은 규칙을
     * 써야 해서 {@link ItineraryLegPlanner} 로 뽑았다. 자세한 이유는 그 클래스 머리말에 있다.
     */
    private final ItineraryLegPlanner legPlanner;

    /**
     * 그 시각에 문을 여는가 — S15P21E201-857.
     *
     * <p>후보를 고르는 단계가 아니라 <b>자리에 앉히는 단계</b>에서 묻는다. 후보 조회는 여행
     * 전체에 한 번 부르고 시각 칸은 한 순간이라, 거기에 첫날 아침을 넣으면 화요일 오후에
     * 방문할 곳까지 월요일 아침 기준으로 걸러진다. 항목마다 날짜와 시각이 다른 이 자리에서만
     * 제대로 물을 수 있다.
     */
    private final OpeningHoursFilterPort openingHours;

    public ItineraryDraftService(TripRepository tripRepository, ItineraryRepository itineraryRepository, Clock clock,
            @Value("${gabolle.itinerary.max-items-per-day:4}") int maxItemsPerDay,
            @Value("${gabolle.itinerary.max-food-per-day:3}") int maxFoodPerDay,
            @Value("${gabolle.itinerary.food-category:FOOD}") String foodCategory,
            ItineraryLegPlanner legPlanner, OpeningHoursFilterPort openingHours) {
        this.tripRepository = tripRepository;
        this.itineraryRepository = itineraryRepository;
        this.clock = clock;
        this.maxItemsPerDay = maxItemsPerDay;
        this.maxFoodPerDay = maxFoodPerDay;
        this.foodCategory = foodCategory;
        this.legPlanner = legPlanner;
        this.openingHours = openingHours;
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

            List<Placed> placedToday = placeIntoSlots(trip, dayPlaces, visitDate);

            for (int i = 0; i < placedToday.size(); i++) {
                Placed placed = placedToday.get(i);
                ItineraryDraftCommand.PlannedPlace place = placed.place();
                placeIdsToday.add(place.placeId());

                Slot slot = placed.slot();
                items.add(new ItineraryDraft.DraftItem(
                        dayIndex, visitDate, i + 1, place.placeId(),
                        UUID.randomUUID(), slot.start(), slot.end(), slot.stayMinutes(),
                        slot.dataStatus(), place.reasonCodes(), placed.warningCodes()));
            }
            placeIdsByDay.add(placeIdsToday);
        }

        List<ItineraryDraft.DraftLeg> legs = this.legPlanner.buildLegs(trip, placeIdsByDay);

        return new ItineraryDraft(command.tripId(), command.userId(), command.requestId(),
                command.modelVersion(), command.featureVersion(), command.ontologyVersion(),
                command.policyVersion(), command.datasetVersion(), items, legs);
    }

    /**
     * 순위대로 날짜에 배분한다. 하루가 {@link #maxItemsPerDay} 를 채우면 다음 날로 넘기고,
     * 모든 날이 다 차면 <b>남은 후보는 일정에 넣지 않는다</b> — S15P21E201-902.
     *
     * <p>예전에는 더 넘길 날이 없으면 남은 것을 전부 마지막 날에 쌓았다. 그 자리 javadoc 은
     * "실제로는 topK 가 이 상황을 사실상 막는다" 고 적어 두었는데 <b>그 가정이 틀렸다.</b>
     * 추천은 기본 10곳을 내놓고 하루 상한은 4라서, 1일 여행이면 {@code days - 1 == 0} 이라
     * 넘길 날이 아예 없어 10곳이 통째로 하루에 들어갔다(2026-09-13 실사용 확인).
     *
     * <p>넘치는 것을 마지막 날에 쌓는 것보다 안 넣는 것이 맞다. 하루에 열 곳은 일정이 아니고,
     * 그렇게 쌓인 날은 이동 시간도 머무는 시간도 계산이 안 맞는다.
     *
     * <p><b>추천 결과를 줄이는 것이 아니다.</b> 순위표는 그대로 다 남아서 대체 장소 제시와
     * 재계산이 쓴다({@code ItineraryRevisionCommand.rankedPool}). 여기서 정하는 것은
     * "일정에 실제로 놓는 수" 뿐이다.
     *
     * <h2>하루에 밥집이 몇 곳인가 — S15P21E201-903</h2>
     *
     * 운영 후보의 89%가 음식점이라 순위대로만 담으면 하루가 전부 밥집이 된다. 그래서 첫
     * 배분에서는 밥집을 하루 {@link #maxFoodPerDay} 곳까지만 앉히고 나머지 자리를 명소로
     * 채운다. 명소가 모자라 자리가 남으면 미뤄 둔 밥집으로 메운다 — 끼니 상한 때문에 자리를
     * 비워 두는 것보다 갈 곳이 있는 편이 낫다.
     */
    private List<List<ItineraryDraftCommand.PlannedPlace>> distributeByDay(
            List<ItineraryDraftCommand.PlannedPlace> places, int days) {

        List<List<ItineraryDraftCommand.PlannedPlace>> byDay = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            byDay.add(new ArrayList<>());
        }

        int[] foodPerDay = new int[days];
        List<ItineraryDraftCommand.PlannedPlace> deferredFood = new ArrayList<>();

        for (ItineraryDraftCommand.PlannedPlace place : places) {
            if (isFood(place) && !seat(byDay, foodPerDay, place, true)) {
                deferredFood.add(place);
            }
            else if (!isFood(place)) {
                seat(byDay, foodPerDay, place, false);
            }
        }

        // 명소가 모자라 빈 자리가 남으면 미뤄 둔 밥집으로 채운다. 끼니 상한 때문에 자리를
        // 비워 두는 것보다, 덜 이상적이어도 갈 곳이 있는 편이 낫다.
        for (ItineraryDraftCommand.PlannedPlace place : deferredFood) {
            seat(byDay, foodPerDay, place, false);
        }
        return byDay;
    }

    /**
     * 순위가 높은 날부터 자리를 찾아 앉힌다. 앉혔으면 {@code true}.
     *
     * @param respectFoodCap 밥집 상한을 지킬지. 첫 배분에서는 지키고, 명소가 모자라 남은
     *     자리를 메울 때는 안 지킨다
     */
    private boolean seat(List<List<ItineraryDraftCommand.PlannedPlace>> byDay, int[] foodPerDay,
            ItineraryDraftCommand.PlannedPlace place, boolean respectFoodCap) {

        boolean food = isFood(place);
        for (int day = 0; day < byDay.size(); day++) {
            if (byDay.get(day).size() >= this.maxItemsPerDay) {
                continue;
            }
            if (food && respectFoodCap && foodPerDay[day] >= this.maxFoodPerDay) {
                continue;
            }
            byDay.get(day).add(place);
            if (food) {
                foodPerDay[day]++;
            }
            return true;
        }
        return false;
    }

    /** 갈래를 모르면 밥집이 아닌 것으로 다룬다 — 모르는 것을 끼니로 세지 않는다. */
    private boolean isFood(ItineraryDraftCommand.PlannedPlace place) {
        return place.category() != null && place.category().equalsIgnoreCase(this.foodCategory);
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
     * 그 날의 장소를 시간 칸에 앉힌다 — S15P21E201-857.
     *
     * <p>칸을 앞에서부터 채우면서, 그 시각에 <b>닫는다고 원천이 말한</b> 장소는 그 자리에
     * 놓지 않고 다음 후보를 본다. 후보는 순위 순으로 훑으므로 걸리는 것이 없으면 순위가
     * 그대로 유지된다.
     *
     * <h2>모른다를 닫힘처럼 다루지 않는다</h2>
     * 영업시간이 들어간 장소는 관광공사 268곳뿐이다(S15P21E201-852). 모름을 닫힘으로 보면
     * 아직 안 넣은 2,355곳이 일정에서 통째로 빠지고, 사용자에게는 그것이 "갈 데가 없다" 로
     * 보인다. 그래서 모름은 앉힌다.
     *
     * <h2>바꿀 후보가 없으면 그대로 놓고 적는다</h2>
     * 남은 후보가 전부 그 시각에 닫혀 있으면 순위 그대로 앉히고 그 항목의 경고에
     * {@code OPENING_HOURS_CLOSED} 를 더한다. 빈 자리를 남기지 않는 이유는 일정에 구멍이
     * 생기면 사용자가 그날 무엇을 할지 알 수 없기 때문이고, 조용히 앉히지 않는 이유는
     * 화면이 그것을 "확인했고 문제 없음" 으로 읽기 때문이다.
     *
     * <h2>시각이 없으면 아무것도 안 한다</h2>
     * 여행이 활동 시간대를 안 정했으면 칸에 시각이 없고, 시각이 없으면 문이 열렸는지 물어볼
     * 수가 없다. 그때는 순위 그대로 앉힌다.
     */
    private List<Placed> placeIntoSlots(Trip trip, List<ItineraryDraftCommand.PlannedPlace> dayPlaces,
                                        LocalDate visitDate) {

        int count = dayPlaces.size();
        List<Placed> placed = new ArrayList<>(count);
        boolean[] used = new boolean[count];

        for (int slotIndex = 0; slotIndex < count; slotIndex++) {
            Slot slot = slotFor(trip, slotIndex, count);
            OffsetDateTime at = (slot.start() == null) ? null
                    : visitDate.atTime(slot.start()).atZone(ZONE).toOffsetDateTime();

            int chosen = -1;
            if (at != null) {
                for (int i = 0; i < count; i++) {
                    if (used[i]) {
                        continue;
                    }
                    if (this.openingHours.openAt(dayPlaces.get(i).placeId(), at)
                            != OpeningHoursFilterPort.Answer.CLOSED) {
                        chosen = i;
                        break;
                    }
                }
            }

            boolean forced = chosen < 0;
            if (forced) {
                for (int i = 0; i < count; i++) {
                    if (!used[i]) {
                        chosen = i;
                        break;
                    }
                }
            }

            used[chosen] = true;
            ItineraryDraftCommand.PlannedPlace place = dayPlaces.get(chosen);
            List<String> warnings = place.warningCodes();
            if (forced && at != null
                    && this.openingHours.openAt(place.placeId(), at) == OpeningHoursFilterPort.Answer.CLOSED) {
                warnings = new ArrayList<>(warnings == null ? List.of() : warnings);
                warnings.add(ItineraryOpeningHoursChecker.VIOLATION_CLOSED);
            }
            placed.add(new Placed(place, slot, warnings));
        }
        return placed;
    }

    /**
     * 한 자리에 앉은 결과.
     *
     * @param warningCodes 후보의 경고에 이 자리에서 생긴 것을 더한 목록
     */
    private record Placed(ItineraryDraftCommand.PlannedPlace place, Slot slot, List<String> warningCodes) {
    }

    /** 방문 시각을 절대 시각으로 바꿀 때 쓰는 시간대. 판정기와 같은 값이다. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

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
        markTripReady(draft.tripId(), now);

        return new ItineraryHandle(itineraryId, 1);
    }

    /**
     * 일정이 생겼으니 여행을 READY 로 옮긴다 — S15P21E201-964.
     *
     * <p>2026-09-15 까지 {@link Trip#markReady} 는 <b>어디에서도 불리지 않았다.</b> 그래서
     * 모든 여행이 PLANNING 에 머물렀고, 내 여행 목록은 일정이 여러 판 쌓인 여행까지
     * "일정 준비 중" 으로 보여 줬다. 목록만 보고는 일정이 만들어졌는지 알 수 없었다.
     *
     * <p>🔴 PLANNING 일 때만 옮긴다. 여행 중(IN_PROGRESS)인 여행의 일정을 다시 만들 때
     * 무조건 READY 로 쓰면 진행 단계가 뒤로 밀린다. 끝난 여행과 지워진 여행은
     * {@code markReady} 가 예외를 던지므로 그 앞에서 거른다 — 여기서 터지면 일정 저장까지
     * 함께 굴러떨어지고, 그러면 <b>상태 한 칸 때문에 일정 생성이 실패한다.</b>
     *
     * <p>바깥 트랜잭션 안이라(머리말 참고) 일정과 상태가 같이 반영되거나 같이 안 된다.
     */
    private void markTripReady(String tripId, Instant now) {
        this.tripRepository.findById(tripId)
                .filter((trip) -> !trip.isDeleted() && trip.status() == Trip.Status.PLANNING)
                .ifPresent((trip) -> {
                    trip.markReady(now);
                    this.tripRepository.updateStatus(trip);
                });
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
        for (ItineraryDraft.DraftLeg draftLeg : this.legPlanner.buildLegs(trip, placeIdsByDay)) {
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
