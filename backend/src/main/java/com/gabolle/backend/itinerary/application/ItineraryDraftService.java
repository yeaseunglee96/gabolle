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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.gabolle.backend.itinerary.application.port.RouteOrderPort;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.OpeningHoursFilterPort;
import com.gabolle.backend.place.service.PlaceTimeFactFilterPort;
import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryExclusion;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;
import com.gabolle.backend.itinerary.domain.ItineraryChangedByMember;
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
 * 추천이 순위 매긴 장소를 실제 일정(항목·구간)으로 조립하고 저장한다.
 * 클래스에 {@code @Transactional} 을 달지 않는다. {@link #persist} 는
 * {@code RecommendationRecorder.recordWithItinerary} 의 트랜잭션 안에서 불려야 하고, 여기서
 * 새 트랜잭션을 열면 일정 저장과 Job·후보 저장이 나뉘어 "일정만 생기고 Job 은 실패로 남는"
 * 상태가 생길 수 있다.
 */
@Service
@Profile({ "db", "dev" })
public class ItineraryDraftService implements ItineraryDraftPort {

    private final TripRepository tripRepository;

    private final ItineraryRepository itineraryRepository;

    private final Clock clock;

    /**
     * 여행 기분이 정하는 하루 곳 수. 앱이 화면에 적어 둔 약속 그대로다
     * ({@code planOptions.ts} 의 {@code PACE_OPTIONS}) — 「여유롭게 = 하루 2–3곳」,
     * 「균형 있게 = 하루 3–4곳」, 「알차게 = 하루 5곳 이상」.
     * <p>
     * 범위의 <b>위쪽</b>을 고른 이유는, 이 수가 「최대」이고 후보가 모자라면 그보다 적게 들어가기
     * 때문이다. 아래쪽을 고르면 「2–3곳」이라 적어 두고 언제나 2곳만 나온다.
     * <p>
     * 🔴 이 숫자들은 실측이 아니라 <b>화면이 이미 한 약속</b>이다. 화면 문구를 고치면 여기도
     * 같이 고친다 — 두 곳이 어긋나면 사용자에게는 앱이 거짓말한 것이 된다.
     */
    private static final java.util.Map<String, Integer> ITEMS_PER_DAY_BY_PACE = java.util.Map.of(
            "RELAXED", 3,
            "BALANCED", 4,
            "PACKED", 5);

    /** 하루에 배정할 최대 항목 수. 프리셋·설정이 없으면 4. */
    private final int maxItemsPerDay;

    private final int maxFoodPerDay;

    private final String foodCategory;

    /**
     * 자리 수의 몇 배를 후보로 받을까 (S15P21E201-1494).
     *
     * <p>🔴 <b>자리 수만큼만 받으면 고를 여지가 없다.</b> 전부 다 들어가야 하므로, 지역이
     * 안 맞는 곳이 있어도 바꿔 넣을 것이 없다.
     *
     * <p>2026-09-22 운영 사례가 그 자리를 정확히 보여줬다. 2일 × 4곳 = 8자리에
     * {@code defaultTopK} 가 10이라 여벌이 <b>둘</b> 있었는데, 그 9·10위가 <b>둘 다 해운대</b>
     * 였다. 영도는 11위가 처음이라 영도 날을 채울 것이 없었다. 여벌이 아주 없었던 것이
     * 아니라 <b>여벌이 한 지역에 몰려 있었다.</b>
     *
     * <p>1 이면 예전 동작(자리 수만큼)이다. 아래로는 안 내려간다 — 0을 주면 후보가 0이 되어
     * 일정이 통째로 비고, 그 증상은 설정 오타와 구별되지 않는다.
     *
     * <p>🔴 이 값은 <b>공짜가 아니다.</b> 추천 엔진이 그만큼 더 계산하고 응답도 커진다
     * ({@code defaultTopK} 가 이 값을 따라간다). 늘릴 때는 그 비용을 같이 본다.
     */
    private final int candidateHeadroom;

    /**
     * 구간(leg) 계산. 생성과 편집(순서 바꾸기) 두 경로가 같은 규칙을 써야 해서
     * {@link ItineraryLegPlanner} 로 뽑았다.
     */
    private final ItineraryLegPlanner legPlanner;

    /**
     * 그 시각에 문을 여는가. 후보를 고르는 단계가 아니라 자리에 앉히는 단계에서 묻는다 —
     * 후보 조회는 여행 전체에 한 번 부르고 시각 칸은 한 순간이라, 거기에 첫날 아침을 넣으면
     * 화요일 오후에 방문할 곳까지 월요일 아침 기준으로 걸러진다.
     */
    private final OpeningHoursFilterPort openingHours;

    /**
     * 브레이크타임에 걸리는가 · 라스트오더를 지났는가.
     * {@link #openingHours} 와 같은 자리에서 같은 이유로 묻는다 — 항목마다 다른 시각을 물어야 한다.
     */
    private final PlaceTimeFactFilterPort timeFact;

    /**
     * 하루의 차례를 거리로 다시 세우는 문. {@link ItineraryLegPlanner} 가 이동시간 문을 다루는
     * 방식과 같게 {@link ObjectProvider} 로 받는다 — 최적화 어댑터가 없는 판(슬라이스 테스트,
     * 프로필이 안 맞는 기동)에서도 일정 생성은 그대로 돌아야 한다. 없으면 순위 차례로 간다.
     */
    private final ObjectProvider<RouteOrderPort> routeOrder;

    /**
     * 날짜를 가를 때 좌표를 읽는 곳 (S15P21E201-1493).
     *
     * <p>같은 패키지의 {@code ItineraryLegPlanner} 도 저장소를 직접 쓴다 — 좌표 하나 때문에
     * 포트를 새로 뚫지 않는다. {@link RouteOrderPort} 처럼 {@code ObjectProvider} 로 받지
     * 않는 것은, 저장소는 이 서비스가 도는 어떤 판에서도 있기 때문이다.
     */
    private final PlaceRepository placeRepository;

    /**
     * 「일정이 생겼다 · 바뀌었다」를 알리는 자리. 듣는 쪽은 동행자 폰에 알림을 띄우는
     * {@code TripPushNotifier} 하나이고, 커밋이 끝난 뒤에만 받는다 (S15P21E201-1391).
     */
    private final ApplicationEventPublisher events;

    public ItineraryDraftService(TripRepository tripRepository, ItineraryRepository itineraryRepository, Clock clock,
            @Value("${gabolle.itinerary.max-items-per-day:4}") int maxItemsPerDay,
            @Value("${gabolle.itinerary.max-food-per-day:3}") int maxFoodPerDay,
            @Value("${gabolle.itinerary.food-category:FOOD}") String foodCategory,
            @Value("${gabolle.itinerary.candidate-headroom:3}") int candidateHeadroom,
            ItineraryLegPlanner legPlanner, OpeningHoursFilterPort openingHours,
            PlaceTimeFactFilterPort timeFact, ObjectProvider<RouteOrderPort> routeOrder,
            PlaceRepository placeRepository, ApplicationEventPublisher events) {
        this.placeRepository = placeRepository;
        this.tripRepository = tripRepository;
        this.itineraryRepository = itineraryRepository;
        this.clock = clock;
        this.maxItemsPerDay = maxItemsPerDay;
        this.maxFoodPerDay = maxFoodPerDay;
        this.foodCategory = foodCategory;
        this.candidateHeadroom = Math.max(1, candidateHeadroom);
        this.legPlanner = legPlanner;
        this.openingHours = openingHours;
        this.timeFact = timeFact;
        this.routeOrder = routeOrder;
        this.events = events;
    }

    /**
     * 날 수 × 하루 항목 수. 하루 몇 곳인지를 정하는 규칙({@link #itemsPerDay})이 여기 있으므로
     * 이 계산도 여기 있다 — 부르는 쪽이 자기 상수로 어림하면 둘이 어긋난다.
     *
     * <p>밥집 상한은 <b>더하지 않는다.</b> 상한은 「그중 몇 곳까지 밥집이어도 되나」이지 자리를
     * 늘리는 값이 아니다. 필요한 것은 자리 수이고, 상한에 걸려 밀린 밥집 대신 앉을 것이
     * 후보에 있어야 한다는 뜻이다.
     *
     * <p>🔴 S15P21E201-1494 — 자리 수에 {@link #candidateHeadroom} 을 곱한다. 자리 수만큼만
     * 받으면 전부 다 들어가야 해서 <b>고를 여지가 없고</b>, 지역이 안 맞는 곳이 있어도 바꿔
     * 넣을 것이 없다. 배정이 지역을 보게 한 것({@code S15P21E201-1493})은 <b>고를 것이
     * 있을 때만</b> 뜻이 있다.
     */
    @Override
    public int placesNeeded(String tripId) {
        return this.tripRepository.findById(tripId)
                .map((trip) -> Math.max(1, trip.days() * itemsPerDay(trip) * this.candidateHeadroom))
                .orElse(1);
    }

    /**
     * 순수 계산 + 읽기만 — DB 에 아무것도 쓰지 않는다. 부르는 쪽이 트랜잭션 밖에서 부른다.
     */
    @Override
    public ItineraryDraft assemble(ItineraryDraftCommand command) {
        if (command.places().isEmpty()) {
            // returnedCount == 0 은 부르는 쪽이 이미 NO_FEASIBLE_RESULT 로 걸러 준다 — 여기 오면 그 방어선이 뚫린 것이다.
            throw new IllegalStateException("일정으로 조립할 장소가 없다: requestId=" + command.requestId());
        }

        Trip trip = this.tripRepository.findById(command.tripId())
                .orElseThrow(() -> new IllegalStateException("여행을 찾을 수 없다: " + command.tripId()));

        int days = trip.days();
        Distribution distribution = distributeByDay(command.places(), days, mealsPerDay(trip),
                itemsPerDay(trip));
        List<List<ItineraryDraftCommand.PlannedPlace>> byDay = distribution.byDay();

        // 구간을 만들 때 필요한, 날짜별 "그 날 다녀올 장소" 원본 순서.
        List<List<UUID>> placeIdsByDay = new ArrayList<>();
        List<List<Placed>> placedByDay = new ArrayList<>();

        for (int dayIndex = 0; dayIndex < byDay.size(); dayIndex++) {
            List<ItineraryDraftCommand.PlannedPlace> dayPlaces = byDay.get(dayIndex);
            LocalDate visitDate = trip.startDate().plusDays(dayIndex);

            // 자리에 앉히기 전에 차례를 거리로 다시 세운다. 앉히는 규칙(영업시간·밥 때)은 그대로
            // 두고 훑는 차례만 바꾼다 — placeIntoSlots 은 목록을 앞에서부터 보므로, 목록의 차례가
            // 곧 "같은 조건이면 이쪽 먼저" 가 된다.
            dayPlaces = reorderByRoute(trip, dayPlaces);

            List<Placed> placedToday = placeIntoSlots(trip, dayPlaces, visitDate);
            placedByDay.add(placedToday);

            List<UUID> placeIdsToday = new ArrayList<>(placedToday.size());
            for (Placed placed : placedToday) {
                placeIdsToday.add(placed.place().placeId());
            }
            placeIdsByDay.add(placeIdsToday);
        }

        List<ItineraryDraft.DraftLeg> legs = this.legPlanner.buildLegs(trip, placeIdsByDay);

        // 시각은 구간을 만든 뒤에 깐다. 순서가 정해져야 이동 시간을 알 수 있고, 이동 시간을
        // 알아야 시각을 깔 수 있다. 순서를 정하면서 시각까지 같이 정하면 그 시점에는 구간이
        // 없어서 이동 시간이 0인 것처럼 깔리고, 「예상 도착」이 장소를 옮길 때마다 밀린다.
        // placeIntoSlots 이 정한 순서는 그대로 쓰고 여기서는 시각만 다시 깐다.
        List<ItineraryDraft.DraftItem> items = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < placedByDay.size(); dayIndex++) {
            List<Placed> placedToday = placedByDay.get(dayIndex);
            LocalDate visitDate = trip.startDate().plusDays(dayIndex);
            List<Slot> timed = layoutDay(trip, placedToday.size(), travelMinutesFor(legs, dayIndex, placedToday.size()));

            for (int i = 0; i < placedToday.size(); i++) {
                Placed placed = placedToday.get(i);
                Slot slot = timed.get(i);
                items.add(new ItineraryDraft.DraftItem(
                        dayIndex, visitDate, i + 1, placed.place().placeId(),
                        UUID.randomUUID(), slot.start(), slot.end(), slot.stayMinutes(),
                        slot.dataStatus(), placed.place().reasonCodes(), placed.warningCodes()));
            }
        }

        // 경고는 서로 독립이라 같이 나올 수 있다 — 하나를 고르지 않는다.
        List<String> draftWarnings = new ArrayList<>(2);
        if (distribution.sightSlotUnfilled()) {
            draftWarnings.add(ItineraryWarningCodes.SIGHT_SLOT_UNFILLED);
        }
        if (distribution.regionMixed()) {
            draftWarnings.add(ItineraryWarningCodes.DAY_REGION_MIXED);
        }

        return new ItineraryDraft(command.tripId(), command.userId(), command.requestId(),
                command.modelVersion(), command.featureVersion(), command.ontologyVersion(),
                command.policyVersion(), command.datasetVersion(), items, legs, draftWarnings);
    }

    /**
     * 그 날의 장소를 <b>이동이 가장 적은 차례</b>로 다시 세운다.
     * <p>
     * 여기까지 오는 차례는 추천 <b>순위</b>다. 순위는 "얼마나 잘 맞는가" 이지 "어디에 있는가" 가
     * 아니라서, 순위 그대로 훑으면 도시 반대편을 오갈 수 있다. 그것이 일정이 가게 나열처럼
     * 보이는 이유였다 — 차례를 정하는 단계가 거리를 한 번도 안 봤다.
     * <p>
     * 🔴 <b>순위를 버리는 것이 아니다.</b> 바뀌는 것은 {@link #placeIntoSlots} 이 후보를 훑는
     * 차례뿐이고, 영업시간·밥 때 판정은 그대로 남는다. 그리고 답이 없거나 받은 것과 한 톨이라도
     * 어긋나면 <b>들어온 차례를 그대로 돌려준다</b> — 최적화가 없어도 일정은 오늘처럼 나온다.
     */
    private List<ItineraryDraftCommand.PlannedPlace> reorderByRoute(Trip trip,
            List<ItineraryDraftCommand.PlannedPlace> dayPlaces) {

        RouteOrderPort port = this.routeOrder.getIfAvailable();
        if (port == null || dayPlaces.size() < 2) {
            return dayPlaces;
        }

        List<UUID> placeIds = new ArrayList<>(dayPlaces.size());
        for (ItineraryDraftCommand.PlannedPlace place : dayPlaces) {
            placeIds.add(place.placeId());
        }

        // 여행이 고른 이동수단의 첫 값. ItineraryLegPlanner 와 같은 규칙이라야 차례를 정한
        // 잣대와 구간을 잰 잣대가 같아진다.
        String[] modes = trip.travelModes();
        String travelMode = (modes == null || modes.length == 0) ? "WALK" : modes[0];

        List<UUID> ordered = port.shortestOrder(new RouteOrderPort.RouteOrderRequest(
                trip.originLat(), trip.originLng(), List.copyOf(placeIds), travelMode));
        if (ordered == null || ordered.size() != dayPlaces.size()) {
            return dayPlaces;
        }

        // 같은 장소가 두 번 들어와도 어긋나지 않게 꺼내 쓴다.
        Map<UUID, List<ItineraryDraftCommand.PlannedPlace>> byId = new HashMap<>();
        for (ItineraryDraftCommand.PlannedPlace place : dayPlaces) {
            byId.computeIfAbsent(place.placeId(), key -> new ArrayList<>()).add(place);
        }

        List<ItineraryDraftCommand.PlannedPlace> reordered = new ArrayList<>(dayPlaces.size());
        for (UUID placeId : ordered) {
            List<ItineraryDraftCommand.PlannedPlace> waiting = byId.get(placeId);
            if (waiting == null || waiting.isEmpty()) {
                // 받은 적 없는 장소가 왔거나 같은 것이 너무 많이 왔다. 통째로 버린다.
                return dayPlaces;
            }
            reordered.add(waiting.remove(waiting.size() - 1));
        }
        return reordered;
    }

    /**
     * 순위대로 날짜에 배분한다. 하루가 {@code itemsPerDay} 를 채우면 다음 날로 넘기고,
     * 모든 날이 다 차면 남은 후보는 일정에 넣지 않는다.
     * 넘치는 것을 마지막 날에 쌓지 않는다 — 하루에 열 곳은 일정이 아니고, 그렇게 쌓인 날은
     * 이동 시간도 머무는 시간도 계산이 안 맞는다. 1일 여행이면 넘길 날이 아예 없어 후보가
     * 통째로 하루에 들어간다.
     * 추천 결과를 줄이는 것이 아니다. 순위표는 그대로 다 남아서 대체 장소 제시와 재계산이
     * 쓴다({@code ItineraryRevisionCommand.rankedPool}). 여기서 정하는 것은 일정에 실제로
     * 놓는 수뿐이다.
     * 첫 배분에서는 밥집을 하루 {@link #maxFoodPerDay} 곳까지만 앉히고 나머지 자리를 명소로
     * 채운다 — 후보의 대부분이 음식점이라 순위대로만 담으면 하루가 전부 밥집이 된다.
     */
    private Distribution distributeByDay(
            List<ItineraryDraftCommand.PlannedPlace> places, int days, int mealsPerDay, int itemsPerDay) {

        List<List<ItineraryDraftCommand.PlannedPlace>> byDay = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            byDay.add(new ArrayList<>());
        }

        int[] foodPerDay = new int[days];

        // S15P21E201-1493 — 좌표를 미리 한 번에 읽는다. 없으면(저장소가 못 주면) 아래 배정은
        // 예전처럼 "자리 있는 첫 날" 로 떨어진다 — 좌표가 없다고 일정 생성이 멈추면 안 된다.
        Map<UUID, double[]> coords = coordinatesOf(places);

        // 하루가 한 지역이 되게 날마다 중심을 잡는다. 순위 1위가 첫 날의 중심이 되고, 그
        // 다음 중심은 이미 잡힌 중심들에서 가장 먼 곳이다 — 그래야 날끼리 겹치지 않는다.
        double[][] dayAnchor = seedDayAnchors(places, coords, days);

        // 🔴 S15P21E201-1494 — **두 번 훑는다.** 한 번만 훑으면서 순위대로 무조건 앉히면,
        //    앞쪽 후보가 자리를 다 채워서 **뒤에 있는 「지역이 맞는 후보」의 차례가 안 온다.**
        //
        //    운영 사례가 그랬다. 8자리를 순위 1~8이 다 채우는데 그 8번째가 해운대라
        //    영도 날에 끼었고, 정작 영도인 11위는 앉을 자리가 없었다. 후보를 더 받아도
        //    (candidateHeadroom) 쓰이질 않으니 아무것도 안 바뀐다.
        //
        //    그래서 첫 훑기는 **지역이 맞는 것만** 앉힌다. 위 8번째는 여기서 건너뛰어지고,
        //    11위가 영도 날을 채운다. 남은 자리는 두 번째 훑기가 순위대로 메운다.
        List<ItineraryDraftCommand.PlannedPlace> deferred = new ArrayList<>();
        for (ItineraryDraftCommand.PlannedPlace place : places) {
            if (!seat(byDay, foodPerDay, place, mealsPerDay, itemsPerDay, coords, dayAnchor, true)) {
                deferred.add(place);
            }
        }

        // 미뤄 둔 밥집으로 빈 자리를 메우지 않는다. 끼니 상한을 무시하고 메우면 명소 데이터가
        // 모자란 지역에서 하루가 통째로 음식점이 되고, 데이터가 모자라다는 사실이 아무 데도
        // 안 보인다. 사용자에게는 "이 앱은 밥집만 추천한다" 로 보이고 팀에게는 신호가 안 온다.
        // 그래서 비워 두고 말한다.
        int rejectedFood = 0;
        for (ItineraryDraftCommand.PlannedPlace place : deferred) {
            if (!seat(byDay, foodPerDay, place, mealsPerDay, itemsPerDay, coords, dayAnchor, false)
                    && isFood(place)) {
                rejectedFood++;
            }
        }

        // 자리는 남았는데 앉힐 것이 밥집밖에 없었던 경우에만 경고한다. 하루가 꽉 차서
        // 밥집이 밀린 것은 정상이고, 그건 빈 자리를 만들지 않는다.
        boolean roomLeft = byDay.stream().anyMatch(day -> day.size() < itemsPerDay);
        return new Distribution(byDay, roomLeft && rejectedFood > 0, regionMixed(byDay, coords));
    }

    /** 날짜별 배분 결과와, 명소가 모자라 빈 자리가 남았는지, 하루에 먼 곳이 섞였는지. */
    private record Distribution(List<List<ItineraryDraftCommand.PlannedPlace>> byDay, boolean sightSlotUnfilled,
            boolean regionMixed) { }

    /**
     * 고정된 식사 시각대 — 이 시간에 사람은 밥을 먹는다.
     * 개수가 아니라 시각이 정하게 한다. "하루에 밥집 최대 3곳" 같은 개수 상한은 하루가 몇
     * 시간이든 똑같아서, 09~18시 여행과 07~22시 여행이 같은 값을 받는다.
     */
    private static final LocalTime[][] MEAL_BANDS = {
            { LocalTime.of(7, 0), LocalTime.of(9, 30) },    // 아침
            { LocalTime.of(11, 30), LocalTime.of(14, 0) },  // 점심
            { LocalTime.of(17, 0), LocalTime.of(20, 0) },   // 저녁
    };

    /** 식사 시간대가 활동 시간과 이만큼은 겹쳐야 "그 끼니를 먹는 여행" 으로 본다. */
    private static final long MEAL_OVERLAP_MINUTES = 60;

    /**
     * 그 여행의 하루에 끼니가 몇 번 들어가나 — 활동 시간대와 겹치는 식사 시간대의 수.
     * 09:00~18:00 이면 점심(150분 겹침)과 저녁(60분 겹침)으로 2다. 아침은 30분만 겹쳐서 안 센다.
     * 활동 시간대를 안 정한 여행은 2를 준다 — 모름을 0으로 두면 밥집이 한 곳도 안 들어간다.
     */
    /**
     * 그 여행의 하루에 몇 곳을 넣을까 — 사용자가 고른 「여행 기분」이 정한다.
     * <p>
     * 안 고른 여행은 설정 기본값({@code gabolle.itinerary.max-items-per-day})을 그대로 쓴다.
     * {@code null} 을 「보통」으로 바꾸지 않는다 — 안 고른 것과 「균형 있게」를 고른 것은 다른
     * 사실이고, 기본값은 운영이 조정할 수 있는 손잡이라 임의로 4 에 묶으면 그 손잡이가 죽는다.
     * <p>
     * 모르는 값이 와도 기본값으로 떨어진다. {@link Trip} 생성자가 이미 아는 값만 통과시키므로
     * 여기까지 오지 않지만, 저장된 옛 행이 어긋났을 때 일정 생성이 멈추지는 않아야 한다.
     */
    private int itemsPerDay(Trip trip) {
        if (trip.pace() == null) {
            return this.maxItemsPerDay;
        }
        return ITEMS_PER_DAY_BY_PACE.getOrDefault(trip.pace(), this.maxItemsPerDay);
    }

    private int mealsPerDay(Trip trip) {
        LocalTime start = trip.timeWindowStart();
        LocalTime end = trip.timeWindowEnd();
        if (start == null || end == null || !end.isAfter(start)) {
            return Math.min(2, this.maxFoodPerDay);
        }
        int meals = 0;
        for (LocalTime[] band : MEAL_BANDS) {
            LocalTime from = band[0].isAfter(start) ? band[0] : start;
            LocalTime to = band[1].isBefore(end) ? band[1] : end;
            if (to.isAfter(from) && Duration.between(from, to).toMinutes() >= MEAL_OVERLAP_MINUTES) {
                meals++;
            }
        }
        // 설정 상한을 넘지 않는다. 그 값은 "최대 이만큼" 이지 "언제나 이만큼" 이 아니다.
        return Math.min(meals, this.maxFoodPerDay);
    }

    /**
     * 자리를 찾아 앉힌다. 앉혔으면 {@code true}.
     *
     * <p>🔴 S15P21E201-1493 — 예전에는 <b>자리 있는 첫 날</b>에 앉혔다. 그러면 순위 상위
     * {@code itemsPerDay} 개가 통째로 1일차가 되고, 그때 <b>좌표를 한 번도 안 본다.</b>
     * 2026-09-22 운영에서 영도 세 곳 사이에 해운대 한 곳이 껴서 16km 를 갔다 돌아왔다
     * (그 두 구간만 왕복 104분).
     *
     * <p>지금은 <b>자리가 있는 날 중 중심이 가장 가까운 날</b>에 앉힌다. 순위 차례로 앉히는
     * 것은 그대로라 순위가 높을수록 원하는 날을 먼저 고른다 — 순위가 자리를 정하고 지역이
     * 날을 정한다.
     *
     * <p>좌표가 없으면 예전 동작(자리 있는 첫 날)으로 떨어진다. 좌표를 못 구했다고 일정
     * 생성이 멈추면 안 된다.
     *
     * @param regionOnly 참이면 <b>지역이 맞는 자리만</b> 받는다. 맞는 날이 없으면 앉히지 않고
     *     {@code false} 를 돌려준다 — 부르는 쪽이 미뤄 뒀다가 두 번째 훑기에서 다시 준다.
     *     이것이 없으면 앞쪽 후보가 자리를 다 채워 뒤의 「지역이 맞는 후보」가 차례를 못 얻는다
     *     (S15P21E201-1494)
     */
    private boolean seat(List<List<ItineraryDraftCommand.PlannedPlace>> byDay, int[] foodPerDay,
            ItineraryDraftCommand.PlannedPlace place, int mealsPerDay, int itemsPerDay,
            Map<UUID, double[]> coords, double[][] dayAnchor, boolean regionOnly) {

        boolean food = isFood(place);
        double[] here = coords.get(place.placeId());

        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int day = 0; day < byDay.size(); day++) {
            if (byDay.get(day).size() >= itemsPerDay) {
                continue;
            }
            if (food && foodPerDay[day] >= mealsPerDay) {
                continue;
            }
            if (here == null || dayAnchor[day] == null) {
                // 좌표를 모르는 자리가 하나라도 있으면 거리로 고를 수 없다. 예전처럼 첫 날.
                // 첫 훑기에서는 그냥 미룬다 — 모르는 것을 「지역이 맞다」로 치지 않는다.
                if (regionOnly) {
                    return false;
                }
                best = day;
                break;
            }
            double distance = haversineKm(here, dayAnchor[day]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = day;
            }
        }
        if (best < 0) {
            return false;
        }
        if (regionOnly && bestDistance > REGION_MIXED_KM) {
            // 자리는 있지만 그 날의 지역이 아니다. 지금 앉히면 뒤에 오는 「그 지역 후보」가
            // 자리를 못 얻는다. 미뤄 두고 두 번째 훑기에 맡긴다.
            return false;
        }
        byDay.get(best).add(place);
        if (food) {
            foodPerDay[best]++;
        }
        return true;
    }

    // ── 지역으로 날을 가르는 부분 (S15P21E201-1493) ──────────────────────────

    /**
     * 하루 안에서 중심에서 이만큼 넘게 떨어진 곳이 있으면 「먼 곳이 섞였다」고 본다.
     *
     * <p>영도~해운대가 약 17km 다. 같은 동네 안의 흩어짐(2~3km)과는 자릿수가 다르므로 그
     * 사이 어딘가면 된다. 8km 는 그 자리다 — 부산 안에서 「지하철을 타야 하는 거리」쯤이다.
     *
     * <p>🔴 이 값은 실측이 아니라 <b>정한 값</b>이다. 실제 일정들의 분포를 재 보고 조정할
     * 값어치가 있다.
     */
    private static final double REGION_MIXED_KM = 8.0;

    /** 좌표를 한 번에 읽는다. 저장소가 못 주는 것은 그냥 빠진다 — 그 자리는 거리 비교를 건너뛴다. */
    private Map<UUID, double[]> coordinatesOf(List<ItineraryDraftCommand.PlannedPlace> places) {
        List<UUID> ids = new ArrayList<>(places.size());
        for (ItineraryDraftCommand.PlannedPlace place : places) {
            ids.add(place.placeId());
        }
        Map<UUID, double[]> coords = new HashMap<>();
        if (ids.isEmpty()) {
            return coords;
        }
        for (Place place : this.placeRepository.findByPlaceIdIn(ids)) {
            if (place.getLat() != null && place.getLng() != null) {
                coords.put(place.getPlaceId(), new double[] { place.getLat(), place.getLng() });
            }
        }
        return coords;
    }

    /**
     * 날마다 중심을 하나씩 잡는다.
     *
     * <p>첫 날의 중심은 <b>순위 1위</b>가 있는 곳이다 — 가장 좋은 곳이 속한 지역이 첫 날이
     * 된다. 그 다음 중심은 <b>이미 잡힌 중심들에서 가장 먼</b> 후보다. 가까운 곳을 또 고르면
     * 두 날의 중심이 붙어 버려 지역이 안 갈린다.
     *
     * <p>좌표를 아는 후보가 날 수보다 적으면 남은 날은 {@code null} 로 둔다 — 그런 날은
     * 거리 비교에서 빠지고 예전처럼 순서대로 채워진다.
     */
    private static double[][] seedDayAnchors(List<ItineraryDraftCommand.PlannedPlace> places,
            Map<UUID, double[]> coords, int days) {

        double[][] anchors = new double[days][];
        List<double[]> known = new ArrayList<>();
        for (ItineraryDraftCommand.PlannedPlace place : places) {
            double[] at = coords.get(place.placeId());
            if (at != null) {
                known.add(at);
            }
        }
        if (known.isEmpty()) {
            return anchors;
        }

        anchors[0] = known.get(0);
        for (int day = 1; day < days; day++) {
            double[] farthest = null;
            double farthestDistance = -1;
            for (double[] candidate : known) {
                double nearest = Double.MAX_VALUE;
                for (int taken = 0; taken < day; taken++) {
                    nearest = Math.min(nearest, haversineKm(candidate, anchors[taken]));
                }
                if (nearest > farthestDistance) {
                    farthestDistance = nearest;
                    farthest = candidate;
                }
            }
            anchors[day] = farthest;
        }
        return anchors;
    }

    /**
     * 하루 안에 멀리 떨어진 곳이 섞였나. 하나라도 있으면 참이다.
     *
     * <p>바꿔 넣지 않고 <b>말하기만</b> 한다. 억지로 바꾸면 순위가 한참 낮은 곳을 넣게 되고,
     * 그건 이동을 줄이려고 추천 품질을 버리는 맞바꿈이다 — 실측으로도 영도권 후보는 28곳뿐이고
     * 그중 20곳이 음식점이라, 지역을 맞추려 내려가다 보면 하루가 밥집이 된다.
     */
    private static boolean regionMixed(List<List<ItineraryDraftCommand.PlannedPlace>> byDay,
            Map<UUID, double[]> coords) {

        for (List<ItineraryDraftCommand.PlannedPlace> day : byDay) {
            List<double[]> here = new ArrayList<>();
            for (ItineraryDraftCommand.PlannedPlace place : day) {
                double[] at = coords.get(place.placeId());
                if (at != null) {
                    here.add(at);
                }
            }
            if (here.size() < 2) {
                continue;
            }
            for (int i = 0; i < here.size(); i++) {
                for (int j = i + 1; j < here.size(); j++) {
                    if (haversineKm(here.get(i), here.get(j)) > REGION_MIXED_KM) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** 두 점 사이 거리(km). 지구를 공으로 본 어림이고, 부산 안에서는 오차가 무시할 만하다. */
    private static double haversineKm(double[] a, double[] b) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(b[0] - a[0]);
        double dLng = Math.toRadians(b[1] - a[1]);
        double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a[0])) * Math.cos(Math.toRadians(b[0]))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * earthRadiusKm * Math.asin(Math.min(1.0, Math.sqrt(s)));
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
     * 그 날의 장소를 시간 칸에 앉힌다.
     * 칸을 앞에서부터 채우면서, 그 시각에 닫는다고 원천이 말한 장소는 그 자리에 놓지 않고 다음
     * 후보를 본다. 후보는 순위 순으로 훑으므로 걸리는 것이 없으면 순위가 그대로 유지된다.
     * 모름을 닫힘처럼 다루지 않는다 — 영업시간이 들어간 장소가 아직 일부뿐이라, 모름을 닫힘으로
     * 보면 나머지가 일정에서 통째로 빠지고 사용자에게는 "갈 데가 없다" 로 보인다.
     * 남은 후보가 전부 그 시각에 닫혀 있으면 순위 그대로 앉히고 그 항목의 경고에
     * {@code OPENING_HOURS_CLOSED} 를 더한다. 빈 자리를 남기면 사용자가 그날 무엇을 할지 알 수
     * 없고, 조용히 앉히면 화면이 그것을 "확인했고 문제 없음" 으로 읽는다.
     * 여행이 활동 시간대를 안 정했으면 칸에 시각이 없다. 시각이 없으면 물어볼 수가 없으므로
     * 순위 그대로 앉힌다.
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

            // 이 칸이 밥 먹는 시각이면 밥집을, 아니면 밥집이 아닌 곳을 먼저 찾는다.
            // 같은 조건이면 순위가 높은 쪽이 먼저다.
            boolean wantFood = overlapsMealBand(slot);

            int chosen = -1;
            if (at != null) {
                chosen = firstOpen(dayPlaces, used, at, wantFood);
                if (chosen < 0) {
                    // 원하는 종류가 없다. 종류를 포기하고 영업시간만 본다 — 자리를 비우는 것보다는 낫다.
                    // "밥 때인데 밥집이 없다" 는 사실은 이미 distributeByDay 가 SIGHT_SLOT_UNFILLED 로 말한다.
                    chosen = firstOpen(dayPlaces, used, at, !wantFood);
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
            String violation = (forced && at != null) ? violationAt(place.placeId(), at) : null;
            if (violation != null) {
                warnings = new ArrayList<>(warnings == null ? List.of() : warnings);
                warnings.add(violation);
            }
            placed.add(new Placed(place, slot, warnings));
        }
        return placed;
    }

    /** 그 시각에 문을 연 후보 중, 원하는 종류의 첫 번째. 없으면 {@code -1}. */
    private int firstOpen(List<ItineraryDraftCommand.PlannedPlace> dayPlaces, boolean[] used,
            OffsetDateTime at, boolean food) {

        for (int i = 0; i < dayPlaces.size(); i++) {
            if (used[i] || isFood(dayPlaces.get(i)) != food) {
                continue;
            }
            if (violationAt(dayPlaces.get(i).placeId(), at) == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 이 칸이 밥 먹는 시각인가 — 식사 시각대와 {@link #MEAL_OVERLAP_MINUTES} 이상 겹치면.
     * 스치기만 한 것은 안 센다. 09:00~18:00 · 하루 4곳이면 첫 칸 09:00~11:15 가 아침에 30분
     * 걸리는데, 겹치기만 하면 센다는 규칙이면 오전 첫 자리가 밥집이 된다.
     * {@link #mealsPerDay} 도 같은 30분을 안 세므로, 같은 잣대를 써야 칸 수와 끼니 수가 맞는다.
     * 칸 자체가 60분보다 짧으면 그 길이를 기준으로 삼는다 — 안 그러면 짧은 칸은 통째로 점심
     * 안에 들어가 있어도 영영 밥 때가 아니게 된다.
     */
    private static boolean overlapsMealBand(Slot slot) {
        if (slot.start() == null || slot.end() == null) {
            return false;
        }
        long required = Math.min(MEAL_OVERLAP_MINUTES, Duration.between(slot.start(), slot.end()).toMinutes());
        for (LocalTime[] band : MEAL_BANDS) {
            LocalTime from = band[0].isAfter(slot.start()) ? band[0] : slot.start();
            LocalTime to = band[1].isBefore(slot.end()) ? band[1] : slot.end();
            if (to.isAfter(from) && Duration.between(from, to).toMinutes() >= required) {
                return true;
            }
        }
        return false;
    }

    /**
     * 그 시각에 그 장소가 걸리는 것이 있는가 — 있으면 경고 코드, 없으면 {@code null}.
     * 영업시간 · 브레이크타임 · 라스트오더 셋을 이 순서로 본다. 셋 다 "모른다" 를 "문제 없음" 으로
     * 접지 않는다 — {@link OpeningHoursFilterPort.Answer#CLOSED} 일 때만 걸린다.
     */
    private String violationAt(UUID placeId, OffsetDateTime at) {
        if (this.openingHours.openAt(placeId, at) == OpeningHoursFilterPort.Answer.CLOSED) {
            return ItineraryOpeningHoursChecker.VIOLATION_CLOSED;
        }
        if (this.timeFact.breakTimeAt(placeId, at) == OpeningHoursFilterPort.Answer.CLOSED) {
            return ItineraryOpeningHoursChecker.VIOLATION_BREAK_TIME;
        }
        if (this.timeFact.lastOrderAt(placeId, at) == OpeningHoursFilterPort.Answer.CLOSED) {
            return ItineraryOpeningHoursChecker.VIOLATION_LAST_ORDER;
        }
        return null;
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
     * 그 날 i번째 장소에 도착하기까지의 이동 시간(분)을 순서대로 뽑는다.
     * {@code ItineraryLegPlanner.buildLegs} 는 항목 하나에 구간 하나를 만들고, {@code sequence = i + 1}
     * 인 구간이 "i번째 장소로 가는 길" 이다. 첫 구간의 출발점은 여행의 출발 좌표라 첫 이동도
     * 빼놓지 않는다 — 숙소에서 첫 장소까지를 0으로 두면 아침부터 이미 밀린다.
     * 소요가 {@code null} 인 구간은 0으로 친다. 모르는 것을 지어내지 않는다.
     */
    private static List<Integer> travelMinutesFor(List<ItineraryDraft.DraftLeg> legs, int dayIndex, int countToday) {
        List<Integer> minutes = new ArrayList<>(countToday);
        for (int i = 0; i < countToday; i++) {
            minutes.add(0);
        }
        for (ItineraryDraft.DraftLeg leg : legs) {
            if (leg.dayIndex() != dayIndex || leg.durationMin() == null) {
                continue;
            }
            int index = leg.sequence() - 1;
            if (index >= 0 && index < countToday) {
                minutes.set(index, Math.max(0, leg.durationMin()));
            }
        }
        return minutes;
    }

    /**
     * 하루의 시각표를 깐다 — 이동 시간을 빼고 남은 만큼만 머문다.
     * 머무는 시간은 (활동 시간대 - 그 날 이동 시간 합) / 그 날 항목 수이고, i번째 시작은
     * 앞 항목의 끝에 i번째로 가는 이동 시간을 더한 값이다. 마지막 항목의 끝이 활동 시간대의
     * 끝을 넘지 않는다.
     * 이동만으로 하루가 다 차면 시각을 아예 안 준다({@link Slot#unknown()}). 이동을 무시하고
     * 나누면 되지도 않는 일정을 그럴듯하게 그리는 것이고, 그건 시각이 없는 것보다 나쁘다.
     * {@link #slotFor} 와 달리 하루치를 한 번에 낸다 — 앞 항목의 끝을 알아야 다음 시작을 정할
     * 수 있어서 항목 하나만 따로 계산할 수가 없다.
     */
    private static List<Slot> layoutDay(Trip trip, int countToday, List<Integer> travelMinutes) {
        List<Slot> slots = new ArrayList<>(countToday);
        LocalTime windowStart = trip.timeWindowStart();
        LocalTime windowEnd = trip.timeWindowEnd();
        if (windowStart == null || windowEnd == null || !windowEnd.isAfter(windowStart) || countToday <= 0) {
            for (int i = 0; i < countToday; i++) {
                slots.add(Slot.unknown());
            }
            return slots;
        }

        long windowMinutes = Duration.between(windowStart, windowEnd).toMinutes();
        long travelTotal = 0;
        for (Integer minutes : travelMinutes) {
            travelTotal += (minutes == null) ? 0 : minutes;
        }

        long stayMinutes = (windowMinutes - travelTotal) / countToday;
        if (stayMinutes < 1) {
            for (int i = 0; i < countToday; i++) {
                slots.add(Slot.unknown());
            }
            return slots;
        }

        LocalTime cursor = windowStart;
        for (int i = 0; i < countToday; i++) {
            Integer move = (i < travelMinutes.size()) ? travelMinutes.get(i) : null;
            cursor = cursor.plusMinutes(move == null ? 0 : move);
            LocalTime start = cursor;
            LocalTime end = start.plusMinutes(stayMinutes);
            slots.add(new Slot(start, end, (int) stayMinutes, "ESTIMATED"));
            cursor = end;
        }
        return slots;
    }

    // 여기서 나온 시각은 화면에 나가지 않는다 — 순서를 정할 때 "이 자리쯤에서 문이 열려
    // 있나" 를 물어보기 위한 임시 눈금일 뿐이다. 실제로 항목에 박히는 시각은 구간을 만든 뒤
    // layoutDay 가 이동 시간까지 넣어 다시 깐다. 둘을 헷갈리면 이동 시간이 0인 시각표로 돌아간다.
    // 어느 쪽이든 등급은 ESTIMATED 다 — 영업시간이나 실제 이동 소요를 본 값이 아니라
    // 활동 시간대를 항목 수로 나눈 것뿐이라, VERIFIED 로 적으면 화면이 둘을 구분할 수 없다.
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
     * 바깥 트랜잭션 안에서 불린다.
     * 여기서 순서를 뒤집지 않는다 — 일정(itineraries·itinerary_versions·항목·구간)을 먼저 만들어야
     * 그 뒤에 Job 에 {@code itinerary_id} 를 붙이는 것이 의미가 있다.
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
                requestIdString, draft.warningCodes());

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
                    // 🔴 요금은 여기서 예전부터 안 넘어간다 — draftLeg.fareKrw() 가 있는데도
                    //    null 이 들어간다. S15P21E201-1251 의 일이 아니라 따로 고칠 것이라
                    //    이번에는 동작을 그대로 두고 눈에만 보이게 적어 둔다.
                    draftLeg.dataStatus(), null, draftLeg.path(), now));
        }

        // 판과 내용을 한 번에 넘긴다. 판을 먼저 만들고 내용을 나중에 넣으면 그 두 걸음 사이가
        // "판은 있는데 내용이 없는" 상태다.
        this.itineraryRepository.create(itinerary, firstVersion, items, legs);
        markTripReady(draft.tripId(), now);

        // 🔴 «이 알림이 이 기능의 이유다.» 일정 만들기는 오래 걸려서 사람이 앱을 닫고 기다린다.
        //    다 됐다는 것을 폰이 알려 주지 않으면, 사람은 몇 분마다 앱을 열어 확인하거나 잊는다.
        //    그래서 CREATE 만은 «만든 본인에게도» 간다 (TripPushNotifier.onItineraryChanged).
        this.events.publishEvent(new ItineraryChangedByMember(
                itineraryId, 1, ItineraryVersion.Operation.CREATE, draft.userId()));

        return new ItineraryHandle(itineraryId, 1);
    }

    /**
     * 일정이 생겼으니 여행을 READY 로 옮긴다.
     * PLANNING 일 때만 옮긴다 — 여행 중(IN_PROGRESS)인 여행의 일정을 다시 만들 때 무조건 READY 로
     * 쓰면 진행 단계가 뒤로 밀린다. 끝난 여행과 지워진 여행은 {@code markReady} 가 예외를 던지므로
     * 그 앞에서 거른다. 여기서 터지면 일정 저장까지 함께 굴러떨어져 상태 한 칸 때문에 일정 생성이
     * 실패한다.
     * 바깥 트랜잭션 안이라 일정과 상태가 같이 반영되거나 같이 안 된다.
     */
    private void markTripReady(String tripId, Instant now) {
        this.tripRepository.findById(tripId)
                .filter((trip) -> !trip.isDeleted() && trip.status() == Trip.Status.PLANNING)
                .ifPresent((trip) -> {
                    trip.markReady(now);
                    this.tripRepository.updateStatus(trip);
                });
    }

    // 있는 판의 하루만 다시 채운다

    /**
     * {@link #revise} 가 만들고 {@link #publish} 가 받는 초안. 추천 계층에는
     * {@link ItineraryRevisionDraft} 라는 겉면만 보인다.
     * {@code newVersionId} 를 여기서 미리 정하는 이유는 항목·구간·제외 행의 부모 키가 그 값이라
     * 초안을 만드는 시점에 이미 필요해서다. 게시가 실패하면 그 id 는 그냥 버려진다.
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
     * 순수 계산 + 읽기 — {@link #assemble} 과 같은 자리에서(트랜잭션 밖) 불린다.
     * 다른 날은 {@link ItineraryRevision#copyOf} 가 {@code item_key} 를 유지한 채 그대로 복사한다.
     * 그 날에서 무엇을 남기는지는 연산이 가른다 — {@code ITEM_REMOVE} 는 지정한 항목 하나만 빼고
     * 나머지를 고정 여부와 무관하게 남기고, {@code ITINERARY_RECALCULATE} 는 고정 항목만 남긴다.
     * 기준 항목({@code itemKey})이 주어지면 그 항목보다 앞선 자리(이미 다녀온 곳)도 남긴다.
     * 빈 자리는 순위 풀에서 채우되 목표는 그 날 원래 있던 항목 수다 — 재계산이 하루의 크기를
     * 바꾸지 않는다. 원래 비어 있던 날만 {@link #maxItemsPerDay} 를 목표로 한다. 제외된 장소와
     * 이 일정에 이미 있는 장소는 풀에서 뺀다.
     * 모자라면 조건을 완화해 억지로 채우지 않고 비워 둔다. 그 사실은 판 경고
     * ({@link ItineraryWarningCodes})로 남는다 — 항목 행이 없어 항목 경고에는 적을 곳이 없다.
     * 제외 목록은 바탕 판의 것을 복사한 위에 이번 제외를 더한다. 그래서 재계산을 몇 번 해도 뺀
     * 장소가 다시 안 나오고, 되돌리기가 제외까지 되돌린다.
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
        //    보통이라 여기서 걸러야 한다.
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
        //    시각은 그 날 항목 수로 다시 나눈다 — 고정 항목의 시각도 함께 움직인다.
        int countToday = kept.size() + fills.size();
        LocalDate visitDate = trip.startDate().plusDays(dayIndex);

        // 여기도 시각을 구간보다 먼저 깔면 안 된다. 순서(남긴 것 → 채운 것)는 시각과 상관없이
        //    이미 정해져 있으므로 그 순서로 그 날 구간을 먼저 만들고, 이동 시간을 아는 상태에서
        //    시각을 깐다. 아래 8단계가 구간을 어차피 전부 다시 만드는데 그 결과를 시각보다 늦게
        //    쓰면 재계산한 날은 이동 시간이 0인 시각표로 되돌아간다.
        List<UUID> orderedToday = new ArrayList<>(countToday);
        for (ItineraryItem item : kept) {
            orderedToday.add(UUID.fromString(item.placeId()));
        }
        for (ItineraryDraftCommand.PlannedPlace fill : fills) {
            orderedToday.add(fill.placeId());
        }
        List<List<UUID>> todayOnly = new ArrayList<>(trip.days());
        for (int d = 0; d < trip.days(); d++) {
            todayOnly.add(d == dayIndex ? orderedToday : List.of());
        }
        List<Slot> timedToday = layoutDay(trip, countToday,
                travelMinutesFor(this.legPlanner.buildLegs(trip, todayOnly), dayIndex, countToday));

        List<ItineraryItem> dayResult = new ArrayList<>(countToday);
        boolean lockedTimeMoved = false;
        int sequence = 1;
        for (ItineraryItem item : kept) {
            Slot slot = timedToday.get(sequence - 1);
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
            Slot slot = timedToday.get(sequence - 1);
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
                    // 요금이 안 넘어가는 것은 위(생성 경로)와 같다 — 따로 고칠 일이다.
                    draftLeg.dataStatus(), null, draftLeg.path(), now));
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
     * 바깥 트랜잭션 안에서 불린다.
     * CAS(내가 읽은 뒤로 바뀐 게 없을 때만 쓴다)는 저장소가 한다 — {@code appendVersion} 의 판
     * 번호 UNIQUE 와 포인터 조건부 UPDATE. 여기서는 그 실패({@link StaleItineraryVersionException})를
     * 포트 예외로 바꿔 던질 뿐이고, 바깥 트랜잭션이 되돌려져 이전 판이 그대로 최신으로 남는다.
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
        // 위 catch 로 빠지면 여기까지 오지 않는다 — 진 편집으로 알림이 나가지 않는다.
        this.events.publishEvent(new ItineraryChangedByMember(
                revision.itineraryId(), newVersion, revision.operation(), revision.userId()));
        return new ItineraryHandle(revision.itineraryId(), newVersion);
    }
}
