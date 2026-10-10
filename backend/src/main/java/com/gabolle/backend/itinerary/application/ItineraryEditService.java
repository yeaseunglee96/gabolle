package com.gabolle.backend.itinerary.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.ItineraryItemActualRepository;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.itinerary.application.port.PlaceEventSchedule;
import com.gabolle.backend.itinerary.application.port.PlaceEventSchedulePort;
import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryExclusion;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;
import com.gabolle.backend.itinerary.domain.ItineraryChangedByMember;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정 편집 — 새 판을 만든다. 기존 판은 절대 고치지 않는다.
 * 바탕 판의 내용을 {@link ItineraryRevision} 으로 복사해 판과 함께 저장한다. 판만 만들면
 * 항목이 하나도 없는 판이 최신이 되어 조회가 빈 일정을 돌려준다.
 * 포인터 이동({@code Itinerary.moveTo})은 여기서 안 부른다 — 저장소가 판 INSERT 와 같은
 * 트랜잭션에서 조건부로 옮긴다({@link ItineraryRepository} javadoc).
 * {@link PlaceEventSchedulePort} 는 선택 의존성이 아니라 필수다. 구현체가 없는 컨텍스트에서
 * 기동이 실패하는 것이 의도다 — 조용히 건너뛰게 만들면 배선이 빠진 채 배포돼도 아무 테스트도
 * 빨개지지 않고 축제 날짜 검사만 사라진다.
 * 그래서 {@code @Profile({"db","dev"})} 를 함께 붙였다. 편집 경로가 아예 없는 {@code no-db}
 * 프로필에서 이 서비스만 뜨려다 기동이 실패하는 것은 잡아야 할 결함이 아니다.
 */
@Service
@Profile({ "db", "dev" })
public class ItineraryEditService {

    private final ItineraryRepository repository;

    private final PlaceEventSchedulePort eventSchedule;

    /**
     * 순서를 바꾼 뒤 그날 구간을 다시 만드는 데 쓴다.
     * 구간 계산을 여기서 다시 쓰지 않고 생성 경로와 같은 코드를 부른다 — 규칙이 적지 않아서
     * (출발지 처리, 이동수단 고르기, 길찾기 실패 시 어림값) 사본을 두면 언젠가 한쪽만 고쳐지고
     * 두 경로가 다른 답을 낸다.
     */
    private final ItineraryLegPlanner legPlanner;

    /**
     * 여행의 출발 좌표와 이동수단을 읽으려고 받는다. 구간의 첫 자리는 여행 출발지에서
     * 시작하고, 이동수단은 걷기 거리를 채울지 말지를 가른다.
     */
    private final TripRepository tripRepository;

    /**
     * 순서를 바꾼 뒤 그날 방문 시각이 영업시간을 어기는지 본다.
     * {@link PlaceEventSchedulePort} 와 같은 이유로 선택 의존성으로 두지 않는다 — 없을 때 조용히
     * 건너뛰면 배선이 빠진 채 배포돼도 경고만 소리 없이 사라진다.
     */
    private final ItineraryOpeningHoursChecker openingHours;

    private final Clock clock;

    /**
     * 재계획이 "지금까지 실제로 어땠나" 를 읽는 문. {@link ItineraryDelayProjector} 가 예상
     * 시각을 세우려면 실제 도착·출발 기록이 있어야 한다.
     */
    private final ItineraryItemActualRepository actualRepository;

    /**
     * 「새 판이 붙었다」를 알리는 자리. 듣는 쪽은 동행자 폰에 알림을 띄우는
     * {@code TripPushNotifier} 하나이고, 커밋이 끝난 뒤에만 받는다 (S15P21E201-1391).
     *
     * <p>이 서비스가 알림을 «직접» 부르지 않는 이유는 되돌아오는 화살표 때문이다 — 알림이
     * 일정을 알면, 일정도 알림을 알게 된다. 사건을 내놓기만 하면 듣는 쪽이 늘어도 여기는 안 바뀐다.
     */
    private final ApplicationEventPublisher events;

    public ItineraryEditService(ItineraryRepository repository, PlaceEventSchedulePort eventSchedule,
                                ItineraryLegPlanner legPlanner, TripRepository tripRepository,
                                ItineraryOpeningHoursChecker openingHours, Clock clock,
                                ItineraryItemActualRepository actualRepository,
                                ApplicationEventPublisher events) {
        this.repository = repository;
        this.eventSchedule = eventSchedule;
        this.legPlanner = legPlanner;
        this.tripRepository = tripRepository;
        this.openingHours = openingHours;
        this.clock = clock;
        this.actualRepository = actualRepository;
        this.events = events;
    }

    /**
     * 항목 하나를 고정하거나 푼다.
     * 순서가 곧 정확성이다 — {@code baseVersion} 이 최신인지 먼저 보고(아니면 그 자리에서 409,
     * 어차피 버려질 복사를 하지 않는다), 바탕 판의 내용을 읽어 {@code locked} 만 바꾼 복사본을
     * 만들고, 판과 내용을 한 트랜잭션으로 저장한다.
     * 사전 확인만으로는 부족하다 — 확인과 저장 사이에 다른 요청이 끼어들 수 있어서 저장소의
     * 판 번호 UNIQUE 와 포인터 조건부 갱신이 마지막 방어선이다.
     *
     * @param itemKey {@code itinerary_item.item_key} — 판을 건너 같은 항목을 가리키는 값이다. PK 가 아니다
     * @throws StaleItineraryVersionException 그 사이 다른 편집이 있었다 (409)
     * @throws ItineraryRevision.ItemNotFoundException 바탕 판에 그 항목이 없다 (404)
     * @throws NoSuchElementException 그런 일정이 없다 (404)
     */
    @Transactional
    public ItineraryVersion setItemLocked(String itineraryId, String itemKey, boolean locked,
                                          int baseVersion, String editorUserId) {
        return setItemLocked(itineraryId, itemKey, locked, null, baseVersion, editorUserId);
    }

    /**
     * 고정 방식까지 정해 고정한다(S15P21E201-2017) — {@code mode} 는 순서 고정·시각 고정. {@code null} 이면 순서 고정.
     */
    @Transactional
    public ItineraryVersion setItemLocked(String itineraryId, String itemKey, boolean locked,
                                          ItineraryItem.LockMode mode, int baseVersion, String editorUserId) {

        Itinerary itinerary = repository.findById(itineraryId)
                .orElseThrow(() -> new NoSuchElementException("일정을 찾을 수 없습니다: " + itineraryId));

        // 다음 판 번호는 latestVersion 이 아니라 검증된 baseVersion 에서 나온다 — 저장소의 포인터 조건과 짝이 맞아야 한다.
        int next = itinerary.nextVersionFrom(baseVersion);

        // 바탕 판의 내용이 없으면 조용히 빈 판을 만들지 않고 시끄럽게 실패한다.
        ItineraryContent base = repository.findContent(itineraryId, baseVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "바탕 판의 내용이 없습니다: itineraryId=" + itineraryId + ", version=" + baseVersion));

        Instant now = clock.instant();
        String newVersionId = UUID.randomUUID().toString();
        ItineraryRevision.Draft draft =
                ItineraryRevision.setLocked(base, newVersionId, itemKey, locked, mode, now);

        ItineraryVersion candidate = new ItineraryVersion(
                newVersionId,
                itineraryId,
                next,
                baseVersion,
                // 푸는 것도 LOCK_ITEM 이다. ck_itinerary_version_operation 에 UNLOCK_ITEM 이 없고,
                //    결과는 항목의 locked 값에 남는다. 값을 더하려면 마이그레이션이 필요하다.
                ItineraryVersion.Operation.LOCK_ITEM,
                editorUserId,
                // 사용자 편집은 추천 요청에서 나온 것이 아니라 requestId 가 없다. 그래도 판마다
                //    요청 식별자가 있어야 해서 편집마다 새로 만든다.
                "req_edit_" + UUID.randomUUID(),
                // 재계산을 안 했으므로 판 값 다섯이 없다. Versions.isComplete() 가 false 인 값을
                //    넣는다 — 지어낸 값을 넣으면 "이 일정은 어느 판으로 만들었나" 에 거짓으로 답한다.
                new ItineraryVersion.Versions(null, null, null, null, null),
                now);

        // 저장과 포인터 이동은 둘 다 저장소가 한 트랜잭션 안에서 한다.
        // draft.exclusions() 는 바탕 판의 제외 목록을 그대로 물려받은 것이다 — 고정·해제가
        //    제외 목록을 지우지 않는다.
        return appendAndAnnounce(candidate, draft.items(), draft.legs(), draft.exclusions());
    }

    /**
     * 사용자가 고른 장소를 그 날의 마지막에 더한 새 판을 만든다.
     * 시각은 여기서 정하지 않는다 — 시각과 이동 구간은 이 호출 뒤에 접수되는 그 날짜 재계산
     * Job 이 정한다({@link ItineraryRevision#withAddedItem} 참고).
     * 재계산 Job 접수를 같은 트랜잭션에 묶지 않는다. 묶으면 접수가 실패했을 때 판까지 되돌아가는데,
     * 판은 이미 사용자가 요청한 사실이라 남아야 하고 재계산은 다시 부를 수 있다.
     * 새 항목은 그 날의 마지막에 붙으므로 기존 항목의 순번이 하나도 바뀌지 않는다 — 밀려나는
     * 방문지가 생기지 않는다.
     *
     * @param tripStartDate 여행 첫날. {@code dayIndex} 를 실제 날짜로 바꾸는 기준이고, 축제가
     *     여행 기간과 겹치는지 판정하는 구간의 시작이다
     * @param tripFinishDate 여행 마지막 날. "요청한 날에만 안 열린다" 와 "이 여행 내내 안 열린다" 를
     *     가르려면 여행 기간 전체가 필요하다
     * @throws StaleItineraryVersionException 그 사이 다른 편집이 있었다 (409)
     * @throws NoSuchElementException 그런 일정이 없다 (404)
     * @throws PlaceNotOpenDuringTripException 기간이 정해진 장소인데 여행 기간과 전혀 안 겹친다 (422)
     * @throws PlaceClosedOnDayException 겹치는 날은 있는데 요청한 날에는 안 열린다 (400)
     * @throws PlaceAlreadyInItineraryException 기간이 정해진 장소가 이미 이 판에 들어 있다 (409)
     */
    @Transactional
    public ItineraryVersion addPlace(String itineraryId, String placeId, int dayIndex,
                                     LocalDate tripStartDate, LocalDate tripFinishDate,
                                     int baseVersion, String editorUserId) {

        Itinerary itinerary = repository.findById(itineraryId)
                .orElseThrow(() -> new NoSuchElementException("일정을 찾을 수 없습니다: " + itineraryId));

        int next = itinerary.nextVersionFrom(baseVersion);

        ItineraryContent base = repository.findContent(itineraryId, baseVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "바탕 판의 내용이 없습니다: itineraryId=" + itineraryId + ", version=" + baseVersion));

        LocalDate visitDate = tripStartDate.plusDays(dayIndex);
        requireAddable(base, placeId, dayIndex, visitDate, tripStartDate, tripFinishDate);

        Instant now = clock.instant();
        String newVersionId = UUID.randomUUID().toString();
        ItineraryRevision.Draft draft =
                ItineraryRevision.withAddedItem(base, newVersionId, placeId, dayIndex, visitDate, now);

        ItineraryVersion candidate = new ItineraryVersion(
                newVersionId,
                itineraryId,
                next,
                baseVersion,
                ItineraryVersion.Operation.ADD_ITEM,
                editorUserId,
                "req_edit_" + UUID.randomUUID(),
                // 엔진을 돌리지 않았으므로 판 값 다섯을 비운다.
                new ItineraryVersion.Versions(null, null, null, null, null),
                now);

        return appendAndAnnounce(candidate, draft.items(), draft.legs(), draft.exclusions());
    }

    /**
     * 하루 안의 방문 순서를 바꾼다.
     * 순서는 {@link #setItemLocked} 와 똑같고 그 자리의 세 장치(사전 확인 · 판 번호 UNIQUE ·
     * 포인터 조건부 갱신)를 그대로 물려받는다.
     * 무엇이 어떻게 바뀌는지는 {@link ItineraryRevision#withReorderedDay} 에 있다 — 시각표는
     * 그대로 두고 자리만 바꿔 앉히며, 그날의 구간은 버린다.
     *
     * @param dayOrder 그날 항목의 {@code itemKey} 를 원하는 순서대로 전부
     * @throws StaleItineraryVersionException 그 사이 다른 편집이 있었다 (409)
     * @throws ItineraryRevision.DayOrderMismatchException 목록이 그날의 항목과 안 맞는다 (400)
     * @throws ItineraryRevision.LockedItemMovedException 고정된 항목이 옮겨진다 (409)
     * @throws NoSuchElementException 그런 일정이 없다 (404)
     */
    @Transactional
    public ReorderOutcome reorderDay(String itineraryId, int dayIndex, List<String> dayOrder,
                                     int baseVersion, String editorUserId) {

        Itinerary itinerary = repository.findById(itineraryId)
                .orElseThrow(() -> new NoSuchElementException("일정을 찾을 수 없습니다: " + itineraryId));

        int next = itinerary.nextVersionFrom(baseVersion);

        ItineraryContent base = repository.findContent(itineraryId, baseVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "바탕 판의 내용이 없습니다: itineraryId=" + itineraryId + ", version=" + baseVersion));

        Instant now = clock.instant();
        String newVersionId = UUID.randomUUID().toString();
        ItineraryRevision.Draft draft =
                ItineraryRevision.withReorderedDay(base, newVersionId, dayIndex, dayOrder, now);

        List<ItineraryLeg> legs = withRebuiltDayLegs(itinerary, draft, dayIndex, newVersionId, now);

        ItineraryVersion candidate = new ItineraryVersion(
                newVersionId,
                itineraryId,
                next,
                baseVersion,
                ItineraryVersion.Operation.REORDER,
                editorUserId,
                "req_edit_" + UUID.randomUUID(),
                // 엔진을 돌리지 않았으므로 판 값 다섯을 비운다.
                new ItineraryVersion.Versions(null, null, null, null, null),
                now);

        ItineraryVersion saved = appendAndAnnounce(candidate, draft.items(), legs, draft.exclusions());

        // 판정은 저장한 뒤에 한다. 위반이 있어도 순서 바꾸기는 성공해야 한다 — 경고가 순서를 막는 자리에 있으면 안 된다.
        return new ReorderOutcome(saved, this.openingHours.checkDay(draft.items(), dayIndex));
    }

    // ── 일정 조정 — 머무는 시간 바꾸기 · 고른 자리에 더하기 (S15P21E201-2017) ─────────────────────────────

    /** 머무는 시간의 눈금 — 15분. */
    public static final int STAY_STEP_MINUTES = 15;

    /** 머무는 시간의 최소 — 15분. */
    public static final int MIN_ADJUSTED_STAY_MINUTES = 15;

    /** 머무는 시간의 최대 — 12시간. 그보다 길면 하루 일정이 아니다. */
    public static final int MAX_ADJUSTED_STAY_MINUTES = 12 * 60;

    /** 하루 활동 시간의 끝을 넘겼다 — 저장은 하되 알린다. */
    public static final String NOTICE_DAY_END_EXCEEDED = "DAY_END_EXCEEDED";

    /** 시각 고정한 곳에 제 시각에 못 닿아 밀렸다. */
    public static final String NOTICE_TIME_FIX_MISSED = "TIME_FIX_MISSED";

    /**
     * 새로 더한 곳의 머무는 시간을 갈래로 정하려고 장소를 읽는다. 생성자에 넣지 않은 이유 — 이 서비스를 손으로 만드는 시험이
     * 둘 있고, 없을 때는 갈래를 모르는 곳의 기본값({@link StayDefaults#UNKNOWN_CATEGORY_MINUTES})으로 충분하다.
     */
    private PlaceRepository placeRepository;

    @Autowired(required = false)
    void setPlaceRepository(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    /**
     * 편집이 스스로 알게 된 것 — 하루 끝을 넘김, 시각 고정을 못 지킴. 영업시간 경고와 같은 칸으로 나간다.
     *
     * @param at 그 곳의 날짜와 시각(부산). 모르면 {@code null}
     */
    public record Notice(String code, String itemKey, String placeId, LocalDateTime at) {
    }

    /** 조정이 만든 것 — 새 판, 영업시간 판정, 그리고 스스로 알게 된 것. */
    public record AdjustOutcome(ItineraryVersion version, ItineraryOpeningHoursChecker.Result openingHours,
            List<Notice> notices) {
    }

    /**
     * 한 곳에 머무는 시간을 바꾸고, 그날 뒤따르는 곳의 시각을 다시 매긴다. 장소·순서·구간은 그대로다.
     * 곳 사이 간격(이동 + 빈 시각)은 그대로 두고 앞에서부터 민다 — 규칙은 {@link ItineraryDayRetimer}.
     * 하루 끝을 넘기거나 시각 고정을 못 지켜도 저장은 한다 — 사용자가 고른 값이다. 대신 {@link Notice} 로 알린다.
     *
     * @throws InvalidStayException 15분 단위가 아니거나 15분 미만·12시간 초과다 (400)
     * @throws ReplanOverflowsDayException 자정을 넘긴다 — 아무것도 저장하지 않는다 (422)
     */
    @Transactional
    public AdjustOutcome changeStay(String itineraryId, String itemKey, int stayMinutes, int baseVersion,
                                    String editorUserId) {
        if (stayMinutes < MIN_ADJUSTED_STAY_MINUTES || stayMinutes > MAX_ADJUSTED_STAY_MINUTES
                || stayMinutes % STAY_STEP_MINUTES != 0) {
            throw new InvalidStayException(stayMinutes);
        }
        Itinerary itinerary = repository.findById(itineraryId)
                .orElseThrow(() -> new NoSuchElementException("일정을 찾을 수 없습니다: " + itineraryId));
        int next = itinerary.nextVersionFrom(baseVersion);
        ItineraryContent base = repository.findContent(itineraryId, baseVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "바탕 판의 내용이 없습니다: itineraryId=" + itineraryId + ", version=" + baseVersion));
        ItineraryItem target = base.items().stream()
                .filter((item) -> item.itemKey().equals(itemKey))
                .findFirst()
                .orElseThrow(() -> new ItineraryRevision.ItemNotFoundException(itemKey));
        int dayIndex = target.dayIndex();

        Instant now = clock.instant();
        String newVersionId = UUID.randomUUID().toString();
        ItineraryRevision.Draft copied = ItineraryRevision.copyOf(base, newVersionId, now);
        Retimed retimed = retimeDay(itinerary, base, copied.items(), copied.legs(), dayIndex,
                Map.of(itemKey, stayMinutes), Set.of());

        ItineraryRevision.Draft draft = ItineraryRevision.withRetimedDay(
                new ItineraryContent(null, retimed.items(), copied.legs(), copied.exclusions()), newVersionId,
                dayIndex, retimed.result().starts(), retimed.result().ends(), now);

        ItineraryVersion candidate = new ItineraryVersion(newVersionId, itineraryId, next, baseVersion,
                // 시각만 다시 매기는 편집이라 REPLAN_DAY 로 남긴다. 따로 CHANGE_STAY 를 두지 않은 이유 — 판 종류를 더하면 폰 알림
                //    문구(PushCopy)의 모든 언어 분기를 함께 고쳐야 하는데, 그 파일은 베트남어 작업이 잡고 있었다(2026-10-09).
                ItineraryVersion.Operation.REPLAN_DAY, editorUserId, "req_edit_" + UUID.randomUUID(),
                new ItineraryVersion.Versions(null, null, null, null, null), now);
        ItineraryVersion saved = appendAndAnnounce(candidate, draft.items(), draft.legs(), draft.exclusions());
        return new AdjustOutcome(saved, this.openingHours.checkDay(draft.items(), dayIndex),
                noticesFor(itinerary, draft.items(), dayIndex, retimed.result()));
    }

    /**
     * 고른 자리에 장소를 끼워 넣고, 그날 구간을 다시 만들고, 시각을 바로 매긴다. 검사는 {@link #addPlace} 와 같다.
     * 끼운 곳의 머무는 시간은 장소 갈래의 기본값({@link StayDefaults})이다.
     *
     * @param position 그날 안의 순번(1부터). 그날 곳 수보다 크면 맨 끝
     */
    @Transactional
    public AdjustOutcome insertPlace(String itineraryId, String placeId, int dayIndex, int position,
                                     LocalDate tripStartDate, LocalDate tripFinishDate,
                                     int baseVersion, String editorUserId) {
        Itinerary itinerary = repository.findById(itineraryId)
                .orElseThrow(() -> new NoSuchElementException("일정을 찾을 수 없습니다: " + itineraryId));
        int next = itinerary.nextVersionFrom(baseVersion);
        ItineraryContent base = repository.findContent(itineraryId, baseVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "바탕 판의 내용이 없습니다: itineraryId=" + itineraryId + ", version=" + baseVersion));
        LocalDate visitDate = tripStartDate.plusDays(dayIndex);
        requireAddable(base, placeId, dayIndex, visitDate, tripStartDate, tripFinishDate);
        // 같은 날에 이미 있는 곳은 끼우지 않는다 — 바로 옆에 끼우면 「같은 장소로의 구간」이 생겨 구간 계산이 실패했고(운영 500,
        //    2026-10-09), 하루에 같은 곳을 두 번 들르는 것은 고른 자리를 잘못 누른 경우가 거의 전부다. 다른 날에는 넣을 수 있다.
        if (base.items().stream().anyMatch((item) -> item.dayIndex() == dayIndex && placeId.equals(item.placeId()))) {
            throw new PlaceAlreadyInItineraryException(placeId, dayIndex, List.of(dayIndex));
        }

        Instant now = clock.instant();
        String newVersionId = UUID.randomUUID().toString();
        ItineraryRevision.Draft added = ItineraryRevision.withAddedItem(base, newVersionId, placeId, dayIndex,
                visitDate, position, now);
        // withAddedItem 은 그날 옛 구간까지 복사해 준다. 끼우면 순서가 바뀌어 옛 구간은 참이 아니고, 그대로 두면 다시 만든 구간과
        //    (판·날·순번) 자리가 겹쳐 uq_itinerary_leg_slot 으로 500 이 난다(운영 2026-10-09). 순서 바꾸기처럼 그날 것은 버리고 다시 만든다.
        ItineraryRevision.Draft withoutDayLegs = new ItineraryRevision.Draft(added.items(),
                added.legs().stream().filter((leg) -> leg.dayIndex() != dayIndex).toList(), added.exclusions());
        List<ItineraryLeg> legs = withRebuiltDayLegs(itinerary, withoutDayLegs, dayIndex, newVersionId, now);

        Set<String> baseKeys = new HashSet<>();
        base.items().forEach((item) -> baseKeys.add(item.itemKey()));
        String newKey = added.items().stream().filter((item) -> !baseKeys.contains(item.itemKey()))
                .map(ItineraryItem::itemKey).findFirst().orElseThrow();
        Retimed retimed = retimeDay(itinerary, base, added.items(), legs, dayIndex,
                Map.of(newKey, stayFor(placeId)), Set.of(newKey));

        ItineraryRevision.Draft draft = ItineraryRevision.withRetimedDay(
                new ItineraryContent(null, retimed.items(), legs, added.exclusions()), newVersionId, dayIndex,
                retimed.result().starts(), retimed.result().ends(), now);

        ItineraryVersion candidate = new ItineraryVersion(newVersionId, itineraryId, next, baseVersion,
                ItineraryVersion.Operation.ADD_ITEM, editorUserId, "req_edit_" + UUID.randomUUID(),
                new ItineraryVersion.Versions(null, null, null, null, null), now);
        ItineraryVersion saved = appendAndAnnounce(candidate, draft.items(), draft.legs(), draft.exclusions());
        return new AdjustOutcome(saved, this.openingHours.checkDay(draft.items(), dayIndex),
                noticesFor(itinerary, draft.items(), dayIndex, retimed.result()));
    }

    private record Retimed(List<ItineraryItem> items, ItineraryDayRetimer.Result result) {
    }

    /**
     * 그날을 다시 매긴다. 곳 사이 간격은 바탕 판에서 «앞 곳이 그대로인» 곳만 물려받고, 앞 곳이 바뀐 곳(끼운 곳과 그 바로 뒤)은
     * 새 구간의 이동 분만 둔다 — 끼워 넣으면 그 자리의 빈 시각을 먼저 쓴다.
     * 하루 시작은 바탕 판 그날 첫 곳의 시작이다. 그것도 모르면 여행의 활동 시작, 그것도 모르면 다시 매기지 않는다.
     *
     * @param stayOverride 머무는 시간을 바꿀 곳
     * @param fresh        새로 생긴 곳
     * @return 항목들(시각을 못 매기는 날이면 바꾼 머무는 시간만 들어간 것)과 다시 매긴 결과
     * @throws ReplanOverflowsDayException 자정을 넘긴다
     */
    private Retimed retimeDay(Itinerary itinerary, ItineraryContent base, List<ItineraryItem> items,
                              List<ItineraryLeg> legs, int dayIndex, Map<String, Integer> stayOverride,
                              Set<String> fresh) {
        List<ItineraryItem> baseDay = base.items().stream().filter((item) -> item.dayIndex() == dayIndex)
                .sorted(Comparator.comparingInt(ItineraryItem::sequence)).toList();
        Map<String, ItineraryItem> baseByKey = new HashMap<>();
        Map<String, String> basePredecessor = new HashMap<>();
        for (int i = 0; i < baseDay.size(); i++) {
            baseByKey.put(baseDay.get(i).itemKey(), baseDay.get(i));
            basePredecessor.put(baseDay.get(i).itemKey(), i == 0 ? null : baseDay.get(i - 1).itemKey());
        }
        Map<Integer, Integer> travelBySequence = new HashMap<>();
        for (ItineraryLeg leg : legs) {
            if (leg.dayIndex() == dayIndex && leg.durationMin() != null) {
                travelBySequence.put(leg.sequence(), Math.max(0, leg.durationMin()));
            }
        }

        // 시각을 못 매기는 날에도 바꾼 머무는 시간은 남아야 한다 — 시각이 없는 곳에는 값만 넣어 둔다.
        List<ItineraryItem> withStay = new ArrayList<>(items.size());
        for (ItineraryItem item : items) {
            Integer stay = stayOverride.get(item.itemKey());
            withStay.add(stay == null || item.startTime() != null ? item : new ItineraryItem(item.itineraryItemId(),
                    item.itineraryVersionId(), item.itemKey(), item.dayIndex(), item.visitDate(), item.sequence(),
                    item.placeId(), null, null, stay, item.locked(), item.estimatedCostKrw(), item.dataStatus(),
                    item.reasonCodes(), item.warningCodes(), item.sourceRequestId(), item.createdAt(),
                    item.lockMode()));
        }

        List<ItineraryItem> day = withStay.stream().filter((item) -> item.dayIndex() == dayIndex)
                .sorted(Comparator.comparingInt(ItineraryItem::sequence)).toList();
        LocalTime dayStart = baseDay.isEmpty() ? null : baseDay.get(0).startTime();
        if (dayStart == null) {
            Trip trip = tripRepository.findById(itinerary.tripId()).orElse(null);
            DayWindow window = trip == null ? null : DayWindow.of(trip);
            dayStart = (window != null && window.known()) ? window.start() : null;
            // 🔴 활동 시작은 «출발지를 나서는» 시각이다 — 첫 곳에는 출발지에서 가는 이동 뒤에 닿는다(S15P21E201-2043).
            //    직접 짜기에서 서면역→해운대 45분을 빼고 첫 곳을 09:00 에 세웠다. 바탕 판에 첫 곳이 있으면 그 시각이 이미 이동을 품고 있다.
            if (dayStart != null && !day.isEmpty()) {
                dayStart = arrivalAfterOriginLeg(dayStart, travelBySequence.get(day.get(0).sequence()));
            }
        }
        if (dayStart == null || day.isEmpty()) {
            return new Retimed(withStay, new ItineraryDayRetimer.Result(Map.of(), Map.of(), List.of(), List.of()));
        }

        List<ItineraryDayRetimer.Stop> stops = new ArrayList<>(day.size());
        for (int i = 0; i < day.size(); i++) {
            ItineraryItem item = day.get(i);
            String previousKey = i == 0 ? null : day.get(i - 1).itemKey();
            int lead = travelBySequence.getOrDefault(item.sequence(), 0);
            boolean samePredecessor = !fresh.contains(item.itemKey()) && baseByKey.containsKey(item.itemKey())
                    && Objects.equals(basePredecessor.get(item.itemKey()), previousKey);
            if (i > 0 && samePredecessor) {
                ItineraryItem original = baseByKey.get(item.itemKey());
                ItineraryItem originalPrevious = baseByKey.get(previousKey);
                if (original.startTime() != null && originalPrevious != null && originalPrevious.endTime() != null) {
                    long gap = Duration.between(originalPrevious.endTime(), original.startTime()).toMinutes();
                    if (gap >= 0) {
                        lead = (int) gap;
                    }
                }
            }
            stops.add(new ItineraryDayRetimer.Stop(item.itemKey(), item.startTime(), stayOf(item, stayOverride),
                    item.timeFixed(), lead));
        }
        ItineraryDayRetimer.Result result = ItineraryDayRetimer.retime(dayStart, stops);
        if (!result.overflowing().isEmpty()) {
            throw new ReplanOverflowsDayException(dayIndex, result.overflowing());
        }
        return new Retimed(withStay, result);
    }

    /** 출발지를 나선 시각에 첫 구간 이동을 더한 도착 시각. 이동을 모르거나 자정을 넘기면 나선 시각 그대로. */
    static LocalTime arrivalAfterOriginLeg(LocalTime leaveAt, Integer originLegMinutes) {
        if (originLegMinutes == null || originLegMinutes <= 0) {
            return leaveAt;
        }
        int arrive = leaveAt.toSecondOfDay() / 60 + originLegMinutes;
        return arrive >= 24 * 60 ? leaveAt : leaveAt.plusMinutes(originLegMinutes);
    }

    private static int stayOf(ItineraryItem item, Map<String, Integer> stayOverride) {
        Integer override = stayOverride.get(item.itemKey());
        if (override != null) {
            return override;
        }
        if (item.stayMinutes() != null) {
            return item.stayMinutes();
        }
        if (item.startTime() != null && item.endTime() != null) {
            return (int) Duration.between(item.startTime(), item.endTime()).toMinutes();
        }
        return StayDefaults.UNKNOWN_CATEGORY_MINUTES;
    }

    private int stayFor(String placeId) {
        if (this.placeRepository == null) {
            return StayDefaults.UNKNOWN_CATEGORY_MINUTES;
        }
        try {
            return this.placeRepository.findById(UUID.fromString(placeId)).map(Place::getCategory)
                    .map(StayDefaults::minutesFor).orElse(StayDefaults.UNKNOWN_CATEGORY_MINUTES);
        }
        catch (IllegalArgumentException e) {
            return StayDefaults.UNKNOWN_CATEGORY_MINUTES;
        }
    }

    /** 시각 고정을 못 지킨 곳과, 하루 활동 시간의 끝을 넘긴 마지막 곳을 알린다. */
    private List<Notice> noticesFor(Itinerary itinerary, List<ItineraryItem> items, int dayIndex,
                                    ItineraryDayRetimer.Result result) {
        List<ItineraryItem> day = items.stream().filter((item) -> item.dayIndex() == dayIndex)
                .sorted(Comparator.comparingInt(ItineraryItem::sequence)).toList();
        List<Notice> notices = new ArrayList<>();
        for (ItineraryItem item : day) {
            if (result.missedTimeFixed().contains(item.itemKey())) {
                notices.add(new Notice(NOTICE_TIME_FIX_MISSED, item.itemKey(), item.placeId(),
                        item.startTime() == null ? null : LocalDateTime.of(item.visitDate(), item.startTime())));
            }
        }
        ItineraryItem last = day.isEmpty() ? null : day.get(day.size() - 1);
        if (last != null && last.endTime() != null) {
            Trip trip = tripRepository.findById(itinerary.tripId()).orElse(null);
            DayWindow window = trip == null ? null : DayWindow.of(trip);
            if (window != null && window.known() && last.endTime().isAfter(window.end())) {
                notices.add(new Notice(NOTICE_DAY_END_EXCEEDED, last.itemKey(), last.placeId(),
                        LocalDateTime.of(last.visitDate(), last.endTime())));
            }
        }
        return notices;
    }

    /** 머무는 시간이 규칙(15분 단위 · 15분 이상 · 12시간 이하)을 어겼다 — 400 ITINERARY_STAY_INVALID. */
    public static class InvalidStayException extends RuntimeException {

        private final int stayMinutes;

        public InvalidStayException(int stayMinutes) {
            super("머무는 시간은 " + STAY_STEP_MINUTES + "분 단위로 " + MIN_ADJUSTED_STAY_MINUTES + "분 이상 "
                    + MAX_ADJUSTED_STAY_MINUTES + "분 이하여야 합니다: " + stayMinutes);
            this.stayMinutes = stayMinutes;
        }

        public int stayMinutes() { return stayMinutes; }
    }

    /**
     * 순서 바꾸기가 만든 것 — 새 판과, 그 결과에 대해 알려 줄 것.
     * 판정한 자리에서 함께 올린다. 판만 돌려주면 부르는 쪽이 경고를 알 길이 없고, 부르는 쪽에서
     * 다시 판정하게 만들면 같은 규칙이 두 곳에 살게 된다.
     */
    public record ReorderOutcome(ItineraryVersion version, ItineraryOpeningHoursChecker.Result openingHours) {
    }

    /**
     * 저장된 판의 그 날짜를 영업시간과 대조한다.
     * 순서 바꾸기는 판정을 {@link ReorderOutcome} 에 실어 올리고, 나머지 편집은 새 판만 돌려주므로
     * 저장한 뒤에 따로 물어본다. 판정 규칙이 이 서비스 안에 남는 것이 요점이다.
     * 판을 한 번 더 읽는다 — 부르는 쪽이 바로 뒤에 일정 전체를 다시 읽으므로 이 한 번이 비용의 전부다.
     */
    public ItineraryOpeningHoursChecker.Result openingHoursForDay(String itineraryId, int version, int dayIndex) {
        return this.openingHours.checkDay(itemsOf(itineraryId, version), dayIndex);
    }

    /**
     * 그 항목이 속한 날짜를 대조한다. 고정·해제가 쓴다 — 요청에 날짜가 없고 항목 열쇠만 온다.
     * 항목을 못 찾으면 판 전체를 본다. 못 찾는 것은 이 편집이 그 항목을 지웠을 때뿐이고,
     * 그때 아무것도 안 보는 것보다 넓게 보는 편이 낫다.
     */
    public ItineraryOpeningHoursChecker.Result openingHoursForItem(String itineraryId, int version, String itemKey) {
        List<ItineraryItem> items = itemsOf(itineraryId, version);
        return items.stream()
                .filter((item) -> itemKey != null && itemKey.equals(item.itemKey()))
                .findFirst()
                .map((item) -> this.openingHours.checkDay(items, item.dayIndex()))
                .orElseGet(() -> this.openingHours.checkAll(items));
    }

    /** 판 전체를 대조한다. 되돌리기가 쓴다 — 한 날이 아니라 판 전체가 바뀐다. */
    public ItineraryOpeningHoursChecker.Result openingHoursForAll(String itineraryId, int version) {
        return this.openingHours.checkAll(itemsOf(itineraryId, version));
    }

    private List<ItineraryItem> itemsOf(String itineraryId, int version) {
        return repository.findContent(itineraryId, version)
                .map(ItineraryContent::items)
                .orElse(List.of());
    }

    /**
     * 남은 하루를 다시 계획한다. 항목을 옮기거나 더하는 것이 아니라 {@link ItineraryDelayProjector}
     * 로 남은 방문지의 예상 시각을 구하고, 그 결과로 시각만 다시 매긴다.
     * 이미 다녀온 방문지({@code Entry.visited() == true})는 시각 지도에 넣지 않는다.
     * {@link ItineraryRevision#withReplannedDay} 가 지도에 없는 항목을 그대로 복사하므로,
     * 넣지 않는 것만으로 "지나간 방문지는 그대로 있다" 가 지켜진다.
     *
     * @param factor 속도 계수. {@code null} 이면 계수를 안 곱하고 계획대로 민다
     * @throws StaleItineraryVersionException 그 사이 다른 편집이 있었다 (409)
     * @throws NoSuchElementException 그런 일정이 없다 (404)
     */
    @Transactional
    public ItineraryVersion replanDay(String itineraryId, int dayIndex, int baseVersion,
                                      BigDecimal factor, String editorUserId) {

        Itinerary itinerary = repository.findById(itineraryId)
                .orElseThrow(() -> new NoSuchElementException("일정을 찾을 수 없습니다: " + itineraryId));

        int next = itinerary.nextVersionFrom(baseVersion);

        ItineraryContent base = repository.findContent(itineraryId, baseVersion)
                .orElseThrow(() -> new IllegalStateException(
                        "바탕 판의 내용이 없습니다: itineraryId=" + itineraryId + ", version=" + baseVersion));

        Instant now = clock.instant();

        List<ItineraryItemActual> actuals = this.actualRepository.findByItineraryId(itineraryId);
        ItineraryDelayProjector.Projection projection = ItineraryDelayProjector.project(
                base.items(), base.legs(), actuals, dayIndex, factor, now);

        Map<String, LocalDate> visitDateByItemKey = new HashMap<>();
        for (ItineraryItem item : base.items()) {
            visitDateByItemKey.put(item.itemKey(), item.visitDate());
        }

        Map<String, LocalTime> newStartTimeByItemKey = new HashMap<>();
        Map<String, LocalTime> newEndTimeByItemKey = new HashMap<>();
        List<String> overflowing = new ArrayList<>();
        for (ItineraryDelayProjector.Entry entry : projection.entries()) {
            if (entry.visited()) {
                continue;
            }
            // 계획 머문 시간이 없던 항목(사용자가 손으로 더한 장소)은 도착과 출발이 같은 순간으로
            // 나온다. 그 값을 시각으로 적으면 시작과 끝이 같아져 항목이 스스로를 거부한다 —
            // 없는 시간을 지어내지 않고 원래대로 둔다.
            if (!entry.predictedDeparture().isAfter(entry.predictedArrival())) {
                continue;
            }

            // 밀린 일정이 자정을 넘어가면 그날 안에 적을 수 없다. LocalTime 으로 바꾸면 00:30 처럼
            // 되감겨서 "새벽에 갔다" 는 거짓이 조용히 저장된다.
            LocalDate visitDate = visitDateByItemKey.get(entry.itemKey());
            if (visitDate != null && !LocalDate.ofInstant(entry.predictedDeparture(),
                    ItineraryDelayProjector.ZONE).equals(visitDate)) {
                overflowing.add(entry.itemKey());
                continue;
            }

            newStartTimeByItemKey.put(entry.itemKey(),
                    LocalTime.ofInstant(entry.predictedArrival(), ItineraryDelayProjector.ZONE));
            newEndTimeByItemKey.put(entry.itemKey(),
                    LocalTime.ofInstant(entry.predictedDeparture(), ItineraryDelayProjector.ZONE));
        }

        if (!overflowing.isEmpty()) {
            throw new ReplanOverflowsDayException(dayIndex, overflowing);
        }

        String newVersionId = UUID.randomUUID().toString();
        ItineraryRevision.Draft draft = ItineraryRevision.withReplannedDay(base, newVersionId, dayIndex,
                newStartTimeByItemKey, newEndTimeByItemKey, now);

        ItineraryVersion candidate = new ItineraryVersion(
                newVersionId,
                itineraryId,
                next,
                baseVersion,
                ItineraryVersion.Operation.REPLAN_DAY,
                editorUserId,
                "req_edit_" + UUID.randomUUID(),
                // 엔진을 돌리지 않았으므로 판 값 다섯을 비운다.
                new ItineraryVersion.Versions(null, null, null, null, null),
                now);

        return appendAndAnnounce(candidate, draft.items(), draft.legs(), draft.exclusions());
    }

    /**
     * 순서를 바꾼 그날의 구간을 다시 만들어 끼운다.
     * {@code withReorderedDay} 는 그날 구간을 버린 채로 준다 — 순서가 바뀌면 "A 에서 B 로 몇 분"
     * 이라는 주장이 더 이상 참이 아니다. 여기서 새 순서로 다시 채운다. 다른 날 구간은 손대지 않는다.
     * 여행을 못 찾거나 방문지에 좌표가 없으면 구간 없이 넘어간다. 순서 바꾸기 자체는 성공해야
     * 하기 때문이다. 그때 구간의 거리·시간은 비고 그 사실이 {@code dataStatus} 에 남는다.
     */
    /**
     * 판을 붙이고, 붙은 뒤에 「바뀌었다」를 알린다 — S15P21E201-1391.
     *
     * <p>🔴 <b>저장이 «성공한 뒤» 에만 낸다.</b> {@code appendVersion} 은 남이 먼저 고쳤으면
     * {@link StaleItineraryVersionException} 을 던진다. 그때는 여기까지 오지 않으므로, 실패한
     * 편집으로 「수민님이 순서를 바꿨어요」가 나가는 일이 없다.
     *
     * <p>실제로 보내는 것은 커밋이 끝난 뒤다 — 듣는 쪽이 {@code AFTER_COMMIT} 이라
     * 이 트랜잭션이 되돌려지면 알림도 함께 없던 일이 된다.
     */
    private ItineraryVersion appendAndAnnounce(ItineraryVersion candidate, List<ItineraryItem> items,
                                               List<ItineraryLeg> legs, List<ItineraryExclusion> exclusions) {
        ItineraryVersion saved = repository.appendVersion(candidate, items, legs, exclusions);
        this.events.publishEvent(new ItineraryChangedByMember(
                saved.itineraryId(), saved.version(), saved.operation(), saved.createdBy()));
        return saved;
    }

    private List<ItineraryLeg> withRebuiltDayLegs(Itinerary itinerary, ItineraryRevision.Draft draft,
                                                  int dayIndex, String newVersionId, Instant now) {

        List<ItineraryLeg> otherDays = draft.legs();

        Trip trip = tripRepository.findById(itinerary.tripId()).orElse(null);
        if (trip == null) {
            return otherDays;
        }

        List<UUID> dayPlaceIds = draft.items().stream()
                .filter((item) -> item.dayIndex() == dayIndex)
                .sorted(Comparator.comparingInt(ItineraryItem::sequence))
                .map(ItineraryItem::placeId)
                .filter(Objects::nonNull)
                .map(UUID::fromString)
                .toList();

        if (dayPlaceIds.isEmpty()) {
            return otherDays;
        }

        List<ItineraryLeg> rebuilt = new ArrayList<>(otherDays);
        rebuilt.addAll(legPlanner.legsForDay(trip, dayIndex, dayPlaceIds, newVersionId, now));
        return rebuilt;
    }

    /**
     * 장소를 더할 수 있는지 본다. 날짜 검사와 중복 검사 둘 다 여기 있다.
     * 두 검사는 열리는 기간이 정해진 장소에만 걸린다({@code scheduled=false} 면 통째로 건너뛴다).
     * 모든 장소에 걸면 기간 정보가 없는 식당·카페는 열리는 날이 하나도 없는 것으로 판정돼
     * 아무것도 넣을 수 없게 되고, "같은 카페를 이틀 연속 간다" 같은 정당한 사용까지 막힌다.
     * 거부를 둘로 가른다. 여행 기간과 전혀 안 겹치면 다른 날을 골라도 안 되고, 겹치는 날이
     * 있는데 요청한 날만 아니면 고칠 수 있는 요청이라 넣을 수 있는 날들을 함께 들고 나간다.
     * 같은 코드를 주면 화면이 날짜 선택기를 띄울지 판정할 수 없다.
     * 중복은 조용히 성공시키지 않는다. 성공으로 답하면 사용자는 두 번째로 누른 것이 반영됐다고
     * 믿고, 하나뿐인 것을 보고 "사라졌다" 로 읽는다.
     * 중복 판정은 요청한 날만이 아니라 바탕 판 전체에서 같은 장소를 찾는다 — 같은 축제를 다른
     * 날에 또 넣는 것도 중복이다.
     */
    private void requireAddable(ItineraryContent base, String placeId, int dayIndex,
                                LocalDate visitDate, LocalDate tripStartDate, LocalDate tripFinishDate) {

        PlaceEventSchedule schedule = eventSchedule.scheduleWithin(placeId, tripStartDate, tripFinishDate);
        if (!schedule.scheduled()) {
            return;
        }

        if (schedule.openDates().isEmpty()) {
            throw new PlaceNotOpenDuringTripException(placeId, tripStartDate, tripFinishDate);
        }
        if (!schedule.opensOn(visitDate)) {
            List<LocalDate> openDates = schedule.openDates();
            List<Integer> openDayIndexes = openDates.stream()
                    .map((date) -> (int) ChronoUnit.DAYS.between(tripStartDate, date))
                    .toList();
            throw new PlaceClosedOnDayException(placeId, dayIndex, visitDate, openDayIndexes, openDates);
        }

        List<Integer> existingDays = base.items().stream()
                .filter((item) -> placeId.equals(item.placeId()))
                .map(ItineraryItem::dayIndex)
                .distinct()
                .sorted()
                .toList();
        if (!existingDays.isEmpty()) {
            throw new PlaceAlreadyInItineraryException(placeId, dayIndex, existingDays);
        }
    }

    /**
     * 기간이 정해진 장소인데 여행 기간과 전혀 겹치지 않는다 — 422 로 답할 자리다.
     * 400 과 가르는 기준은 날짜를 바꿔 다시 보내면 되는지다. 이 경우는 안 된다.
     */
    public static class PlaceNotOpenDuringTripException extends RuntimeException {

        private final String placeId;
        private final LocalDate tripStartDate;
        private final LocalDate tripFinishDate;

        public PlaceNotOpenDuringTripException(String placeId, LocalDate tripStartDate, LocalDate tripFinishDate) {
            super("이 장소는 여행 기간(" + tripStartDate + "~" + tripFinishDate + ")에 열리지 않습니다.");
            this.placeId = placeId;
            this.tripStartDate = tripStartDate;
            this.tripFinishDate = tripFinishDate;
        }

        public String placeId()           { return placeId; }
        public LocalDate tripStartDate()  { return tripStartDate; }
        public LocalDate tripFinishDate() { return tripFinishDate; }
    }

    /**
     * 겹치는 날은 있는데 요청한 날에는 안 열린다 — 400 으로 답할 자리다.
     * 넣을 수 있는 날들을 들고 나간다. 날짜와 며칠째를 둘 다 담는데, 화면은 며칠째로 탭을 옮기고
     * 날짜로 사람이 읽을 문장을 만든다.
     */
    /**
     * 다시 짠 시간표가 그날 안에 안 들어간다 — 422 로 답할 자리다.
     * 시각을 그대로 적으면 {@code LocalTime} 이 00:30 처럼 되감겨 거짓이 조용히 저장되고,
     * 23:59 로 자르는 것도 지어내기이며, 다음 날로 넘기는 것은 그 날을 다시 짜는 다른 일이다.
     * 그래서 아무것도 저장하지 않고 어느 방문지가 넘치는지 알려 준다.
     */
    public static class ReplanOverflowsDayException extends RuntimeException {

        private final int dayIndex;

        private final List<String> overflowingItemKeys;

        public ReplanOverflowsDayException(int dayIndex, List<String> overflowingItemKeys) {
            super("남은 일정이 그날 안에 들어가지 않습니다. 방문지를 빼거나 순서를 바꿔 주세요.");
            this.dayIndex = dayIndex;
            this.overflowingItemKeys = List.copyOf(overflowingItemKeys);
        }

        public int dayIndex()                      { return dayIndex; }
        public List<String> overflowingItemKeys()  { return overflowingItemKeys; }
    }

    public static class PlaceClosedOnDayException extends RuntimeException {

        private final String placeId;
        private final int dayIndex;
        private final LocalDate requestedDate;
        private final List<Integer> openDayIndexes;
        private final List<LocalDate> openDates;

        public PlaceClosedOnDayException(String placeId, int dayIndex, LocalDate requestedDate,
                                         List<Integer> openDayIndexes, List<LocalDate> openDates) {
            super("이 장소는 " + requestedDate + " 에 열리지 않습니다. 열리는 날 중에서 골라 주세요.");
            this.placeId = placeId;
            this.dayIndex = dayIndex;
            this.requestedDate = requestedDate;
            this.openDayIndexes = List.copyOf(openDayIndexes);
            this.openDates = List.copyOf(openDates);
        }

        public String placeId()                { return placeId; }
        public int dayIndex()                  { return dayIndex; }
        public LocalDate requestedDate()       { return requestedDate; }
        public List<Integer> openDayIndexes()  { return openDayIndexes; }
        public List<LocalDate> openDates()     { return openDates; }
    }

    /**
     * 기간이 정해진 장소가 이미 이 일정에 들어 있다 — 409 로 답할 자리다.
     * 판이 낡았을 때({@code ITINERARY_VERSION_CONFLICT})와 상태 코드는 같지만 코드는 다르다.
     * 앞의 것은 "최신 일정을 다시 불러와라" 이고 이것은 "이미 담겨 있다" 라, 화면이 할 일이 정반대다.
     */
    public static class PlaceAlreadyInItineraryException extends RuntimeException {

        private final String placeId;
        private final int requestedDayIndex;
        private final List<Integer> existingDayIndexes;

        public PlaceAlreadyInItineraryException(String placeId, int requestedDayIndex,
                                                List<Integer> existingDayIndexes) {
            super("이미 일정에 담긴 장소입니다.");
            this.placeId = placeId;
            this.requestedDayIndex = requestedDayIndex;
            this.existingDayIndexes = List.copyOf(existingDayIndexes);
        }

        public String placeId()                    { return placeId; }
        public int requestedDayIndex()             { return requestedDayIndex; }
        public List<Integer> existingDayIndexes()  { return existingDayIndexes; }

        /** 화면이 곧바로 옮겨 갈 날. 여러 날에 있으면 가장 앞선 날이다. */
        public int existingDayIndex() {
            return existingDayIndexes.get(0);
        }
    }

    /**
     * 되돌리기. 어느 판의 내용을 새 판으로 복사한다(operation {@code REVERT}).
     * 별도 스냅샷 표가 없다 — 항목·구간·제외 목록이 판마다 복사되므로 판 체인이 이미 그것이다.
     * 고정과 같은 복사 규칙({@link ItineraryRevision#copyOf})을 쓴다: {@code item_key} 가 유지되고
     * 제외 목록도 그 판의 것으로 돌아간다(뺀 장소가 돌아오면서 제외도 풀린다).
     * {@code toVersion} 을 안 주면 최신 판을 만들 때 바탕이 됐던 판({@code latest.baseVersion}),
     * 즉 "마지막 편집 직전" 이다. 되돌리기 판 자체도 {@code baseVersion} 을 가지므로 한 번 더
     * 누르면 되돌리기 직전(= 다시 실행)으로 간다.
     * 덮어쓰지 않는다. 중간 판은 전부 남고 {@code reverted_from_version} 에 어디로 돌아갔는지
     * 남는다 — {@code base_version} 은 누를 때 보고 있던 최신 판이라 대개 다른 값이다.
     *
     * @throws NothingToRevertException 최초 판(CREATE) 위에서 {@code toVersion} 없이 눌렀다 — 되돌릴 편집이 없다
     * @throws RevertTargetException {@code toVersion} 이 1 미만이거나 현재 판 이상이거나 그 판이 없다
     * @throws StaleItineraryVersionException {@code baseVersion} 이 최신이 아니다 — 409
     */
    @Transactional
    public ItineraryVersion revert(String itineraryId, int baseVersion, Integer toVersion, String editorUserId) {
        Itinerary itinerary = repository.findById(itineraryId)
                .orElseThrow(() -> new NoSuchElementException("일정을 찾을 수 없습니다: " + itineraryId));
        int next = itinerary.nextVersionFrom(baseVersion);

        int target;
        if (toVersion == null) {
            ItineraryVersion latest = repository.findVersion(itineraryId, baseVersion)
                    .orElseThrow(() -> new IllegalStateException(
                            "최신 판 행이 없습니다: itineraryId=" + itineraryId + ", version=" + baseVersion));
            if (latest.baseVersion() == null) {
                // CREATE 판이다 — 그 앞에 아무 편집도 없다. 오류로 죽지 않고 그 사실을 돌려준다.
                throw new NothingToRevertException(itineraryId, baseVersion);
            }
            target = latest.baseVersion();
        }
        else {
            if (toVersion < 1 || toVersion >= baseVersion) {
                throw new RevertTargetException(itineraryId, toVersion, baseVersion,
                        "돌아갈 판은 1 이상, 현재 판(" + baseVersion + ") 미만이어야 합니다: " + toVersion);
            }
            target = toVersion;
        }

        ItineraryContent source = repository.findContent(itineraryId, target)
                .orElseThrow(() -> new RevertTargetException(itineraryId, target, baseVersion,
                        "돌아갈 판의 내용이 없습니다: version=" + target));

        Instant now = clock.instant();
        String newVersionId = UUID.randomUUID().toString();
        ItineraryRevision.Draft draft = ItineraryRevision.copyOf(source, newVersionId, now);

        // 판 값 다섯 칸은 비운다 — 엔진을 돌리지 않았다. 어느 판의 내용인지는 revertedFromVersion 이 말한다.
        ItineraryVersion candidate = new ItineraryVersion(
                newVersionId,
                itineraryId,
                next,
                baseVersion,
                ItineraryVersion.Operation.REVERT,
                editorUserId,
                "req_edit_" + UUID.randomUUID(),
                new ItineraryVersion.Versions(null, null, null, null, null),
                now,
                null,
                List.of(),
                target);
        return appendAndAnnounce(candidate, draft.items(), draft.legs(), draft.exclusions());
    }

    /** 최초 판 위에서 되돌리기를 눌렀다 — 되돌릴 편집이 없다. 422 로 답할 자리다. */
    public static class NothingToRevertException extends RuntimeException {

        private final String itineraryId;
        private final int latestVersion;

        public NothingToRevertException(String itineraryId, int latestVersion) {
            super("되돌릴 편집이 없습니다. 이 일정은 아직 편집된 적이 없습니다.");
            this.itineraryId = itineraryId;
            this.latestVersion = latestVersion;
        }

        public String itineraryId() { return itineraryId; }
        public int latestVersion()  { return latestVersion; }
    }

    /** 돌아갈 판 번호가 범위 밖이거나 그 판이 없다 — 400 으로 답할 자리다. */
    public static class RevertTargetException extends RuntimeException {

        private final String itineraryId;
        private final int toVersion;
        private final int latestVersion;

        public RevertTargetException(String itineraryId, int toVersion, int latestVersion, String message) {
            super(message);
            this.itineraryId = itineraryId;
            this.toVersion = toVersion;
            this.latestVersion = latestVersion;
        }

        public String itineraryId() { return itineraryId; }
        public int toVersion()      { return toVersion; }
        public int latestVersion()  { return latestVersion; }
    }
}
