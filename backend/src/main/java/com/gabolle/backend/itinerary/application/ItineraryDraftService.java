package com.gabolle.backend.itinerary.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.calibration.StayCalibrationPort;
import com.gabolle.backend.itinerary.application.port.PlaceEventSchedulePort;
import com.gabolle.backend.itinerary.application.port.RouteOrderPort;
import com.gabolle.backend.place.domain.InterestTagCode;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.service.ChainBrand;
import com.gabolle.backend.place.service.PlaceDessertOnlyPort;
import com.gabolle.backend.place.service.PlaceMenuPricePort;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.OpeningHoursFilterPort;
import com.gabolle.backend.place.service.PlaceTimeTablePort;
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
import com.gabolle.backend.recommendation.adapter.SeedBoost;
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
import com.gabolle.backend.trip.domain.WalkOnlyFirstDay;

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
     * 그 시각에 문을 여는가 · 브레이크타임에 걸리는가 · 라스트오더를 지났는가. 후보를 고르는 단계가 아니라 자리에
     * 앉히는 단계에서 묻는다 — 후보 조회는 여행 전체에 한 번 부르고 시각 칸은 한 순간이라, 거기에 첫날 아침을 넣으면
     * 화요일 오후에 방문할 곳까지 월요일 아침 기준으로 걸러진다.
     *
     * <p>장소마다 영업표를 한 번 읽고 시각마다의 판정은 메모리에서 한다(S15P21E201-1663) — {@link ViolationMemo}.
     */
    private final PlaceTimeTablePort timeTables;

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

    /**
     * 카페는 하루 한 곳까지 — 남는 자리는 명소(문화·자연·도시·바다)가 먼저 앉는다 (S15P21E201-1573).
     *
     * <p>밥집은 끼니 수로 상한이 있지만 카페는 없어서, 카테고리를 안 고른 여행이 하루 4곳 중 밥집 2 · 카페 1~2 · 명소 0~1
     * 로 짜였다(운영 실측 2026-09-24 — 후보에는 문화 18·자연 7 이 있었다). 사용자 요청: 고르지 않아도 액티비티·자연이 끼게.
     * 명소가 모자라면 미뤄 둔 카페가 빈 자리를 채운다 — 자리를 비우지 않는다(카페에는 끼니 규칙이 없다).
     */
    static final String CAFE_CATEGORY = "CAFE_HEALING";

    static final int MAX_CAFE_PER_DAY = 1;

    /**
     * 장소마다의 대표 메뉴 값. 없으면(시험용 조립·가격 계층이 없는 컨텍스트) 예산 상한을 안 건다 — 모르는 값으로
     * 막지 않는다.
     */
    private PlaceMenuPricePort menuPrice;

    @Autowired(required = false)
    public void setMenuPrice(PlaceMenuPricePort menuPrice) {
        this.menuPrice = menuPrice;
    }

    /**
     * 축제처럼 기간이 정해진 장소가 여행 날짜에 여는가 — 손으로 장소를 더할 때({@code ItineraryEditService})와 같은 문이다.
     * 없으면(시험용 조립·장소 계층이 없는 컨텍스트) 거르지 않는다.
     */
    private PlaceEventSchedulePort eventSchedule;

    @Autowired(required = false)
    public void setEventSchedule(PlaceEventSchedulePort eventSchedule) {
        this.eventSchedule = eventSchedule;
    }

    /**
     * 음식 종류 표식이 디저트 하나뿐인 곳 — 이런 밥집은 끼니가 아니라 카페로 본다(S15P21E201-1635). 없으면(시험용 조립·장소
     * 계층이 없는 컨텍스트) 갈래를 그대로 쓴다.
     */
    private PlaceDessertOnlyPort dessertOnly;

    @Autowired(required = false)
    public void setDessertOnly(PlaceDessertOnlyPort dessertOnly) {
        this.dessertOnly = dessertOnly;
    }

    /**
     * 실제로 머문 시간으로 고친 갈래별 체류 시간(S15P21E201-1692). 스위치가 꺼져 있거나 그 갈래 값이 아직 없으면
     * {@link StayDefaults} 를 쓴다.
     */
    private StayCalibrationPort stayCalibration;

    @Autowired(required = false)
    public void setStayCalibration(StayCalibrationPort stayCalibration) {
        this.stayCalibration = stayCalibration;
    }

    /** 장소 태그(야경 · 야시장)를 읽는 곳 — 저녁 날(S15P21E201-1734)에 쓴다. 없으면 갈래로만 고른다. */
    private PlaceFeatureRepository placeFeatures;

    @Autowired(required = false)
    public void setPlaceFeatures(PlaceFeatureRepository placeFeatures) {
        this.placeFeatures = placeFeatures;
    }

    public ItineraryDraftService(TripRepository tripRepository, ItineraryRepository itineraryRepository, Clock clock,
            @Value("${gabolle.itinerary.max-items-per-day:4}") int maxItemsPerDay,
            @Value("${gabolle.itinerary.max-food-per-day:3}") int maxFoodPerDay,
            @Value("${gabolle.itinerary.food-category:FOOD}") String foodCategory,
            @Value("${gabolle.itinerary.candidate-headroom:3}") int candidateHeadroom,
            ItineraryLegPlanner legPlanner, PlaceTimeTablePort timeTables, ObjectProvider<RouteOrderPort> routeOrder,
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
        this.timeTables = timeTables;
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
        // 🔴 끝난 축제가 추천 일정에 들어가던 것 — 여행 날짜에 한 날도 안 여는 행사 장소는 후보에서 빼고, 며칠만 여는 곳은
        //    그 날에만 앉힌다({@link #eventDaysOf}).
        Map<UUID, Set<Integer>> eventDays = eventDaysOf(trip, command.places());
        List<ItineraryDraftCommand.PlannedPlace> places = asCafeIfDessertOnly(oneOfEachBrand(command.places().stream()
                .filter((p) -> !eventDays.containsKey(p.placeId()) || !eventDays.get(p.placeId()).isEmpty())
                .toList()));
        BudgetCap cap = budgetCapOf(trip, places);
        // 걷기만 고른 여행이면 첫날은 출발지에서 걸어서 30분 안(S15P21E201-1634). 아니면 null.
        double[] firstDayOrigin = WalkOnlyFirstDay.applies(trip)
                ? new double[] { trip.originLat(), trip.originLng() } : null;
        // 날마다 활동 시간대 — 오늘 오후에 만든 오늘 여행이면 첫날만 그 시각부터다(S15P21E201-1734). 첫날이 짧아진 만큼
        // 넣을 곳과 끼니도 줄인다.
        DayWindow[] windows = new DayWindow[days];
        int[] itemsByDay = new int[days];
        int[] mealsByDay = new int[days];
        boolean[] eveningDay = new boolean[days];
        Instant madeAt = this.clock.instant();
        for (int d = 0; d < days; d++) {
            windows[d] = DayWindow.of(trip, d, madeAt);
            itemsByDay[d] = windows[d].scaledItems(itemsPerDay(trip), DayWindow.of(trip));
            // 저녁 날은 식당이 먼저다 — 저녁 시각대와 덜 겹쳐도 한 끼는 둔다.
            mealsByDay[d] = windows[d].evening() ? Math.max(1, mealsPerDay(windows[d])) : mealsPerDay(windows[d]);
            eveningDay[d] = windows[d].evening();
        }
        if (days == 1 && windows[0].noTimeLeft()) {
            throw new ItineraryDraftPort.NoTimeLeftTodayException("오늘 출발하는 당일치기인데 넣을 시간이 없다: tripId="
                    + trip.tripId() + ", madeAt=" + madeAt);
        }
        Set<UUID> nightFriendly = eveningDay[0] ? nightFriendly(places) : Set.of();
        Distribution distribution = distributeByDay(places, days, cap, mealsByDay, itemsByDay, eveningDay,
                nightFriendly, eventDays, firstDayOrigin);
        List<List<ItineraryDraftCommand.PlannedPlace>> byDay = distribution.byDay();
        SpareCandidates spare = new SpareCandidates(places, byDay, cap, coordinatesOf(places), eventDays,
                firstDayOrigin, eveningDay, nightFriendly);

        // 구간을 만들 때 필요한, 날짜별 "그 날 다녀올 장소" 원본 순서.
        List<List<UUID>> placeIdsByDay = new ArrayList<>();
        List<List<Placed>> placedByDay = new ArrayList<>();

        // 둘째 날부터는 숙소에서 나선다 — 차례를 정하는 잣대도 구간을 재는 잣대와 같은 출발점을 쓴다.
        ItineraryLegPlanner.Anchor lodging = this.legPlanner.lodgingOf(trip);
        ViolationMemo verdicts = new ViolationMemo();
        for (int dayIndex = 0; dayIndex < byDay.size(); dayIndex++) {
            List<ItineraryDraftCommand.PlannedPlace> dayPlaces = byDay.get(dayIndex);
            LocalDate visitDate = trip.startDate().plusDays(dayIndex);

            // 자리에 앉히기 전에 차례를 거리로 다시 세운다. 앉히는 규칙(영업시간·밥 때)은 그대로
            // 두고 훑는 차례만 바꾼다 — placeIntoSlots 은 목록을 앞에서부터 보므로, 목록의 차례가
            // 곧 "같은 조건이면 이쪽 먼저" 가 된다.
            dayPlaces = reorderByRoute(trip, dayIndex, lodging, dayPlaces);

            List<Placed> placedToday = placeIntoSlots(trip, dayPlaces, visitDate, verdicts, spare, dayIndex,
                    windows[dayIndex]);
            // 칸에 앉힌 뒤 한 번 더 — 끼니 칸이 차례를 섞어 놓은 것을 동선으로 다시 푼다(S15P21E201-1547).
            placedToday = shortestSlotOrder(trip, dayIndex, lodging, placedToday, visitDate, verdicts,
                    windows[dayIndex]);
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
            // 하루 끝에 숙소(마지막 날은 출발지)로 돌아가는 시간을 먼저 뗀다 — 안 떼면 마지막 방문지가 활동
            // 시간대 끝까지 머물고, 돌아가는 길은 시간대 밖으로 밀린다(S15P21E201-1565).
            Integer returnMinutes = returnMinutesOf(trip, dayIndex, lodging, placeIdsByDay.get(dayIndex));
            // 밥 칸에 앉은 밥집만 끼니다 — 밥집이 모자란 칸을 채운 밥집(아침 첫 칸 등)까지 점심에 맞추면 하루가 늦게 시작한다.
            LocalTime[][] mealBands = mealSlotBands(windows[dayIndex], placedToday.size());
            List<DayTimeLayout.Stop> stops = new ArrayList<>(placedToday.size());
            for (int i = 0; i < placedToday.size(); i++) {
                ItineraryDraftCommand.PlannedPlace place = placedToday.get(i).place();
                boolean meal = mealBands[i] != null && isFood(place);
                stops.add(new DayTimeLayout.Stop(stayMinutesFor(place.category()),
                        meal ? mealBands[i][0] : null, meal ? mealBands[i][1] : null));
            }
            List<Slot> timed = layoutDay(windows[dayIndex], stops, travelMinutesFor(legs, dayIndex, placedToday.size()),
                    returnMinutes);

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
    private List<ItineraryDraftCommand.PlannedPlace> reorderByRoute(Trip trip, int dayIndex, ItineraryLegPlanner.Anchor lodging,
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

        Double[] start = this.legPlanner.dayStart(trip, dayIndex, lodging);
        List<UUID> ordered = port.shortestOrder(new RouteOrderPort.RouteOrderRequest(
                start[0], start[1], List.copyOf(placeIds), travelMode));
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
    /**
     * 예산 상한 — 아는 가격의 누계가 {@code 예산 × (1 + 허용 폭)}({@link BudgetAllowance#capKrw}) 을 넘지 않게 자리를 채운다.
     *
     * <p>합계 규칙은 일정 응답·화면과 같다(ItineraryQueryService.costOf · 앱 itineraryBudget): <b>아는 가격만</b>
     * 더한다. 모르는 곳은 0 이 아니라 합계에서 빠지므로 여기서도 막지 않는다.
     *
     * <p>🔴 S15P21E201-1579 — 메뉴 값은 한 그릇(1인분)이고 예산은 여행 전체 총액이다. 그래서 값에 인원수를 곱해
     * 센다. 안 곱하면 3명 여행에서 실제 식비의 1/3 만 세어 상한이 사실상 안 걸린다.
     */
    private static final class BudgetCap {

        private final Map<UUID, Integer> prices;

        private final int partySize;

        private final long limit;

        private long spent;

        private BudgetCap(Map<UUID, Integer> prices, int partySize, long limit) {
            this.prices = prices;
            this.partySize = partySize;
            this.limit = limit;
        }

        /** 이곳을 넣으면 상한을 넘나. 값을 모르면 안 넘는다. */
        boolean wouldExceed(ItineraryDraftCommand.PlannedPlace place) {
            Integer price = this.prices.get(place.placeId());
            return price != null && this.spent + (long) price * this.partySize > this.limit;
        }

        void take(ItineraryDraftCommand.PlannedPlace place) {
            Integer price = this.prices.get(place.placeId());
            if (price != null) {
                this.spent += (long) price * this.partySize;
            }
        }
    }

    /** 예산이 없거나 가격 계층이 없으면 {@code null} — 상한을 안 건다. */
    private BudgetCap budgetCapOf(Trip trip, List<ItineraryDraftCommand.PlannedPlace> places) {
        // 상한은 한 곳에서 센다 — 일정 응답의 budgetCapKrw 와 같은 값이어야 한다(S15P21E201-1743).
        Integer limit = BudgetAllowance.capKrw(trip.budgetKrw());
        if (limit == null || this.menuPrice == null) {
            return null;
        }
        List<UUID> ids = new ArrayList<>(places.size());
        for (ItineraryDraftCommand.PlannedPlace place : places) {
            ids.add(place.placeId());
        }
        return new BudgetCap(this.menuPrice.pricesOf(ids), trip.partySize(), limit);
    }

    /**
     * 기간이 정해진 장소(축제·박람회 — {@code place_event_period} 가 한 줄이라도 있는 곳)가 이 여행의 몇째 날(0부터)에
     * 여나. 한 날도 안 열면 빈 집합이다 — 부르는 쪽이 후보에서 뺀다.
     *
     * <p>🔴 운영(2026-09-24)에서 회차 14개가 전부 끝났는데 「카운트다운 부산」이 9월 여행에 들어갔다. 기간은 손으로
     * 더할 때만 보고 추천이 후보를 고를 때는 안 봤다 — 그 장소들의 갈래가 {@code CULTURE_TEMPLE} 이라 일반 명소와
     * 똑같이 앉았다. 판정은 손으로 더할 때와 같은 {@link PlaceEventSchedulePort} 다 — 규칙을 두 번 쓰지 않는다.
     *
     * <p>기간이 없는 장소는 담지 않는다(아무 날이나). 사용자가 직접 고른 「꼭 갈 장소」도 담지 않는다 — 사용자의 선택이다.
     */
    private Map<UUID, Set<Integer>> eventDaysOf(Trip trip, List<ItineraryDraftCommand.PlannedPlace> places) {
        if (this.eventSchedule == null || places.isEmpty()) {
            return Map.of();
        }
        List<String> ids = places.stream()
                .filter((p) -> p.reasonCodes() == null || !p.reasonCodes().contains(SeedBoost.REASON_CODE_MUST_VISIT))
                .map((p) -> p.placeId().toString())
                .distinct()
                .toList();
        LocalDate first = trip.startDate();
        Map<UUID, Set<Integer>> out = new HashMap<>();
        this.eventSchedule.schedulesWithin(ids, first, trip.finishDate()).forEach((placeId, schedule) -> {
            if (!schedule.scheduled()) {
                return;
            }
            Set<Integer> days = new HashSet<>();
            for (LocalDate open : schedule.openDates()) {
                days.add((int) ChronoUnit.DAYS.between(first, open));
            }
            out.put(UUID.fromString(placeId), days);
        });
        return out;
    }

    private Distribution distributeByDay(
            List<ItineraryDraftCommand.PlannedPlace> places, int days, BudgetCap cap, int[] mealsPerDay, int[] itemsPerDay,
            boolean[] eveningDay, Set<UUID> nightFriendly, Map<UUID, Set<Integer>> eventDays, double[] firstDayOrigin) {

        List<List<ItineraryDraftCommand.PlannedPlace>> byDay = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            byDay.add(new ArrayList<>());
        }

        int[] foodPerDay = new int[days];
        int[] cafePerDay = new int[days];

        // S15P21E201-1493 — 좌표를 미리 한 번에 읽는다. 없으면(저장소가 못 주면) 아래 배정은
        // 예전처럼 "자리 있는 첫 날" 로 떨어진다 — 좌표가 없다고 일정 생성이 멈추면 안 된다.
        Map<UUID, double[]> coords = coordinatesOf(places);

        // 하루가 한 지역이 되게 날마다 중심을 잡는다. 순위 1위가 첫 날의 중심이 되고, 그
        // 다음 중심은 이미 잡힌 중심들에서 가장 먼 곳이다 — 그래야 날끼리 겹치지 않는다.
        double[][] dayAnchor = seedDayAnchors(places, coords, days);
        if (firstDayOrigin != null && days > 0) {
            dayAnchor[0] = firstDayOrigin; // 걷기만 고른 여행의 첫날은 출발지가 중심이다(S15P21E201-1634)
        }

        // 🔴 S15P21E201-1494 — **두 번 훑는다.** 한 번만 훑으면서 순위대로 무조건 앉히면,
        //    앞쪽 후보가 자리를 다 채워서 **뒤에 있는 「지역이 맞는 후보」의 차례가 안 온다.**
        //
        //    운영 사례가 그랬다. 8자리를 순위 1~8이 다 채우는데 그 8번째가 해운대라
        //    영도 날에 끼었고, 정작 영도인 11위는 앉을 자리가 없었다. 후보를 더 받아도
        //    (candidateHeadroom) 쓰이질 않으니 아무것도 안 바뀐다.
        //
        //    그래서 첫 훑기는 **지역이 맞는 것만** 앉힌다. 위 8번째는 여기서 건너뛰어지고,
        //    11위가 영도 날을 채운다. 남은 자리는 두 번째 훑기가 순위대로 메운다.
        // 🔴 예산 상한(S15P21E201-1572) — 넣으면 상한을 넘는 곳은 아예 앉히지 않는다. 그 자리는 뒤의 후보(더 싸거나
        //    값을 모르는 곳)가 맡는다. 두 훑기 모두에서 먼저 본다 — 두 번째 훑기가 순위대로 메우며 비싼 곳을 되살리면 안 된다.
        List<ItineraryDraftCommand.PlannedPlace> deferred = new ArrayList<>();
        for (ItineraryDraftCommand.PlannedPlace place : places) {
            if (cap != null && cap.wouldExceed(place)) {
                continue;
            }
            if (!seat(byDay, foodPerDay, cafePerDay, true, place, mealsPerDay, itemsPerDay, eveningDay, nightFriendly,
                    coords, dayAnchor, true,
                    eventDays.get(place.placeId()), firstDayOrigin)) {
                deferred.add(place);
            }
            else if (cap != null) {
                cap.take(place);
            }
        }

        // 미뤄 둔 밥집으로 빈 자리를 메우지 않는다. 끼니 상한을 무시하고 메우면 명소 데이터가
        // 모자란 지역에서 하루가 통째로 음식점이 되고, 데이터가 모자라다는 사실이 아무 데도
        // 안 보인다. 사용자에게는 "이 앱은 밥집만 추천한다" 로 보이고 팀에게는 신호가 안 온다.
        // 그래서 비워 두고 말한다.
        int rejectedFood = 0;
        for (ItineraryDraftCommand.PlannedPlace place : deferred) {
            if (cap != null && cap.wouldExceed(place)) {
                // 예산으로 뺀 것은 「밥집밖에 없어 비웠다」가 아니다 — 그 경고에 안 센다.
                continue;
            }
            if (seat(byDay, foodPerDay, cafePerDay, true, place, mealsPerDay, itemsPerDay, eveningDay, nightFriendly,
                    coords, dayAnchor, false,
                    eventDays.get(place.placeId()), firstDayOrigin)) {
                if (cap != null) {
                    cap.take(place);
                }
            }
            else if (isFood(place)) {
                rejectedFood++;
            }
        }

        // 세 번째 훑기 — 명소로 못 채운 자리가 남으면 미뤄 둔 카페로 메운다(카페 상한을 풀고). 빈 자리보다 카페가 낫다.
        for (ItineraryDraftCommand.PlannedPlace place : deferred) {
            if (!isCafe(place) || byDay.stream().anyMatch(day -> day.contains(place))) {
                continue;
            }
            if (cap != null && cap.wouldExceed(place)) {
                continue;
            }
            if (seat(byDay, foodPerDay, cafePerDay, false, place, mealsPerDay, itemsPerDay, eveningDay, nightFriendly,
                    coords, dayAnchor, false,
                    eventDays.get(place.placeId()), firstDayOrigin)
                    && cap != null) {
                cap.take(place);
            }
        }

        // 자리는 남았는데 앉힐 것이 밥집밖에 없었던 경우에만 경고한다. 하루가 꽉 차서
        // 밥집이 밀린 것은 정상이고, 그건 빈 자리를 만들지 않는다.
        boolean roomLeft = false;
        for (int day = 0; day < days; day++) {
            roomLeft |= byDay.get(day).size() < itemsPerDay[day];
        }
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

    private int mealsPerDay(DayWindow window) {
        LocalTime start = window.start();
        LocalTime end = window.end();
        if (!window.known()) {
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
    private boolean seat(List<List<ItineraryDraftCommand.PlannedPlace>> byDay, int[] foodPerDay, int[] cafePerDay,
            boolean enforceCafeCap, ItineraryDraftCommand.PlannedPlace place, int[] mealsPerDay, int[] itemsPerDay,
            boolean[] eveningDay, Set<UUID> nightFriendly,
            Map<UUID, double[]> coords, double[][] dayAnchor, boolean regionOnly, Set<Integer> openDays,
            double[] firstDayOrigin) {

        boolean food = isFood(place);
        boolean cafe = isCafe(place);
        double[] here = coords.get(place.placeId());

        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int day = 0; day < byDay.size(); day++) {
            if (byDay.get(day).size() >= itemsPerDay[day]) {
                continue;
            }
            // 기간이 정해진 장소(축제)는 여는 날에만 앉는다. null 이면 기간이 없는 장소다.
            if (openDays != null && !openDays.contains(day)) {
                continue;
            }
            if (food && foodPerDay[day] >= mealsPerDay[day]) {
                continue;
            }
            // 저녁 날(S15P21E201-1734)은 식당과 밤에 볼 만한 곳만 — 저녁에 절·박물관·산길을 넣지 않는다.
            if (eveningDay[day] && !food && !nightFriendly.contains(place.placeId())) {
                continue;
            }
            if (cafe && enforceCafeCap && cafePerDay[day] >= MAX_CAFE_PER_DAY) {
                continue;
            }
            if (!fitsWalkOnlyFirstDay(place, here, day, firstDayOrigin)) {
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
        if (cafe) {
            cafePerDay[best]++;
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

    /**
     * 괄호 안만 다른 이름이 이만큼 안에 있으면 같은 곳으로 보고 한 곳만 남긴다(S15P21E201-1758, e4 결정). 광안리 드론쇼가
     * 두 줄 — 오픈스트리트맵 「광안리 M 드론 라이트쇼」와 TourAPI 「광안리 M(Marvelous) 드론 라이트쇼」, 약 400m — 로 들어와
     * 한 일정에 두 번 앉았다. 적재 때 중복을 합치는 기준은 150m 라 못 걸렀다.
     *
     * <p>운영(2026-09-26, 6,844곳)에서 이 값에 걸리는 쌍은 9쌍이고, 그중 실제로 다른 곳은 「세계를 바라보다(남자)·(여자)」
     * (934m) 한 쌍이다. 괄호를 빼면 같지만 1km 밖이라 둘 다 남는 쌍은 19쌍이다.
     */
    private static final double PAREN_VARIANT_KM = 1.0;

    /**
     * 걷기만 고른 여행의 첫날 규칙(S15P21E201-1634) — 첫날은 출발지에서 걸어서 {@link WalkOnlyFirstDay#MINUTES}분 안
     * ({@link WalkOnlyFirstDay#RADIUS_M}m)의 곳만, 범위 밖인데 출발지 둘레라서 들어온 곳({@link WalkOnlyFirstDay#REASON_CODE})은
     * 첫날에만. 걷기만 고른 여행이 아니면({@code firstDayOrigin == null}) 늘 참이다. 첫날에 좌표를 모르는 곳은 안 앉힌다.
     */
    private static boolean fitsWalkOnlyFirstDay(ItineraryDraftCommand.PlannedPlace place, double[] here, int day,
            double[] firstDayOrigin) {
        if (firstDayOrigin == null) {
            return true;
        }
        if (day == 0) {
            return here != null && haversineKm(here, firstDayOrigin) * 1000 <= WalkOnlyFirstDay.RADIUS_M;
        }
        return place.reasonCodes() == null || !place.reasonCodes().contains(WalkOnlyFirstDay.REASON_CODE);
    }

    /**
     * 한 일정에 같은 상표는 한 번만 — 순위가 가장 높은 지점 하나만 남기고 뒤 지점은 후보에서 뺀다
     * (S15P21E201-1616, 사용자 결정). 운영 일정 201개 중 4개가 같은 체인의 다른 지점을 두 번 넣었다.
     *
     * <p>앉히기 <b>전에</b> 뺀다. 세 번 훑는 앉히기 안에서 막으면 훑기마다 같은 검사를 넣어야 하고 하나라도 빠지면
     * 새어 나간다. 판정은 추천 점수를 낮추는 쪽과 같은 사전({@link ChainBrand})이다.
     *
     * <p>🔴 사전에 없는 가게도 <b>이름이 같으면</b> 한 곳만 남긴다(S15P21E201-1631, 사용자 결정). 운영 일정 161개 중
     * 3개에 젤라또부(400m 떨어진 두 지점)·젤라또조이(5km)가 두 번씩 들어갔다 — 사전은 등록된 45개 상표만 본다.
     * 이름은 띄어쓰기·대소문자를 무시하고 견준다.
     *
     * <p>🔴 <b>괄호 안만 다른 이름</b>은 {@link #PAREN_VARIANT_KM} 안에 있을 때만 한 곳으로 본다(S15P21E201-1758).
     * 이름이 완전히 같을 때처럼 거리와 상관없이 빼지 않는 것은, 괄호가 곳을 가르는 말인 경우가 있어서다 — 「전망대(황령산)」와
     * 「전망대(이기대)」는 다른 곳이다. 견주는 상대는 <b>남긴 곳</b>뿐이다 — 빠진 곳이 다른 곳을 끌고 가지 않는다.
     */
    private List<ItineraryDraftCommand.PlannedPlace> oneOfEachBrand(List<ItineraryDraftCommand.PlannedPlace> places) {
        if (places.isEmpty()) {
            return places;
        }
        List<UUID> ids = places.stream().map(ItineraryDraftCommand.PlannedPlace::placeId).toList();
        Map<UUID, String> nameById = new HashMap<>();
        Map<UUID, double[]> coordById = new HashMap<>();
        for (Place place : this.placeRepository.findByPlaceIdIn(ids)) {
            nameById.put(place.getPlaceId(), place.getNameKo());
            if (place.getLat() != null && place.getLng() != null) {
                coordById.put(place.getPlaceId(), new double[] { place.getLat(), place.getLng() });
            }
        }
        Set<String> seen = new HashSet<>();
        Map<String, List<double[]>> keptByBaseName = new HashMap<>();
        List<ItineraryDraftCommand.PlannedPlace> kept = new ArrayList<>(places.size());
        for (ItineraryDraftCommand.PlannedPlace place : places) {
            String name = nameById.get(place.placeId());
            String brand = ChainBrand.brandOf(name);
            String key = (brand != null) ? "brand:" + brand
                    : (name == null || name.isBlank()) ? null : "name:" + name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            String baseName = (name == null) ? null
                    : name.replaceAll("\\([^)]*\\)", "").replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            double[] here = coordById.get(place.placeId());
            if ((key != null && seen.contains(key)) || nearKeptSameBaseName(keptByBaseName.get(baseName), here)) {
                continue;
            }
            if (key != null) {
                seen.add(key);
            }
            if (here != null && baseName != null && !baseName.isEmpty()) {
                keptByBaseName.computeIfAbsent(baseName, (k) -> new ArrayList<>()).add(here);
            }
            kept.add(place);
        }
        return kept;
    }

    /** 괄호를 뺀 이름이 같은 남긴 곳 가운데 {@link #PAREN_VARIANT_KM} 안에 있는 것이 있나. 좌표를 모르면 빼지 않는다. */
    private static boolean nearKeptSameBaseName(List<double[]> keptSpots, double[] here) {
        if (keptSpots == null || here == null) {
            return false;
        }
        for (double[] spot : keptSpots) {
            if (haversineKm(spot, here) <= PAREN_VARIANT_KM) {
                return true;
            }
        }
        return false;
    }

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

    /**
     * 디저트 표식만 있는 밥집을 카페로 다시 읽는다 — S15P21E201-1635.
     *
     * <p>🔴 왜. 상가 자료가 젤라또·아이스크림·빵집을 「음식점」으로 넣어 끼니 칸을 차지했다 — 「점심으로 젤라또」(최근 4일
     * 운영 일정에 12번, 그중 8번이 점심·저녁 시간). 조립을 시작할 때 한 번 바꾸면 끼니 수·끼니 칸·카페 상한이 전부
     * 카페로 따라간다. 데이터는 마이그레이션(V20260925060000)이 고치고, 이 코드는 다음 적재 때 다시 생기는 것을 막는다.
     */
    private List<ItineraryDraftCommand.PlannedPlace> asCafeIfDessertOnly(List<ItineraryDraftCommand.PlannedPlace> places) {
        if (this.dessertOnly == null || places.isEmpty()) {
            return places;
        }
        List<UUID> foodIds = places.stream().filter(this::isFood).map(ItineraryDraftCommand.PlannedPlace::placeId).toList();
        if (foodIds.isEmpty()) {
            return places;
        }
        Set<UUID> dessert = this.dessertOnly.dessertOnly(foodIds);
        if (dessert.isEmpty()) {
            return places;
        }
        return places.stream()
                .map(p -> dessert.contains(p.placeId())
                        ? new ItineraryDraftCommand.PlannedPlace(p.placeId(), p.rank(), p.reasonCodes(), p.warningCodes(),
                                CAFE_CATEGORY)
                        : p)
                .toList();
    }

    /** 갈래를 모르면 밥집이 아닌 것으로 다룬다 — 모르는 것을 끼니로 세지 않는다. */
    private boolean isFood(ItineraryDraftCommand.PlannedPlace place) {
        return place.category() != null && place.category().equalsIgnoreCase(this.foodCategory);
    }

    private static boolean isCafe(ItineraryDraftCommand.PlannedPlace place) {
        return place.category() != null && place.category().equalsIgnoreCase(CAFE_CATEGORY);
    }

    /** 밤에 볼 만한 갈래 — 바다와 도시 풍경(S15P21E201-1734). 절 · 박물관 · 산길은 밤에 닫거나 어둡다. */
    private static final Set<String> NIGHT_CATEGORIES = Set.of("SEA_BEACH", "CITY");

    /** 밤에 볼 만한 곳이라고 조사가 붙인 태그. 2026-09-26 이 PC 로컬 복사본 기준 야경 5곳 · 야시장 0곳이라 갈래가 주로 쓰인다. */
    private static final Set<String> NIGHT_TAGS = Set.of(InterestTagCode.NIGHT_VIEW.name(),
            InterestTagCode.NIGHT_MARKET.name());

    /**
     * 후보 가운데 저녁 날에 앉힐 수 있는 곳(식당 말고) — 갈래가 {@link #NIGHT_CATEGORIES} 이거나 {@link #NIGHT_TAGS} 태그가 붙은
     * 곳. 영업시간은 여기서 안 본다 — 칸에 앉힐 때({@link #placeIntoSlots}) 그 시각에 닫힌 곳을 빼는 규칙이 그대로 돈다.
     */
    private Set<UUID> nightFriendly(List<ItineraryDraftCommand.PlannedPlace> places) {
        Set<UUID> out = new HashSet<>();
        List<UUID> ids = new ArrayList<>(places.size());
        for (ItineraryDraftCommand.PlannedPlace place : places) {
            ids.add(place.placeId());
            if (place.category() != null && NIGHT_CATEGORIES.contains(place.category().toUpperCase(Locale.ROOT))) {
                out.add(place.placeId());
            }
        }
        if (this.placeFeatures != null && !ids.isEmpty()) {
            for (PlaceFeature feature : this.placeFeatures.findByPlaceIdIn(ids)) {
                if (InterestTagCode.FEATURE_TYPE.equals(feature.getFeatureType())
                        && NIGHT_TAGS.contains(feature.getFeatureKey())) {
                    out.add(feature.getPlaceId());
                }
            }
        }
        return out;
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
                                        LocalDate visitDate, ViolationMemo verdicts, SpareCandidates spare,
                                        int dayIndex, DayWindow window) {

        int count = dayPlaces.size();
        List<Placed> placed = new ArrayList<>(count);
        boolean[] used = new boolean[count];
        boolean[] mealSlot = mealSlots(window, count);

        for (int slotIndex = 0; slotIndex < count; slotIndex++) {
            Slot slot = slotFor(window, slotIndex, count);
            OffsetDateTime at = (slot.start() == null) ? null
                    : visitDate.atTime(slot.start()).atZone(ZONE).toOffsetDateTime();

            // 이 칸이 밥 먹는 시각이면 밥집을, 아니면 밥집이 아닌 곳을 먼저 찾는다.
            // 같은 조건이면 순위가 높은 쪽이 먼저다.
            boolean wantFood = mealSlot[slotIndex];

            int chosen = -1;
            if (at != null) {
                chosen = firstOpen(dayPlaces, used, at, wantFood, verdicts);
                if (chosen < 0) {
                    // 원하는 종류가 없다. 종류를 포기하고 영업시간만 본다 — 자리를 비우는 것보다는 낫다.
                    // "밥 때인데 밥집이 없다" 는 사실은 이미 distributeByDay 가 SIGHT_SLOT_UNFILLED 로 말한다.
                    chosen = firstOpen(dayPlaces, used, at, !wantFood, verdicts);
                }
            }

            if (chosen < 0 && at != null) {
                // 그날 남은 곳이 그 시각에 다 닫혔다 — 안 쓴 후보 가운데 여는 곳으로 바꾼다(S15P21E201-1632).
                // 바뀐 곳에 밀린 그날 후보 하나는 끝까지 자리를 못 받고 빠진다.
                ItineraryDraftCommand.PlannedPlace substitute = spare.openAt(dayIndex, dayPlaces, at, wantFood, verdicts);
                if (substitute == null) {
                    substitute = spare.openAt(dayIndex, dayPlaces, at, !wantFood, verdicts);
                }
                if (substitute != null) {
                    placed.add(new Placed(substitute, slot, substitute.warningCodes()));
                    continue;
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
            String violation = (forced && at != null) ? verdicts.at(place.placeId(), at) : null;
            if (violation != null) {
                warnings = new ArrayList<>(warnings == null ? List.of() : warnings);
                warnings.add(violation);
            }
            placed.add(new Placed(place, slot, warnings));
        }
        return placed;
    }

    /**
     * 칸 종류(끼니·아닌 것)와 영업시간을 지키는 차례 중 <b>하루 이동거리가 가장 짧은 것</b>으로 바꾼다.
     *
     * <p>🔴 왜 (2026-09-23, S15P21E201-1547). 차례는 {@link #reorderByRoute} 가 최단으로 정하지만,
     * {@link #placeIntoSlots} 가 점심·저녁 칸에 «목록에서 처음 나오는 밥집» 을 앉히면서 그 차례를 다시
     * 섞는다. 운영 사례: 남포 카페 → <b>해운대</b> 밥집 → 영도 시장 → 영도 밥집 — 남포·해운대·영도를
     * 오갔다(일정 eb0d0494). 하루는 3~5곳이라 가능한 차례를 전부 따져도 120가지다.
     *
     * <p>지키는 것: ① 끼니 칸에 밥집이 앉는 수가 원래보다 줄지 않는다 ② 영업시간에 걸리는 수가
     * 원래보다 늘지 않는다. 둘을 지키는 차례가 원래보다 짧을 때만 바꾼다 — 같으면 원래 그대로.
     *
     * <p>좌표를 모르는 곳이 있거나, 칸 시각이 없거나(활동 시간 미정), 하루가 6곳을 넘으면 손대지 않는다.
     */
    private List<Placed> shortestSlotOrder(Trip trip, int dayIndex, ItineraryLegPlanner.Anchor lodging, List<Placed> placed,
            LocalDate visitDate, ViolationMemo verdicts, DayWindow window) {

        int count = placed.size();
        if (count < 2 || count > 6) {
            return placed;
        }
        Double[] start = this.legPlanner.dayStart(trip, dayIndex, lodging);
        List<ItineraryDraftCommand.PlannedPlace> places = new ArrayList<>(count);
        for (Placed p : placed) {
            places.add(p.place());
        }
        Map<UUID, double[]> coords = coordinatesOf(places);
        if (coords.size() < count) {
            return placed;
        }
        OffsetDateTime[] at = new OffsetDateTime[count];
        boolean[] wantFood = mealSlots(window, count);
        for (int i = 0; i < count; i++) {
            Slot slot = placed.get(i).slot();
            if (slot.start() == null) {
                return placed;
            }
            at[i] = visitDate.atTime(slot.start()).atZone(ZONE).toOffsetDateTime();
        }

        int[] identity = new int[count];
        for (int i = 0; i < count; i++) {
            identity[i] = i;
        }
        int baseMealHits = mealHits(places, identity, wantFood);
        int baseViolations = violations(places, identity, at, verdicts);
        double baseDistance = pathKm(start, places, identity, coords);

        int[] best = identity;
        double bestDistance = baseDistance;
        for (int[] order : permutations(count)) {
            if (mealHits(places, order, wantFood) < baseMealHits || violations(places, order, at, verdicts) > baseViolations) {
                continue;
            }
            double distance = pathKm(start, places, order, coords);
            // 반올림 흔들림으로 바꾸지 않게 100m 넘게 짧을 때만.
            if (distance < bestDistance - 0.1) {
                bestDistance = distance;
                best = order;
            }
        }
        if (best == identity) {
            return placed;
        }

        List<Placed> reordered = new ArrayList<>(count);
        for (int slotIndex = 0; slotIndex < count; slotIndex++) {
            ItineraryDraftCommand.PlannedPlace place = places.get(best[slotIndex]);
            List<String> warnings = place.warningCodes();
            String violation = verdicts.at(place.placeId(), at[slotIndex]);
            if (violation != null) {
                warnings = new ArrayList<>(warnings == null ? List.of() : warnings);
                warnings.add(violation);
            }
            reordered.add(new Placed(place, placed.get(slotIndex).slot(), warnings));
        }
        return reordered;
    }

    private int mealHits(List<ItineraryDraftCommand.PlannedPlace> places, int[] order, boolean[] wantFood) {
        int hits = 0;
        for (int i = 0; i < order.length; i++) {
            if (wantFood[i] && isFood(places.get(order[i]))) {
                hits++;
            }
        }
        return hits;
    }

    private int violations(List<ItineraryDraftCommand.PlannedPlace> places, int[] order, OffsetDateTime[] at,
            ViolationMemo verdicts) {
        int count = 0;
        for (int i = 0; i < order.length; i++) {
            if (verdicts.at(places.get(order[i]).placeId(), at[i]) != null) {
                count++;
            }
        }
        return count;
    }

    /** 출발점 → 차례대로의 직선거리 합(km). 출발점을 모르면 첫 곳부터 잰다. */
    private static double pathKm(Double[] start, List<ItineraryDraftCommand.PlannedPlace> places, int[] order,
            Map<UUID, double[]> coords) {
        double total = 0;
        double[] previous = (start[0] != null && start[1] != null) ? new double[] { start[0], start[1] } : null;
        for (int index : order) {
            double[] here = coords.get(places.get(index).placeId());
            if (previous != null) {
                total += haversineKm(previous, here);
            }
            previous = here;
        }
        return total;
    }

    private static List<int[]> permutations(int n) {
        List<int[]> out = new ArrayList<>();
        permute(new int[n], new boolean[n], 0, out);
        return out;
    }

    private static void permute(int[] current, boolean[] taken, int depth, List<int[]> out) {
        if (depth == current.length) {
            out.add(current.clone());
            return;
        }
        for (int i = 0; i < current.length; i++) {
            if (!taken[i]) {
                taken[i] = true;
                current[depth] = i;
                permute(current, taken, depth + 1, out);
                taken[i] = false;
            }
        }
    }

    /** 그 시각에 문을 연 후보 중, 원하는 종류의 첫 번째. 없으면 {@code -1}. */
    private int firstOpen(List<ItineraryDraftCommand.PlannedPlace> dayPlaces, boolean[] used,
            OffsetDateTime at, boolean food, ViolationMemo verdicts) {

        for (int i = 0; i < dayPlaces.size(); i++) {
            if (used[i] || isFood(dayPlaces.get(i)) != food) {
                continue;
            }
            if (verdicts.at(dayPlaces.get(i).placeId(), at) == null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 하루 칸 가운데 밥 칸 — 식사 시각대마다 <b>한 칸만</b> (S15P21E201-1624).
     *
     * <p>🔴 왜. 칸이 길면 한 시각대가 두 칸에 걸린다. 09~21시·하루 5곳이면 칸이 144분씩이라 저녁(17~20시)이
     * 넷째 칸(16:12~, 96분)과 다섯째 칸(18:36~, 84분)에 다 걸렸다. 밥 칸이 셋(점심 하나·저녁 둘)인데 밥집은
     * 하루 둘이라, {@link #shortestSlotOrder} 가 「밥 칸에 앉은 밥집 수가 안 줄면 된다」로 둘을 저녁 두 칸에
     * 옮겼다 — 점심이 빠지고 16시·18시에 밥을 두 번 먹었다(운영 일정 55476311 2일차).
     *
     * <p>고르는 법: 그 시각대에 걸리는 칸({@link #overlapsBand}) 가운데 <b>도착해서 첫 한 시간</b>이 시각대와 가장
     * 많이 겹치는 칸. 밥은 도착해서 먹는다 — 16:12 에 닿는 칸보다 18:36 에 닿는 칸이 저녁이다. 같으면 칸 전체가 더
     * 많이 겹치는 칸, 그것도 같으면 앞 칸. 걸리는 칸이 없는 시각대는 밥 칸이 없다(전과 같다).
     */
    private static boolean[] mealSlots(DayWindow window, int count) {
        LocalTime[][] bands = mealSlotBands(window, count);
        boolean[] meal = new boolean[count];
        for (int i = 0; i < count; i++) {
            meal[i] = bands[i] != null;
        }
        return meal;
    }

    /** 이 갈래에 머무는 시간 — 고친 값이 있으면 그것, 없으면 기본값(S15P21E201-1692). */
    private int stayMinutesFor(String category) {
        if (this.stayCalibration != null) {
            OptionalInt calibrated = this.stayCalibration.minutesFor(category);
            if (calibrated.isPresent()) {
                return calibrated.getAsInt();
            }
        }
        return StayDefaults.minutesFor(category);
    }

    /**
     * {@link #mealSlots} 와 같은 칸을 고르되, 칸마다 맡은 식사 시각대({@code {시작, 끝}})를 낸다 — 밥 칸이 아니면 {@code null}.
     * 시각 깔기({@link DayTimeLayout})가 밥 칸의 밥집을 그 시각대 안에 놓을 때 쓴다(S15P21E201-1667).
     */
    private static LocalTime[][] mealSlotBands(DayWindow window, int count) {
        LocalTime[][] meal = new LocalTime[count][];
        for (LocalTime[] band : MEAL_BANDS) {
            int best = -1;
            long bestFirstHour = -1;
            long bestOverlap = -1;
            for (int i = 0; i < count; i++) {
                Slot slot = slotFor(window, i, count);
                if (!overlapsBand(slot, band)) {
                    continue;
                }
                // 칸이 한 시간보다 길 때만 더하므로 자정을 넘겨 돌아가지 않는다.
                LocalTime firstHourEnd = Duration.between(slot.start(), slot.end()).toMinutes() > MEAL_OVERLAP_MINUTES
                        ? slot.start().plusMinutes(MEAL_OVERLAP_MINUTES)
                        : slot.end();
                long firstHour = overlapMinutes(slot.start(), firstHourEnd, band);
                long overlap = overlapMinutes(slot.start(), slot.end(), band);
                if (firstHour > bestFirstHour || (firstHour == bestFirstHour && overlap > bestOverlap)) {
                    best = i;
                    bestFirstHour = firstHour;
                    bestOverlap = overlap;
                }
            }
            if (best >= 0) {
                meal[best] = band;
            }
        }
        return meal;
    }

    /**
     * 이 칸이 이 식사 시각대에 걸리는가 — {@link #MEAL_OVERLAP_MINUTES} 이상 겹치면.
     * 스치기만 한 것은 안 센다. 09:00~18:00 · 하루 4곳이면 첫 칸 09:00~11:15 가 아침에 30분
     * 걸리는데, 겹치기만 하면 센다는 규칙이면 오전 첫 자리가 밥집이 된다.
     * {@link #mealsPerDay} 도 같은 30분을 안 세므로, 같은 잣대를 써야 칸 수와 끼니 수가 맞는다.
     * 칸 자체가 60분보다 짧으면 그 길이를 기준으로 삼는다 — 안 그러면 짧은 칸은 통째로 점심
     * 안에 들어가 있어도 영영 밥 때가 아니게 된다.
     */
    private static boolean overlapsBand(Slot slot, LocalTime[] band) {
        if (slot.start() == null || slot.end() == null) {
            return false;
        }
        long required = Math.min(MEAL_OVERLAP_MINUTES, Duration.between(slot.start(), slot.end()).toMinutes());
        long overlap = overlapMinutes(slot.start(), slot.end(), band);
        return overlap > 0 && overlap >= required;
    }

    /** [from, to) 와 식사 시각대가 겹치는 분. 안 겹치면 0. */
    private static long overlapMinutes(LocalTime from, LocalTime to, LocalTime[] band) {
        LocalTime start = band[0].isAfter(from) ? band[0] : from;
        LocalTime end = band[1].isBefore(to) ? band[1] : to;
        return end.isAfter(start) ? Duration.between(start, end).toMinutes() : 0;
    }

    /**
     * 그 시각에 그 장소가 걸리는 것이 있는가 — 있으면 경고 코드, 없으면 {@code null}.
     * 영업시간 · 브레이크타임 · 라스트오더 셋을 이 순서로 본다. 셋 다 "모른다" 를 "문제 없음" 으로
     * 접지 않는다 — {@link OpeningHoursFilterPort.Answer#CLOSED} 일 때만 걸린다.
     */
    private static String violationAt(PlaceTimeTablePort.PlaceTimeTable table, OffsetDateTime at) {
        if (table.openAt(at) == OpeningHoursFilterPort.Answer.CLOSED) {
            return ItineraryOpeningHoursChecker.VIOLATION_CLOSED;
        }
        if (table.breakTimeAt(at) == OpeningHoursFilterPort.Answer.CLOSED) {
            return ItineraryOpeningHoursChecker.VIOLATION_BREAK_TIME;
        }
        if (table.lastOrderAt(at) == OpeningHoursFilterPort.Answer.CLOSED) {
            return ItineraryOpeningHoursChecker.VIOLATION_LAST_ORDER;
        }
        return null;
    }

    /**
     * 어느 날에도 안 앉힌 후보 — 그날 남은 곳이 그 시각에 다 닫혔을 때 바꿔 넣을 곳을 여기서 찾는다(S15P21E201-1632).
     *
     * <p>🔴 왜. 그날 남은 곳이 다 닫혔으면 순위대로 앉히고 「문 닫힘」 경고만 달았다. 운영 일정(최근 4일 161개)에
     * 축제 말고도 18개 — 대부분 09~21시 마지막 칸(18시대)에 18시에 닫는 미술관, 월요일에 쉬는 책방. 여는 곳이
     * 후보에 남아 있는데도 쓰지 않았다. 연 곳이 하나도 없을 때만 지금처럼 경고를 달고 앉힌다(사용자 결정).
     *
     * <p>바꿔 넣는 곳의 조건 — 날짜에 배분할 때와 같은 선을 지킨다: 순위 차례 · 원하는 종류(밥 칸이면 밥집 먼저) ·
     * 기간이 정해진 곳(축제)은 그날 열어야 · 예산 상한을 안 넘어야 · 그날 곳들의 가운데에서 {@link #REGION_MIXED_KM}
     * 안(좌표를 모르면 안 쓴다 — 멀지도 모른다). 한 번 쓴 후보는 다른 날에 또 안 쓴다.
     */
    private final class SpareCandidates {

        private final List<ItineraryDraftCommand.PlannedPlace> places;

        private final Set<UUID> taken = new HashSet<>();

        private final BudgetCap cap;

        private final Map<UUID, double[]> coords;

        private final Map<UUID, Set<Integer>> eventDays;

        private final double[] firstDayOrigin;

        /** 저녁 날(S15P21E201-1734)은 여벌도 식당과 밤에 볼 만한 곳만 — 배분과 같은 규칙이다. */
        private final boolean[] eveningDay;

        private final Set<UUID> nightFriendly;

        SpareCandidates(List<ItineraryDraftCommand.PlannedPlace> candidates,
                List<List<ItineraryDraftCommand.PlannedPlace>> byDay, BudgetCap cap, Map<UUID, double[]> coords,
                Map<UUID, Set<Integer>> eventDays, double[] firstDayOrigin, boolean[] eveningDay,
                Set<UUID> nightFriendly) {
            this.firstDayOrigin = firstDayOrigin;
            this.eveningDay = eveningDay;
            this.nightFriendly = nightFriendly;
            Set<UUID> seated = new HashSet<>();
            byDay.forEach(day -> day.forEach(p -> seated.add(p.placeId())));
            this.places = candidates.stream().filter(p -> !seated.contains(p.placeId())).toList();
            this.cap = cap;
            this.coords = coords;
            this.eventDays = eventDays;
        }

        /** 그날 그 시각에 여는, 원하는 종류의 첫 후보. 없으면 {@code null}. 찾으면 쓴 것으로 적는다. */
        ItineraryDraftCommand.PlannedPlace openAt(int dayIndex, List<ItineraryDraftCommand.PlannedPlace> dayPlaces,
                OffsetDateTime at, boolean food, ViolationMemo verdicts) {
            double[] center = centerOf(dayPlaces);
            for (ItineraryDraftCommand.PlannedPlace candidate : this.places) {
                UUID id = candidate.placeId();
                if (this.taken.contains(id) || isFood(candidate) != food) {
                    continue;
                }
                if (this.eveningDay[dayIndex] && !isFood(candidate) && !this.nightFriendly.contains(id)) {
                    continue;
                }
                Set<Integer> openDays = this.eventDays.get(id);
                if (openDays != null && !openDays.contains(dayIndex)) {
                    continue;
                }
                if (this.cap != null && this.cap.wouldExceed(candidate)) {
                    continue;
                }
                double[] here = this.coords.get(id);
                if (center != null && (here == null || haversineKm(center, here) > REGION_MIXED_KM)) {
                    continue;
                }
                if (!fitsWalkOnlyFirstDay(candidate, here, dayIndex, this.firstDayOrigin)) {
                    continue;
                }
                if (verdicts.at(id, at) == null) {
                    this.taken.add(id);
                    if (this.cap != null) {
                        this.cap.take(candidate);
                    }
                    return candidate;
                }
            }
            return null;
        }

        /** 그날 곳들의 좌표 가운데. 하나도 모르면 {@code null} — 그때는 거리로 거르지 않는다. */
        private double[] centerOf(List<ItineraryDraftCommand.PlannedPlace> dayPlaces) {
            double lat = 0;
            double lng = 0;
            int n = 0;
            for (ItineraryDraftCommand.PlannedPlace p : dayPlaces) {
                double[] c = this.coords.get(p.placeId());
                if (c != null) {
                    lat += c[0];
                    lng += c[1];
                    n++;
                }
            }
            return n == 0 ? null : new double[] { lat / n, lng / n };
        }
    }

    /**
     * 한 번 조립하는 동안 장소마다 영업표를 한 번만 읽는다 — S15P21E201-1621 · S15P21E201-1663.
     *
     * <p>🔴 왜. {@link #shortestSlotOrder} 는 하루 5곳이면 120가지 차례를 전부 따지는데, 차례마다·자리마다
     * 영업시간·브레이크타임·라스트오더를 DB 에서 새로 읽었다. 8일·하루 5곳 조립이 질의 6,852번·7.3초였다
     * (운영 8.3초). 처음(1621)에는 (장소, 시각)마다 답을 기억했는데, 그래도 서로 다른 시각마다 셋을 새로 읽었다
     * (영업시간·브레이크타임·라스트오더가 다 있는 48곳·8일에 457번). 읽는 것은 장소에만 달려 있고 시각은 판정에만
     * 쓰이므로, 장소마다 한 번 읽어 두고 판정은 메모리에서 한다.
     *
     * <p>판정 규칙은 그대로다 — 같은 행을 같은 판정 함수로 읽으므로 고르는 차례도 전과 같다.
     * 조립 한 번 안에서만 산다(요청마다 새로 만든다). 서비스는 여러 요청이 함께 쓰는 하나라 필드에 두면 안 된다.
     */
    private final class ViolationMemo {

        private final Map<UUID, PlaceTimeTablePort.PlaceTimeTable> tables = new HashMap<>();

        /** 그 시각에 그 장소가 걸리는 것 — {@link #violationAt}. */
        String at(UUID placeId, OffsetDateTime at) {
            return violationAt(this.tables.computeIfAbsent(placeId, timeTables::tableOf), at);
        }
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

    /** 그날 마지막 방문지에서 돌아가는 데 드는 분 — 돌아갈 자리를 모르거나 못 재면 {@code null}. */
    private Integer returnMinutesOf(Trip trip, int dayIndex, ItineraryLegPlanner.Anchor lodging, List<UUID> placeIdsToday) {
        if (placeIdsToday == null || placeIdsToday.isEmpty()) {
            return null;
        }
        ItineraryLegPlanner.DayReturn back = this.legPlanner.returnFor(trip, dayIndex, lodging,
                placeIdsToday.get(placeIdsToday.size() - 1));
        return back == null ? null : back.travel().durationMin();
    }

    /**
     * 하루의 시각표를 깐다 — 곳마다 갈래별 체류만큼 머물고, 이동 시간을 비켜 가며, 남는 시간은 곳 사이의 빈 시각이 된다.
     * 규칙은 {@link DayTimeLayout} 에 있다(S15P21E201-1667). 마지막 항목의 끝이 활동 시간대의 끝을 넘지 않는다.
     * 이동만으로 하루가 다 차면 시각을 아예 안 준다({@link Slot#unknown()}). 이동을 무시하고
     * 나누면 되지도 않는 일정을 그럴듯하게 그리는 것이고, 그건 시각이 없는 것보다 나쁘다.
     * {@link #slotFor} 와 달리 하루치를 한 번에 낸다 — 앞 항목의 끝을 알아야 다음 시작을 정할
     * 수 있어서 항목 하나만 따로 계산할 수가 없다.
     *
     * @param returnMinutes 마지막 방문지에서 돌아가는 분. 없으면 {@code null} — 전처럼 시간대 끝까지 쓴다
     */
    private static List<Slot> layoutDay(DayWindow window, List<DayTimeLayout.Stop> stops, List<Integer> travelMinutes,
            Integer returnMinutes) {
        int countToday = stops.size();
        List<Slot> slots = new ArrayList<>(countToday);
        List<DayTimeLayout.Visit> visits = (!window.known() || countToday <= 0)
                ? null
                : DayTimeLayout.layout(window.start(), window.end(), stops, travelMinutes, returnMinutes);
        if (visits == null) {
            for (int i = 0; i < countToday; i++) {
                slots.add(Slot.unknown());
            }
            return slots;
        }
        for (DayTimeLayout.Visit visit : visits) {
            slots.add(new Slot(visit.start(), visit.end(), visit.stayMinutes(), "ESTIMATED"));
        }
        return slots;
    }

    // 여기서 나온 시각은 화면에 나가지 않는다 — 순서를 정할 때 "이 자리쯤에서 문이 열려
    // 있나" 를 물어보기 위한 임시 눈금일 뿐이다. 실제로 항목에 박히는 시각은 구간을 만든 뒤
    // layoutDay 가 이동 시간까지 넣어 다시 깐다. 둘을 헷갈리면 이동 시간이 0인 시각표로 돌아간다.
    // 어느 쪽이든 등급은 ESTIMATED 다 — 영업시간이나 실제 이동 소요를 본 값이 아니라
    // 활동 시간대를 항목 수로 나눈 것뿐이라, VERIFIED 로 적으면 화면이 둘을 구분할 수 없다.
    private static Slot slotFor(DayWindow window, int index, int countToday) {
        if (!window.known() || countToday <= 0) {
            return Slot.unknown();
        }
        LocalTime windowStart = window.start();
        long windowMinutes = window.minutes();
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
        String requestIdString = draft.requestId().toString();
        return persist(draft, requestIdString, requestIdString);
    }

    /**
     * 추천 코스 2안·3안을 <b>고른 순간</b> 일정으로 만든다 (S15P21E201-1454). 초안은
     * {@link TripCourseService} 가 1안과 같은 추천 결과로 조립한 것이다.
     *
     * <p>🔴 판의 {@code source_request_id} 를 <b>비운다.</b> 그 칸은 「추천 요청 하나에 일정 하나」를
     * 유일 색인({@code uq_itinerary_version_source_request})으로 지키고, 그 자리는 이미 1안이
     * 차지하고 있다. 같은 요청 번호로 넣으면 두 번째 INSERT 가 거기서 실패한다. 어느 추천에서 나왔는지는
     * 항목의 {@code source_request_id}(유일하지 않다)에 그대로 남는다.
     *
     * <p>대신 판의 {@code request_id} 에 {@code courseLabel} 을 적는다. 같은 안을 두 번 골랐을 때
     * 새로 만들지 않고 그 일정을 돌려주는 데 쓴다({@link TripCourseService}).
     *
     * <p>{@link #persist(ItineraryDraft)} 와 달리 트랜잭션을 여기서 연다 — 이 경로에는 감싸 줄 추천
     * 작업이 없다. 일정과 여행 상태가 같이 반영되거나 같이 안 돼야 한다.
     */
    @Transactional
    public ItineraryHandle persistAlternative(ItineraryDraft draft, String courseLabel) {
        return persist(draft, courseLabel, null);
    }

    private ItineraryHandle persist(ItineraryDraft draft, String versionRequestId, String sourceRequestId) {
        String itineraryId = UUID.randomUUID().toString();
        String itineraryVersionId = UUID.randomUUID().toString();
        Instant now = this.clock.instant();

        Itinerary itinerary = new Itinerary(itineraryId, draft.tripId(), 1);
        ItineraryVersion.Versions versions = new ItineraryVersion.Versions(
                draft.modelVersion(), draft.featureVersion(), draft.ontologyVersion(),
                draft.policyVersion(), draft.datasetVersion());
        ItineraryVersion firstVersion = new ItineraryVersion(itineraryVersionId, itineraryId, 1, null,
                ItineraryVersion.Operation.CREATE, draft.userId(), versionRequestId, versions, now,
                sourceRequestId, draft.warningCodes());

        DraftContent content = contentOf(draft, itineraryVersionId, now);

        // 판과 내용을 한 번에 넘긴다. 판을 먼저 만들고 내용을 나중에 넣으면 그 두 걸음 사이가
        // "판은 있는데 내용이 없는" 상태다.
        this.itineraryRepository.create(itinerary, firstVersion, content.items(), content.legs());
        markTripReady(draft.tripId(), now);

        // 🔴 «이 알림이 이 기능의 이유다.» 일정 만들기는 오래 걸려서 사람이 앱을 닫고 기다린다.
        //    다 됐다는 것을 폰이 알려 주지 않으면, 사람은 몇 분마다 앱을 열어 확인하거나 잊는다.
        //    그래서 CREATE 만은 «만든 본인에게도» 간다 (TripPushNotifier.onItineraryChanged).
        this.events.publishEvent(new ItineraryChangedByMember(
                itineraryId, 1, ItineraryVersion.Operation.CREATE, draft.userId()));

        return new ItineraryHandle(itineraryId, 1);
    }

    /** 초안을 옮긴 판 하나의 항목·구간. */
    record DraftContent(List<ItineraryItem> items, List<ItineraryLeg> legs) {
    }

    /**
     * 초안을 판 하나의 항목·구간으로 옮긴다. 저장하지 않는다.
     *
     * <p>저장({@link #persist})과 추천 코스 미리보기({@link TripCourseService}, S15P21E201-1454)가 이
     * 한 벌을 같이 쓴다. 둘이 따로 옮기면 <b>미리 본 코스와 골라서 만들어진 일정이 어긋난다</b> —
     * 칸 하나(요금·선형)를 한쪽만 넘기는 식으로. 그 어긋남은 S15P21E201-1498 에서 이미 한 번 났다.
     */
    DraftContent contentOf(ItineraryDraft draft, String itineraryVersionId, Instant now) {
        String requestIdString = draft.requestId().toString();

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
                    // 요금을 여기서 흘리면 일정을 처음 만들 때 받은 값이 통째로 사라진다.
                    // 다시 계산하는 경로(ItineraryLegPlanner.toLeg)는 넘기고 있어서, 같은 칸이
                    // 어느 경로로 만들어졌느냐로 값이 갈렸다 (S15P21E201-1498).
                    // 선형도 같은 자리에서 같은 이유로 넘긴다 (S15P21E201-1251).
                    draftLeg.dataStatus(), draftLeg.fareKrw(), draftLeg.path(),
                    draftLeg.uncalibratedDurationMin(), now));
        }
        return new DraftContent(items, legs);
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
        // 그날의 활동 시간대 — 오늘 오후에 만든 오늘 여행의 첫날은 처음 만든 시각부터다(S15P21E201-1734). 고치는 지금이 아니라
        // 처음 만든 시각을 쓴다: 재계산은 들렀는지를 모른 채 그날을 통째로 다시 깔아서, 지금을 쓰면 오전에 들른 곳이 오후로 밀린다.
        Instant madeAt = this.itineraryRepository.findVersion(command.itineraryId(), 1)
                .map(ItineraryVersion::createdAt)
                .orElse(now);
        DayWindow window = DayWindow.of(trip, day, madeAt);

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
        //    넣을 시간이 없어 비운 첫날(S15P21E201-1734)은 채우지 않는다.
        int target = window.noTimeLeft() ? kept.size() : dayItems.isEmpty() ? this.maxItemsPerDay : dayItems.size();
        int vacancies = Math.max(0, target - kept.size());
        List<ItineraryDraftCommand.PlannedPlace> fills = new ArrayList<>(vacancies);
        // 새 일정과 같은 규칙 — 그 날 안 여는 행사 장소(끝난 축제 등)로 빈자리를 채우지 않는다.
        Map<UUID, Set<Integer>> eventDays = eventDaysOf(trip, command.rankedPool());
        // 새 일정과 같은 규칙 — 저녁 날은 식당과 밤에 볼 만한 곳으로만 채운다.
        Set<UUID> nightFriendly = window.evening() ? nightFriendly(command.rankedPool()) : Set.of();
        for (ItineraryDraftCommand.PlannedPlace candidate : command.rankedPool()) {
            if (fills.size() >= vacancies) {
                break;
            }
            Set<Integer> openDays = eventDays.get(candidate.placeId());
            if (openDays != null && !openDays.contains(dayIndex)) {
                continue;
            }
            if (window.evening() && !isFood(candidate) && !nightFriendly.contains(candidate.placeId())) {
                continue;
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
        Map<UUID, String> categoryToday = new HashMap<>();
        for (Place place : this.placeRepository.findByPlaceIdIn(orderedToday)) {
            categoryToday.put(place.getPlaceId(), place.getCategory());
        }
        LocalTime[][] mealBandsToday = mealSlotBands(window, countToday);
        List<DayTimeLayout.Stop> stopsToday = new ArrayList<>(countToday);
        for (int i = 0; i < countToday; i++) {
            String category = categoryToday.get(orderedToday.get(i));
            boolean meal = mealBandsToday[i] != null && category != null
                    && category.equalsIgnoreCase(this.foodCategory);
            stopsToday.add(new DayTimeLayout.Stop(stayMinutesFor(category),
                    meal ? mealBandsToday[i][0] : null, meal ? mealBandsToday[i][1] : null));
        }
        List<Slot> timedToday = layoutDay(window, stopsToday,
                travelMinutesFor(this.legPlanner.buildLegs(trip, todayOnly), dayIndex, countToday),
                returnMinutesOf(trip, dayIndex, this.legPlanner.lodgingOf(trip), orderedToday));

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
                    // 판을 새로 만들 때도 같다 — 요금이 판 하나 넘어갈 때마다 사라지면
                    // 편집한 일정만 조용히 비용을 잃는다 (S15P21E201-1498).
                    // 선형도 마찬가지다 (S15P21E201-1251).
                    draftLeg.dataStatus(), draftLeg.fareKrw(), draftLeg.path(),
                    draftLeg.uncalibratedDurationMin(), now));
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
