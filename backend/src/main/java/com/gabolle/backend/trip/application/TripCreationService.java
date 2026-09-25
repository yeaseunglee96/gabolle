package com.gabolle.backend.trip.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TimeWindows;
import com.gabolle.backend.trip.domain.TripConditionRules;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TravelModes;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.domain.TravelArea;
import com.gabolle.backend.trip.domain.TripSeedPlace;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;
import com.gabolle.backend.trip.domain.TripTravelAreaRepository;
import com.gabolle.backend.place.api.PlaceSnapshotRequest;
import com.gabolle.backend.place.service.UserSubmittedPlaceService;
import com.gabolle.backend.user.application.ConsentGuard;

/** 여행 생성. 이 계층은 순서를 조율하고 트랜잭션 경계를 긋는다 — 업무 규칙은 도메인에 있다. */
@Service
public class TripCreationService {

    private final TripRepository repository;
    private final Clock clock;

    private final PreferenceDefaultsService preferenceDefaults;

    /**
     * 민감정보(알레르기·필수 식단) 동의 검사. 이 경로가 민감 제약을 표에 넣는 유일한 자리라
     * 여기서 본다 — 컨트롤러에 두면 다른 호출자가 생길 때 그대로 새어 나간다.
     */
    private final ConsentGuard consentGuard;

    /**
     * {@link Optional} 로 받는다 — 이 저장소는 DB 프로필에만 있고 이 서비스는 프로필을 안 가려서,
     * 직접 주입하면 인메모리 프로필에서 컨텍스트가 안 뜬다. 비어 있으면 씨앗을 안 적고 여행 생성은
     * 그대로 된다.
     */
    private final Optional<TripSeedPlaceRepository> seedPlaces;

    /** {@link #seedPlaces} 와 같은 이유로 {@link Optional} 이다. */
    private final Optional<TripTravelAreaRepository> travelAreas;

    /**
     * 🔴 {@code Optional} 인 것은 이 클래스가 프로필 없이 뜨는 문맥에도 있어서다.
     *    {@code UserSubmittedPlaceService} 는 {@code db}·{@code dev} 에서만 있는
     *    {@code PlaceRepository} 를 물고 있어, 그냥 주입하면 그 문맥이 통째로 못 뜬다
     *    (S15P21E201-1096 에서 실제로 그렇게 깨졌다). 위 저장소 둘이 이미 같은 방식이다.
     */
    private final Optional<UserSubmittedPlaceService> userSubmittedPlaces;

    /**
     * 숙소 스냅샷을 안 푸는 판 — 그 기능을 안 쓰는 호출자(주로 시험)가 쓴다. 스냅샷이 와도
     * {@code accommodationPlaceId} 만 본다.
     */
    public TripCreationService(TripRepository repository, Clock clock,
                               PreferenceDefaultsService preferenceDefaults, ConsentGuard consentGuard,
                               Optional<TripSeedPlaceRepository> seedPlaces,
                               Optional<TripTravelAreaRepository> travelAreas) {
        this(repository, clock, preferenceDefaults, consentGuard, seedPlaces, travelAreas, Optional.empty());
    }

    // 🔴 생성자가 둘이라 이 표시가 없으면 Spring 이 어느 것을 쓸지 못 정하고
    //    "No default constructor found" 로 죽는다. 하나뿐일 때는 알아서 골랐다.
    @Autowired
    public TripCreationService(TripRepository repository, Clock clock,
                               PreferenceDefaultsService preferenceDefaults, ConsentGuard consentGuard,
                               Optional<TripSeedPlaceRepository> seedPlaces,
                               Optional<TripTravelAreaRepository> travelAreas,
                               Optional<UserSubmittedPlaceService> userSubmittedPlaces) {
        this.repository = repository;
        this.clock = clock;
        this.preferenceDefaults = preferenceDefaults;
        this.consentGuard = consentGuard;
        this.seedPlaces = seedPlaces;
        this.travelAreas = travelAreas;
        this.userSubmittedPlaces = userSubmittedPlaces;
    }

    /**
     * 1박 이상 여행에 숙소를 요구하는가(S15P21E201-1585) — <b>기본은 꺼짐</b>(S15P21E201-1596).
     *
     * <p>🔴 규칙이 운영에 나가자 앱 build 44 가 1박 이상 여행을 못 만들었다 — 추천 동네를 골라도 동네 코드를
     * ({@code accommodationArea}) 안 보내는 판이라 숙소 없는 여행으로 판정됐다. App Store 재제출이 그 판으로
     * 준비 중이어서 검사를 스위치 뒤로 뺐다. 동네 코드를 보내는 앱이 나간 뒤 운영 환경변수
     * {@code GABOLLE_TRIP_LODGING_REQUIRED=true} 로 켠다. 추천 요청 쪽({@code RecommendationJobRunner})도 같은 스위치다.
     */
    private boolean lodgingRequired;

    /**
     * 사람이 여행을 만들며 직접 넣은 것을 이벤트로 남기는 곳(S15P21E201-1689) — 이벤트 패키지를 안 올리는 테스트 슬라이스에서는
     * 없다. 생성자가 아니라 설정자로 받는 것은 이 서비스를 직접 만드는 시험이 많아서다.
     */
    private EventIngestService events;

    @Autowired(required = false)
    public void setEvents(EventIngestService events) {
        this.events = events;
    }

    @Value("${gabolle.trip.lodging-required:false}")
    public void setLodgingRequired(boolean lodgingRequired) {
        this.lodgingRequired = lodgingRequired;
    }

    /**
     * 여행을 만든다. 여행·제약·소유자·취향이 하나의 트랜잭션이다 — 나뉘면 소유자 없는 여행이나
     * 제약이 절반만 들어간 여행이 생긴다. 같은 {@code Idempotency-Key} 로 다시 오면 기존 여행을
     * 돌려준다.
     *
     * @param idempotencyKey 없으면 중복 방지를 하지 않는다
     * @return 새로 만들었으면 {@code created=true}, 이미 있었으면 {@code false}
     */
    @Transactional
    public Result create(Command command, String idempotencyKey) {

        // 조건을 먼저 본다. 컨트롤러는 DTO 번역만 하고 Trip 생성자는 DB 값을 되살릴 때도
        // 지나가는 자리라, 조건 검사는 생성이 일어나는 이 자리의 일이다.
        TripConditionRules.require(command.startDate(), command.finishDate(), command.partySize(),
                command.budgetKrw(), command.originLat(), command.originLng(), command.timeWindow());

        String fingerprint = fingerprintOf(command);

        Instant now = clock.instant();
        String tripId = UUID.randomUUID().toString();

        // 파생은 생성이 일어나는 이 자리에서 한 번만 한다. Trip 생성자에서 매번 파생하면
        // 저장된 값과 "지금 파싱 규칙으로 다시 뽑은 값" 이 규칙이 바뀐 날 어긋난다.
        Optional<TimeWindows.TimeWindow> window = TimeWindows.parseRange(command.timeWindow());
        String[] travelModes = resolveTravelModes(command.preferences());
        String pace = resolvePace(command.preferences());

        // 자차 이동이면 최대 환승 횟수는 뜻이 없다. 400 으로 거부하지 않고 조용히 무시하는 것은
        // 두 조건을 함께 고르는 것이 사용자 잘못이 아니라 화면이 상호배제를 안 걸었을 수 있어서다.
        boolean usesPrivateCar = java.util.Arrays.asList(travelModes).contains("PRIVATE_CAR");
        Integer maxTransitTransfers = usesPrivateCar ? null : command.maxTransitTransfers();

        // 숙소를 장소로 바꾼다. 앱이 우리 place_id 를 못 주고 좌표로만 보내던 것을 여기서 받는다
        // (S15P21E201-1522). Trip 을 만들기 전에 해야 accommodation_place_id 외래키가 맞는다.
        String accommodationPlaceId = resolveAccommodation(command);
        // 🔴 1박 이상이면 숙소가 있어야 한다(S15P21E201-1585). 스냅샷을 장소로 바꾼 뒤에 본다 — 검색으로 고른 호텔도 숙소다.
        //    스위치가 켜졌을 때만이다(S15P21E201-1596) — 기본은 꺼짐.
        if (this.lodgingRequired) {
            TripConditionRules.requireLodging(command.startDate(), command.finishDate(), accommodationPlaceId,
                    command.accommodationArea());
        }

        // timeWindow 는 원문을 그대로 넘긴다 — fingerprintOf 가 원문 기준이라, 파생값을 저장하면
        // 재시도 판정이 흔들린다.
        Trip trip = new Trip(tripId, command.userId(), command.ownerType(),
                command.startDate(), command.finishDate(),
                command.originLat(), command.originLng(),
                command.budgetKrw(), command.partySize(),
                command.timeWindow(), command.timezone(),
                travelModes,
                window.map(TimeWindows.TimeWindow::start).orElse(null),
                window.map(TimeWindows.TimeWindow::end).orElse(null),
                accommodationPlaceId,
                command.englishMenuRequired(), command.foreignCardRequired(), command.soloFriendlyPriority(),
                maxTransitTransfers,
                pace,
                now,
                TravelArea.of(command.accommodationArea()).map(Enum::name).orElse(null));

        // scope 는 TRIP 고정이다 — 여기서 만드는 제약은 항상 이번 여행 전용이다.
        List<TripConstraint> constraints = new ArrayList<>();
        List<String> constraintIds = new ArrayList<>();
        for (Command.ConstraintInput c : command.constraints()) {
            String id = UUID.randomUUID().toString();
            constraints.add(new TripConstraint(id, tripId, c.type(), c.constraintKey(), c.severity(),
                    c.operator(), c.value(), c.threshold(), c.evidenceStatus(),
                    c.answerStatus(), PersonalizationScope.TRIP, c.dietRequirement()));
            constraintIds.add(id);
        }

        // 민감 제약이 하나라도 있으면 동의를 본다. 루프 뒤인 것이 중요하다 — 도메인 검증이 먼저
        // 이겨야 자유 입력 거부(400)가 미동의 사용자에게도 보인다. 앞에 두면 403 에 가린다.
        // 민감 여부는 여기서 판정하지 않고 TripConstraint.isSensitive 에 묻는다 — 목록이 두 벌이
        // 되면 한쪽만 늘어나고 그 어긋남은 어느 화면에도 안 보인다.
        if (command.constraints().stream()
                .anyMatch(c -> TripConstraint.isSensitive(c.type(), c.dietRequirement()))) {
            this.consentGuard.requireHealthConstraints(asUuidOrNull(command.userId()));
        }

        // 만든 사람을 OWNER 로 넣는다. 안 넣으면 자기 여행을 못 본다.
        TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, command.userId(), now);

        // 선호 스냅샷은 그때의 취향을 복사해 굳힌다 — 계정 취향이 나중에 바뀌어도 이 여행이
        // 무엇으로 만들어졌는지는 안 바뀐다.
        //
        // "transport" 답은 저장 목록에서만 뺀다. preference_answer.dimension 의 CHECK 가 그 값을
        // 안 받아서, 두면 취향 한 줄 때문에 여행·멤버·제약까지 통째로 롤백된다. 값은 이미 위에서
        // travelModes 로 소비했고, 지문은 원본 command.preferences() 를 쓰므로 영향받지 않는다.
        // 대소문자를 안 가리는 것은 정규화를 거쳐 "TRANSPORT" 로 들어올 수 있어서다.
        // "pace" 도 같은 이유로 뺀다 — 취향이 아니라 여행의 모양이라 CHECK 어휘에 없고,
        // 값은 바로 위에서 trip.pace 로 소비했다.
        List<PreferenceSnapshot.PreferenceAnswer> storedPreferences = command.preferences().stream()
                .filter(a -> !"transport".equalsIgnoreCase(a.dimension()))
                .filter(a -> !"pace".equalsIgnoreCase(a.dimension()))
                .toList();

        // 계정 기본 취향으로 이 여행이 답하지 않은 차원을 채운다. 규칙은 PreferenceDefaultsService
        // 에 있다 — 여행 답이 항상 이기고, 일부러 건너뛴 차원은 되살리지 않는다.
        List<PreferenceSnapshot.PreferenceAnswer> mergedPreferences =
                preferenceDefaults.overlayDefaults(command.userId(), storedPreferences);

        PreferenceSnapshot snapshot = new PreferenceSnapshot(
                UUID.randomUUID().toString(), tripId, 1,
                mergedPreferences, PersonalizationScope.TRIP, constraintIds, now);

        // 키 확보와 저장을 한 동작으로 한다. 나누면 같은 키로 동시에 온 요청이 전부 여행을 만든다.
        // 여기서 만든 Trip 객체는 경쟁에서 지면 저장되지 않고 버려진다.
        TripRepository.SaveOutcome outcome = repository.saveWithIdempotency(
                command.userId(), idempotencyKey, fingerprint,
                trip, constraints, owner, snapshot);

        // 이 여행에서 고른 답을 계정 기본값으로 이어받는다. mergedPreferences 가 아니라
        // storedPreferences 를 넘긴다 — 겹친 뒤의 목록에는 계정 기본값이 섞여 있어 사용자가
        // 답하지 않은 차원까지 "고른 것" 이 된다. 실제로 만들어졌을 때만 한다 — 같은 키로 다시 온
        // 요청은 여행을 안 만들었으므로 취향도 새로 정한 것이 아니다.
        if (outcome.created()) {
            preferenceDefaults.carryOver(command.userId(), storedPreferences);
            saveMustVisitPlaces(outcome.trip().tripId(), command.mustVisitPlaceIds(), now);
            saveTravelAreas(outcome.trip().tripId(), command.travelAreas());
            recordExplicitInputs(outcome.trip(), command, storedPreferences, constraints);
        }

        return new Result(outcome.trip(), outcome.snapshot(), outcome.created());
    }

    /**
     * 꼭 가고 싶은 장소를 {@code trip_seed_place} 에 씨앗으로 적는다. 새로 만든 여행일 때만
     * 부른다 — 재시도로 돌려준 여행에는 씨앗이 이미 있어 다시 적으면 기본키에 걸린다. 같은
     * 이유로 중복 장소는 앞의 것만 남긴다. 저장소가 없으면 조용히 건너뛴다.
     */
    private void saveMustVisitPlaces(String tripId, List<String> placeIds, Instant now) {
        if (this.seedPlaces.isEmpty() || placeIds.isEmpty()) {
            return;
        }
        List<TripSeedPlace> seeds = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String placeId : placeIds) {
            if (placeId == null || placeId.isBlank() || !seen.add(placeId)) {
                continue;
            }
            seeds.add(new TripSeedPlace(tripId, placeId, seen.size(), null, null, now));
        }
        if (!seeds.isEmpty()) {
            this.seedPlaces.get().saveAll(seeds);
        }
    }

    /**
     * 고른 여행 범위를 적는다. 모르는 코드는 버린다 — 앱이 새 지역을 먼저 내보내는 날 여행 생성이
     * 막히면 안 된다. 새로 만든 여행일 때만 부른다({@link #saveMustVisitPlaces} 와 같은 이유).
     */
    private void saveTravelAreas(String tripId, List<String> codes) {
        if (this.travelAreas.isEmpty() || codes.isEmpty()) {
            return;
        }
        List<TravelArea> areas = new ArrayList<>();
        for (String code : codes) {
            TravelArea.of(code).filter((area) -> !areas.contains(area)).ifPresent(areas::add);
        }
        if (!areas.isEmpty()) {
            this.travelAreas.get().saveAll(tripId, areas);
        }
    }

    /**
     * 사용자 ID 를 {@code UUID} 로 바꾼다. 형식이 아니면 예외가 아니라 {@code null} 이다 —
     * 여기서 400 을 내면 "동의가 없다" 와 "ID 가 이상하다" 가 다른 오류로 갈라지는데, 둘 다 할 일은
     * 저장하지 않는 것 하나다. 가드가 {@code null} 을 미동의로 다룬다.
     */
    /**
     * 여행을 만들며 사람이 직접 넣은 것을 이벤트로 남긴다 (S15P21E201-1689) — 계획서 P0 의 {@code trip_created} 1건 · 답한 취향마다
     * {@code preference_set}(scope TRIP) · 답한 제약마다 {@code constraint_set}. 전에는 종류만 정의돼 있고 내는 곳이 없어 운영에
     * 한 건도 없었다.
     *
     * <p>같은 트랜잭션이다(아웃박스) — 여행 저장이 되돌려지면 이벤트도 없다. 동의와 무관하다 — 행동 관찰이 아니라 명시
     * 입력이다({@code EventType.BEHAVIOR_SIGNALS} 에 없다).
     *
     * <p>🔴 싣지 않는 것: 출발지 좌표(지역 코드도 없이 「있다」만), 제약 값, <b>제약 항목 이름</b>. 항목 이름을 모든 제약에서
     * 빼는 까닭(2026-09-25 결정) — 「개인화 정보로 이미 가지고 있다」는 원칙이 이름에도 같고, 민감 판정
     * ({@link TripConstraint#isSensitive})이 알레르기·꼭 지킬 식단만 거른다. 휠체어(장애 = 건강 정보)·선호 식단의
     * HALAL·KOSHER(종교) 같은 민감정보가 그 판정을 지나 이름으로 실린다. 남기는 것은 종류 · 값 유무 · 반드시 여부 · 답 상태다.
     * 비로그인(ANONYMOUS) 여행은 사람 축 이벤트(취향·제약)를 적을 사람이 없어 {@code trip_created} 만 남긴다.
     */
    private void recordExplicitInputs(Trip trip, Command command, List<PreferenceSnapshot.PreferenceAnswer> preferences,
            List<TripConstraint> constraints) {
        if (this.events == null) {
            return;
        }
        UUID tripId = UUID.fromString(trip.tripId());
        UUID userId = trip.ownerType() == Trip.OwnerType.USER ? asUuidOrNull(command.userId()) : null;

        Map<String, Object> created = new LinkedHashMap<>();
        created.put("trip_version", 1); // 새 여행은 1판이다
        created.put("start_date", String.valueOf(trip.startDate()));
        created.put("finish_date", String.valueOf(trip.finishDate()));
        created.put("party_size", trip.partySize());
        created.put("budget_krw", trip.budgetKrw());
        created.put("time_window", trip.timeWindow());
        created.put("pace", trip.pace());
        created.put("travel_modes", List.of(trip.travelModes()));
        created.put("travel_areas", command.travelAreas() == null ? List.of() : List.copyOf(command.travelAreas()));
        created.put("accommodation_area", trip.accommodationArea());
        created.put("has_accommodation_place", trip.accommodationPlaceId() != null);
        created.put("must_visit_count", command.mustVisitPlaceIds() == null ? 0 : command.mustVisitPlaceIds().size());
        created.put("has_origin", trip.originLat() != null && trip.originLng() != null);
        this.events.recordFromServer(UUID.randomUUID(), EventType.TRIP_CREATED, 1, userId, tripId, null, created);

        if (userId == null) {
            return;
        }
        for (PreferenceSnapshot.PreferenceAnswer answer : preferences) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("scope", "TRIP");
            payload.put("dimension", answer.dimension());
            payload.put("value", answer.valueJson());
            payload.put("answer_status", answer.status() == null ? null : answer.status().name());
            this.events.recordFromServer(UUID.randomUUID(), EventType.PREFERENCE_SET, 1, userId, tripId, null, payload);
        }
        for (TripConstraint constraint : constraints) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("scope", "TRIP");
            payload.put("constraint_type", constraint.type());
            payload.put("has_value", constraint.value() != null || constraint.threshold() != null);
            payload.put("hard", constraint.severity() == TripConstraint.Severity.HARD);
            payload.put("answer_status", constraint.answerStatus() == null ? null : constraint.answerStatus().name());
            this.events.recordFromServer(UUID.randomUUID(), EventType.CONSTRAINT_SET, 1, userId, tripId, null, payload);
        }
    }

    private static UUID asUuidOrNull(String userId) {
        try {
            return (userId == null) ? null : UUID.fromString(userId);
        }
        catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    /** 같은 키를 다른 내용으로 재사용했는지 가리는 값 — 그때는 409 로 거부한다. */
    private String fingerprintOf(Command c) {
        String raw = String.join("|",
                c.userId(), String.valueOf(c.startDate()), String.valueOf(c.finishDate()),
                String.valueOf(c.originLat()), String.valueOf(c.originLng()),
                String.valueOf(c.budgetKrw()), String.valueOf(c.partySize()),
                String.valueOf(c.timeWindow()), String.valueOf(c.timezone()),
                String.valueOf(c.preferences()), String.valueOf(c.constraints()),
                String.valueOf(c.accommodationPlaceId()), String.valueOf(c.englishMenuRequired()),
                String.valueOf(c.foreignCardRequired()), String.valueOf(c.soloFriendlyPriority()),
                String.valueOf(c.maxTransitTransfers()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 이 없다", e);
        }
    }

    /**
     * 답이 없거나 {@code SELECTED} 가 아니면 빈 배열이다. 답이 있는데 값을 이해하지 못하면 예외를
     * 던진다 — 조용히 넘기면 사용자가 고른 값이 말없이 버려진다.
     */
    /**
     * 「여행 기분」. 답이 없거나 {@code SELECTED} 가 아니면 {@code null} 이고, 그때 일정 생성은
     * 지금까지처럼 설정 기본값으로 하루 곳 수를 정한다 — {@code null} 은 「보통」이 아니라 「모른다」다.
     * 값을 이해하지 못하면 {@link Trip} 생성자가 예외를 던진다. 조용히 넘기면 사용자가 고른
     * 값이 말없이 버려진다.
     */
    private String resolvePace(List<PreferenceSnapshot.PreferenceAnswer> preferences) {
        return preferences.stream()
                .filter(a -> "pace".equalsIgnoreCase(a.dimension()))
                .findFirst()
                .filter(a -> a.status() == PreferenceSnapshot.AnswerStatus.SELECTED)
                .map(a -> unquoteJsonString(a.valueJson()))
                .orElse(null);
    }

    private String[] resolveTravelModes(List<PreferenceSnapshot.PreferenceAnswer> preferences) {
        return preferences.stream()
                .filter(a -> "transport".equalsIgnoreCase(a.dimension()))
                .findFirst()
                .filter(a -> a.status() == PreferenceSnapshot.AnswerStatus.SELECTED)
                .map(a -> TravelModes.fromTransportPreference(unquoteJsonString(a.valueJson())))
                .orElse(new String[0]);
    }

    /**
     * 화면이 {@code JSON.stringify} 해서 보낸 값의 앞뒤 큰따옴표만 벗긴다
     * (예: {@code "\"TRANSIT\""} → {@code TRANSIT}).
     *
     * <p>값이 단순 문자열 하나뿐이라 이걸로 충분하고, {@code ObjectMapper} 를 안 쓰는 것은
     * 도메인에 Jackson 의존을 들이지 않기 위해서다. 배열 JSON 이 오면 벗겨지지 않고 그대로 넘어가
     * 거부된다 — 배열이 왔다는 것 자체가 계약 밖의 요청이라 그것이 맞다.
     */
    private static String unquoteJsonString(String valueJson) {
        if (valueJson != null && valueJson.length() >= 2
                && valueJson.startsWith("\"") && valueJson.endsWith("\"")) {
            return valueJson.substring(1, valueJson.length() - 1);
        }
        return valueJson;
    }

    /**
     * 이 여행의 숙소 {@code place_id}. 앱이 직접 준 것이 있으면 그것이고, 없고 스냅샷이
     * 오면 그것으로 장소를 찾거나 만든다 — S15P21E201-1522.
     *
     * <p>🔴 빈이 없으면 스냅샷을 못 푼다. 그때는 {@code null} 로 둔다 — 여기서 던지면
     * 숙소 하나 때문에 여행 생성 전체가 실패한다. 빈이 없는 것은 프로필이 좁은 문맥뿐이고
     * 거기서는 어차피 저장이 일어나지 않는다.
     */
    private String resolveAccommodation(Command command) {
        if (command.accommodationPlaceId() != null || command.accommodation() == null) {
            return command.accommodationPlaceId();
        }
        UserSubmittedPlaceService resolver = this.userSubmittedPlaces.orElse(null);
        if (resolver == null) {
            return null;
        }
        PlaceSnapshotRequest snapshot = command.accommodation();
        return resolver.findOrCreate(new UserSubmittedPlaceService.Snapshot(
                snapshot.source(), snapshot.externalId(), snapshot.name(), snapshot.address(),
                snapshot.lat(), snapshot.lng(), snapshot.category())).getPlaceId().toString();
    }

    /** 응용 계층 입력. 표현 계층 DTO 를 도메인까지 끌고 들어가지 않는다. */
    public record Command(
            String userId,
            LocalDate startDate,
            LocalDate finishDate,
            Double originLat,
            Double originLng,
            Integer budgetKrw,
            int partySize,
            String timeWindow,
            String timezone,
            List<PreferenceSnapshot.PreferenceAnswer> preferences,
            List<ConstraintInput> constraints,
            Trip.OwnerType ownerType,
            /** 매일 여기서 시작하고 여기로 돌아온다. {@code null} 이면 아직 안 정한 것이다. */
            String accommodationPlaceId,
            boolean englishMenuRequired,
            boolean foreignCardRequired,
            boolean soloFriendlyPriority,
            /** {@code null} 이면 제한 없음. {@code PRIVATE_CAR} 이동이면 저장 전에 무시된다. */
            Integer maxTransitTransfers,

            /** 고른 순서를 그대로 쓴다. 새 여행일 때만 {@code trip_seed_place} 에 적힌다. */
            List<String> mustVisitPlaceIds,

            /** 모르는 코드는 저장 단계에서 버린다({@link TravelArea#of}). */
            List<String> travelAreas,

            /**
             * 우리 표에 없는 숙소를 골랐을 때의 스냅샷 — S15P21E201-1522. {@code null} 이거나
             * {@code accommodationPlaceId} 가 이미 있으면 안 본다.
             */
            PlaceSnapshotRequest accommodation,

            /** 묵는 동네({@code TravelArea} 코드). 모르는 값이면 저장 단계에서 버린다. */
            String accommodationArea) {

        /** 안 준 목록을 빈 목록으로 고정한다 — 뒤쪽이 null 을 다시 보지 않게 한다. */
        public Command {
            mustVisitPlaceIds = mustVisitPlaceIds == null ? List.of() : List.copyOf(mustVisitPlaceIds);
            travelAreas = travelAreas == null ? List.of() : List.copyOf(travelAreas);
        }

        /** 짧은 생성자들은 뒤에 붙은 칸을 기본값(빈 목록·null·false)으로 채워 위임한다. */
        public Command(String userId, LocalDate startDate, LocalDate finishDate, Double originLat, Double originLng,
                Integer budgetKrw, int partySize, String timeWindow, String timezone,
                List<PreferenceSnapshot.PreferenceAnswer> preferences, List<ConstraintInput> constraints,
                Trip.OwnerType ownerType, String accommodationPlaceId, boolean englishMenuRequired,
                boolean foreignCardRequired, boolean soloFriendlyPriority, Integer maxTransitTransfers) {
            this(userId, startDate, finishDate, originLat, originLng, budgetKrw, partySize, timeWindow, timezone,
                    preferences, constraints, ownerType, accommodationPlaceId, englishMenuRequired,
                    foreignCardRequired, soloFriendlyPriority, maxTransitTransfers, List.of(), List.of(), null, null);
        }

        public Command(String userId, LocalDate startDate, LocalDate finishDate, Double originLat, Double originLng,
                Integer budgetKrw, int partySize, String timeWindow, String timezone,
                List<PreferenceSnapshot.PreferenceAnswer> preferences, List<ConstraintInput> constraints,
                Trip.OwnerType ownerType, String accommodationPlaceId, boolean englishMenuRequired,
                boolean foreignCardRequired, boolean soloFriendlyPriority, Integer maxTransitTransfers,
                List<String> mustVisitPlaceIds) {
            this(userId, startDate, finishDate, originLat, originLng, budgetKrw, partySize, timeWindow, timezone,
                    preferences, constraints, ownerType, accommodationPlaceId, englishMenuRequired,
                    foreignCardRequired, soloFriendlyPriority, maxTransitTransfers, mustVisitPlaceIds, List.of(), null, null);
        }

        /** 숙소 스냅샷 없이 목록 둘까지 주는 판. 그 기능을 안 쓰는 호출자가 쓴다. */
        public Command(String userId, LocalDate startDate, LocalDate finishDate, Double originLat,
                Double originLng, Integer budgetKrw, int partySize, String timeWindow, String timezone,
                List<PreferenceSnapshot.PreferenceAnswer> preferences, List<ConstraintInput> constraints,
                Trip.OwnerType ownerType, String accommodationPlaceId, boolean englishMenuRequired,
                boolean foreignCardRequired, boolean soloFriendlyPriority, Integer maxTransitTransfers,
                List<String> mustVisitPlaceIds, List<String> travelAreas) {
            this(userId, startDate, finishDate, originLat, originLng, budgetKrw, partySize, timeWindow,
                    timezone, preferences, constraints, ownerType, accommodationPlaceId, englishMenuRequired,
                    foreignCardRequired, soloFriendlyPriority, maxTransitTransfers, mustVisitPlaceIds,
                    travelAreas, null, null);
        }

        public Command(String userId, LocalDate startDate, LocalDate finishDate, Double originLat, Double originLng,
                Integer budgetKrw, int partySize, String timeWindow, String timezone,
                List<PreferenceSnapshot.PreferenceAnswer> preferences, List<ConstraintInput> constraints) {
            this(userId, startDate, finishDate, originLat, originLng, budgetKrw, partySize, timeWindow, timezone,
                    preferences, constraints, Trip.OwnerType.USER, null, false, false, false, null);
        }

        public Command(
                String userId,
                LocalDate startDate,
                LocalDate finishDate,
                Double originLat,
                Double originLng,
                Integer budgetKrw,
                int partySize,
                String timeWindow,
                String timezone,
                List<PreferenceSnapshot.PreferenceAnswer> preferences,
                List<ConstraintInput> constraints,
                String accommodationPlaceId,
                boolean englishMenuRequired,
                boolean foreignCardRequired,
                boolean soloFriendlyPriority,
                Integer maxTransitTransfers) {
            this(userId, startDate, finishDate, originLat, originLng, budgetKrw, partySize, timeWindow,
                    timezone, preferences, constraints, Trip.OwnerType.USER, accommodationPlaceId,
                    englishMenuRequired, foreignCardRequired, soloFriendlyPriority, maxTransitTransfers);
        }

        public Command(
                String userId,
                LocalDate startDate,
                LocalDate finishDate,
                Double originLat,
                Double originLng,
                Integer budgetKrw,
                int partySize,
                String timeWindow,
                String timezone,
                List<PreferenceSnapshot.PreferenceAnswer> preferences,
                List<ConstraintInput> constraints,
                Trip.OwnerType ownerType) {
            this(userId, startDate, finishDate, originLat, originLng, budgetKrw, partySize, timeWindow,
                    timezone, preferences, constraints, ownerType, null, false, false, false, null);
        }

        public record ConstraintInput(
                String type,
                String constraintKey,
                TripConstraint.Severity severity,
                String operator,
                String value,
                Double threshold,
                TripConstraint.EvidenceStatus evidenceStatus,
                TripConstraint.AnswerStatus answerStatus,
                TripConstraint.DietRequirement dietRequirement) {}
    }

    /** {@code created=false} 면 재시도였고 기존 여행을 돌려준 것이다. */
    public record Result(Trip trip, PreferenceSnapshot snapshot, boolean created) {}
}
