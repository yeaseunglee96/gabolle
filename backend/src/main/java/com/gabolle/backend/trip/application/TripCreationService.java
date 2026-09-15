package com.gabolle.backend.trip.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TimeWindows;
import com.gabolle.backend.trip.domain.TripConditionRules;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TravelModes;
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.backend.trip.domain.TripSeedPlace;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;
import com.gabolle.backend.user.application.ConsentGuard;

/**
 * 여행 생성 — S15P21E201-461 · TRIP-01.
 *
 * <p>이 계층이 하는 일은 <b>순서를 조율하고 트랜잭션 경계를 긋는 것</b>이다.
 * 업무 규칙은 도메인 생성자에 있다.
 */
@Service
public class TripCreationService {

    private final TripRepository repository;
    private final Clock clock;

    /**
     * 계정 기본 취향을 여행 답에 겹치는 규칙 (S15P21E201-547).
     *
     * <p>🔴 계정 기본값이 없으면 아무 일도 하지 않는다 — 지금까지와 똑같이 동작한다.
     */
    private final PreferenceDefaultsService preferenceDefaults;

    /**
     * 🔴 민감정보(알레르기·필수 식단) 동의를 검사한다 — S15P21E201-549.
     *
     * <p>여행 생성이 이 서비스의 일인데 왜 동의까지 보는가 — 이 경로가 <b>민감 제약을 표에
     * 넣는 유일한 자리</b>이기 때문이다. 컨트롤러에 두면 다른 호출자가 생길 때 그대로
     * 새어 나가고, 이 클래스 머리말이 정한 "업무 규칙은 생성이 일어나는 자리에" 와도 어긋난다.
     */
    private final ConsentGuard consentGuard;

    /**
     * 꼭 가고 싶은 장소를 적는 자리 — S15P21E201-973.
     *
     * <p>🔴 {@link Optional} 로 받는다. 이 저장소는 DB 프로필에만 있고 이 서비스는 프로필을
     * 안 가린다 — 직접 주입하면 인메모리 프로필에서 컨텍스트가 안 뜬다. 비어 있으면 씨앗을
     * 안 적고 여행 생성은 그대로 된다(씨앗은 추천을 거들 뿐 필수가 아니다).
     */
    private final Optional<TripSeedPlaceRepository> seedPlaces;

    public TripCreationService(TripRepository repository, Clock clock,
                               PreferenceDefaultsService preferenceDefaults, ConsentGuard consentGuard,
                               Optional<TripSeedPlaceRepository> seedPlaces) {
        this.repository = repository;
        this.clock = clock;
        this.preferenceDefaults = preferenceDefaults;
        this.consentGuard = consentGuard;
        this.seedPlaces = seedPlaces;
    }

    /**
     * 여행을 만든다.
     *
     * <p>🔴 <b>넷이 하나의 트랜잭션이다.</b> 나뉘면 "여행은 생겼는데 소유자가 없는" 또는
     * "제약이 절반만 들어간" 상태가 생긴다. 그러면 만든 사람이 자기 여행을 못 보거나,
     * 알레르기 제약이 빠진 채로 추천이 돌아간다.
     *
     * <p>🔴 같은 {@code Idempotency-Key} 로 다시 오면 <b>기존 여행을 돌려준다</b>(API-09).
     * 지하철에서 응답이 끊긴 앱은 반드시 재시도하고, 그때 여행이 두 개 생기면 안 된다.
     *
     * @param idempotencyKey 없으면 중복 방지를 하지 않는다. 클라이언트가 붙여야 한다
     * @return 새로 만들었으면 {@code created=true}, 이미 있었으면 {@code false}
     */
    @Transactional
    public Result create(Command command, String idempotencyKey) {

        // 🔴 S15P21E201-440 — 조건을 먼저 본다. 여기서 막지 않으면 말이 안 되는 여행이
        //    저장되고, 그 위에서 일정 계산기가 터진다. 계산기 안에서 난 오류는 원인을
        //    짚기 어렵다. 규칙은 TripConditionRules 한 자리에 있고, 어긴 항목을 전부
        //    모아 돌려준다 — 하나씩 알려 주면 사용자가 고칠 때마다 다시 거절당한다.
        //
        //    이 자리인 이유는 위(81행) 주석과 같다. 컨트롤러는 DTO 번역만 하고, Trip
        //    생성자는 DB 값을 되살릴 때도 지나가는 자리다. 조건 검사는 생성이 일어나는
        //    이 자리의 일이다.
        TripConditionRules.require(command.startDate(), command.finishDate(), command.partySize(),
                command.budgetKrw(), command.originLat(), command.originLng(), command.timeWindow());

        String fingerprint = fingerprintOf(command);

        Instant now = clock.instant();
        String tripId = UUID.randomUUID().toString();

        // 🔴 S15P21E201-664 — timeWindow·이동수단이 조용히 버려지던 것을 여기서 채운다.
        //    왜 컨트롤러나 Trip 생성자가 아니라 여기인가 — 컨트롤러는 DTO→Command
        //    번역만 하고 업무 규칙을 갖지 않는다. Trip 생성자(14-인자)는 저장소가 DB
        //    값을 그대로 되살릴 때(Trip.builder())도 지나가는 자리라, 거기서 매번
        //    새로 파생하면 "그때 저장된 travelModes·시각"과 "지금 파싱 규칙으로 다시
        //    뽑은 값"이 파싱 규칙이 바뀐 날 어긋난다. 생성이 일어나는 이 자리
        //    한 번에서만 파생해야 DB 값과 파생값이 항상 같다.
        Optional<TimeWindows.TimeWindow> window = TimeWindows.parseRange(command.timeWindow());
        String[] travelModes = resolveTravelModes(command.preferences());

        // 🔴 S15P21E201-456 — 자차(PRIVATE_CAR) 이동이면 최대 환승 횟수는 뜻이 없다.
        //    화면이 실수로 값을 함께 보내도 조용히 무시한다 — 자차에는 환승 개념이
        //    없으므로 저장해 봐야 나중에 아무도 그 값을 안 쓴다. 400 으로 거부하지 않는
        //    이유는 두 조건을 함께 고르는 것 자체가 사용자 잘못이 아니라 화면이 아직
        //    상호배제를 안 걸었을 수 있어서다 — 저장 시점에 조용히 걸러 두면 화면이
        //    나중에 그 로직을 넣어도 서버 쪽 동작은 안 바뀐다.
        boolean usesPrivateCar = java.util.Arrays.asList(travelModes).contains("PRIVATE_CAR");
        Integer maxTransitTransfers = usesPrivateCar ? null : command.maxTransitTransfers();

        // ① 여행. 생성자가 조건을 검증한다 — 종료일이 시작일보다 앞이면 여기서 거부된다.
        //    timeWindow 원문은 그대로 넘긴다 — fingerprintOf 가 이 원문 기준이라(아래),
        //    파생값이 아니라 원문을 저장해야 재시도 판정이 안 흔들린다.
        Trip trip = new Trip(tripId, command.userId(), command.ownerType(),
                command.startDate(), command.finishDate(),
                command.originLat(), command.originLng(),
                command.budgetKrw(), command.partySize(),
                command.timeWindow(), command.timezone(),
                travelModes,
                window.map(TimeWindows.TimeWindow::start).orElse(null),
                window.map(TimeWindows.TimeWindow::end).orElse(null),
                command.accommodationPlaceId(),
                command.englishMenuRequired(), command.foreignCardRequired(), command.soloFriendlyPriority(),
                maxTransitTransfers,
                now);

        // ② 제약. 🔴 민감 종류(알레르기·필수 식단)에 값이 들어오면 생성자가 거부한다 —
        //    M1 에는 암호화 경로가 없고, 평문으로 한 번 저장하면 그 데이터가 남는다.
        //    scope 는 TRIP 으로 고정한다 — TRIP-01 이 만드는 제약은 항상 이번 여행
        //    전용이다(PersonalizationScope 문서 참고).
        List<TripConstraint> constraints = new ArrayList<>();
        List<String> constraintIds = new ArrayList<>();
        for (Command.ConstraintInput c : command.constraints()) {
            String id = UUID.randomUUID().toString();
            constraints.add(new TripConstraint(id, tripId, c.type(), c.constraintKey(), c.severity(),
                    c.operator(), c.value(), c.threshold(), c.evidenceStatus(),
                    c.answerStatus(), PersonalizationScope.TRIP, c.dietRequirement()));
            constraintIds.add(id);
        }

        // 🔴 S15P21E201-549 — 민감 제약이 하나라도 있으면 동의를 본다.
        //
        //    위 ② 문단이 막는 것은 "평문 자유 입력을 저장하는 것" 이고, 여기서 막는 것은
        //    "동의 없이 수집하는 것" 이다. 다른 문제다 — 코드로 된 민감 값(ALLERGY+PEANUT
        //    같은)은 생성자를 그대로 통과해 저장되는데, 그것도 개인정보보호법이 말하는
        //    건강에 관한 민감정보다. HEALTH_CONSTRAINTS 동의는 받아서 표에 기록까지 하면서
        //    아무도 안 보고 있었다.
        //
        //    🔴 왜 루프 <b>뒤</b>인가. 도메인 검증이 먼저 이겨야 하기 때문이다. 자유 입력
        //    거부(SensitiveConstraintNotSupportedException)는 동의가 있든 없든 나는 400 인데,
        //    동의를 먼저 보면 미동의 사용자에게는 그 오류가 403 에 가려 영영 안 보인다.
        //    저장은 이 아래에서 한 번에 일어나므로, 여기서 막으면 <b>표에는 아무것도 안 들어간다</b>.
        //
        //    🔴 민감 여부는 여기서 판정하지 않고 TripConstraint.isSensitive 에 묻는다.
        //    목록이 두 벌이 되면 한쪽만 늘어나고, 그 어긋남은 "이 종류만 동의 없이
        //    저장되는" 모양으로 나타나 어느 화면에도 안 보인다.
        if (command.constraints().stream()
                .anyMatch(c -> TripConstraint.isSensitive(c.type(), c.dietRequirement()))) {
            this.consentGuard.requireHealthConstraints(asUuidOrNull(command.userId()));
        }

        // ③ 🔴 만든 사람을 OWNER 로 넣는다. 안 넣으면 자기 여행을 못 본다.
        TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, command.userId(), now);

        // ④ 선호 스냅샷 v1. 계정 취향을 복사해 굳힌다 — 나중에 계정 취향이 바뀌어도
        //    이 여행이 무엇으로 만들어졌는지는 안 바뀐다 (NFR-08). scope 는 TRIP 고정 —
        //    위 제약과 같은 이유다.
        //
        //    🔴 S15P21E201-664 — "transport" 답은 여기서 뺀다. preference_answer 표의
        //    dimension 칸은 CHECK(ck_preference_answer_dimension)로 CATEGORY 등
        //    8종만 허용하고 "transport" 는 그 목록에 없다(마이그레이션
        //    V20260903120000). transport 값은 이미 위에서 travelModes 로 다 소비했으니,
        //    그대로 두면 취향 스냅샷 하나 저장하려다 DB CHECK 위반으로 이 트랜잭션
        //    전체(여행·멤버·제약까지)가 롤백된다 — 그래서 저장 목록에서만 뺀다.
        //    지문(fingerprintOf)은 원본 command.preferences() 를 그대로 쓰므로
        //    영향받지 않는다.
        //
        //    🔴 대소문자를 구분하지 않고 뺀다("TRANSPORT" 도 걸린다) — 별도 작업에서
        //    화면이 보내는 소문자 camelCase 차원 이름 전체(category, touristPreference 등)를
        //    DB CHECK 가 요구하는 대문자로 정규화하는 절차가 컨트롤러 계층에 들어올
        //    예정이고, 그 정규화를 거치면 이 자리에 "TRANSPORT" 로 들어올 수 있다.
        List<PreferenceSnapshot.PreferenceAnswer> storedPreferences = command.preferences().stream()
                .filter(a -> !"transport".equalsIgnoreCase(a.dimension()))
                .toList();

        // 🔴 S15P21E201-547 — 계정 기본 취향으로 이 여행이 답하지 않은 차원을 채운다.
        //
        //    여행에서 답한 값이 항상 이긴다. 채우는 것은 "아예 안 물어봤다"(UNKNOWN)와
        //    아직 없는 차원뿐이고, 화면에서 보고 **일부러 건너뛴**(SKIPPED) 차원은 그대로
        //    둔다 — "이번 여행만 이 조건 빼고" 를 기본값으로 되살리면 사용자는 그 이유를
        //    알 수 없다. 규칙 전체는 PreferenceDefaultsService javadoc 의 표에 있다.
        //
        //    🔴 **반대 방향은 없다.** 이 여행의 답을 계정 기본값에 쓰지 않는다. 그것이
        //    이 티켓의 요구이고(명세 2.2), 그래서 여기서는 읽기만 한다.
        //
        //    계정 기본값이 아직 하나도 없으면(지금은 저장하는 경로가 없다) storedPreferences
        //    가 그대로 나온다 — 즉 이 줄은 동작을 바꾸지 않는다.
        //
        //    ┈┈ 🔴 정정 (2026-09-15 · S15P21E201-639) ┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈
        //    위 두 문단은 **이제 절반만 맞다.** 지우지 않고 남기는 것은 그때의 판단이
        //    틀린 것이 아니라 범위가 늘었기 때문이다.
        //
        //      · "반대 방향은 없다" → **이어받는 방향이 생겼다** (아래 ⑥). 2.2 가 막은 것은
        //        "모르게 바뀌는 일" 인데, 이 줄이 값을 화면에 채워 보여 주게 되면서 그
        //        전제가 달라졌다. 보고 고친 것이 반영되는 것은 모르게 바뀌는 일이 아니다.
        //      · "지금은 저장하는 경로가 없다" → 이제 있다. 다만 HTTP 경로가 아니라
        //        여행을 만들 때 안에서 옮긴다.
        //      · "이 줄은 동작을 바꾸지 않는다" → 두 번째 여행부터 **실제로 채운다.**
        //        첫 여행은 채울 것이 없으므로 그때는 여전히 그대로다.
        //    ┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈┈
        List<PreferenceSnapshot.PreferenceAnswer> mergedPreferences =
                preferenceDefaults.overlayDefaults(command.userId(), storedPreferences);

        PreferenceSnapshot snapshot = new PreferenceSnapshot(
                UUID.randomUUID().toString(), tripId, 1,
                mergedPreferences, PersonalizationScope.TRIP, constraintIds, now);

        // ⑤ 🔴 키 확보와 저장을 한 동작으로 한다.
        //    나누면 같은 키로 동시에 온 요청이 전부 여행을 만든다 — 테스트가 잡았다.
        //    여기서 만든 Trip 객체는 경쟁에서 지면 버려진다. 저장되지 않으므로 문제없다.
        TripRepository.SaveOutcome outcome = repository.saveWithIdempotency(
                command.userId(), idempotencyKey, fingerprint,
                trip, constraints, owner, snapshot);

        // ⑥ 🔴 S15P21E201-639 — 이 여행에서 **고른** 답을 계정 기본값으로 이어받는다.
        //
        //    🔴 왜 여는가 — 겹치기만 두고 채우는 길을 안 만들었더니, 계정 기본값을 가진
        //    사람이 소비 성향 한 차원뿐이었다(2026-09-15 실측: USER 스냅샷 9건 전부
        //    SPEND_PROFILE). 채울 것이 없으니 겹치기가 아무 일도 안 했고, 같은 사람이 두 번째
        //    여행에서도 처음부터 다시 답했다. 어느 차원을 이어받는지는 CARRY_OVER 에 있다.
        //
        //    🔴 "빈칸만 채운다" 로 먼저 만들었다가 되돌렸다. 계정 기본값을 고치는 화면이
        //    없어서(소비 성향 하나만 있다) 사용자가 첫 답에 영구히 갇히기 때문이다 —
        //    화면의 값을 고쳐도 그 여행에만 적용되고 계정은 그대로라, 다음 여행에 또 옛
        //    값이 채워진다. **매번 다시 묻는 것보다 나쁘다.** 까닭 전부는
        //    PreferenceDefaultsService javadoc 의 「빈칸만 채운다 로 먼저 만들었다가
        //    되돌렸다」 절에 있다.
        //
        //    🔴 명세 2.2 와 어긋나 보이지만 전제가 달라졌다. 2.2 가 막은 것은 "사용자가
        //    **모르게** 프로필이 바뀌는 일" 이고, 그때는 계정 기본값이 화면에 안 보였다.
        //    이제 위 ④ 의 겹치기가 그것을 화면에 채워 보여 준다 — 보고 고친 것이 반영되는
        //    것은 모르게 바뀌는 일이 아니다. 오히려 반영이 안 되는 쪽이 놀랍다.
        //
        //    SKIPPED("이번 여행만 이 조건 빼고")와 UNKNOWN(안 물어봤다)은 그대로 안 건드린다.
        //
        //    🔴 storedPreferences 를 넘긴다 — mergedPreferences 가 아니다. 겹친 뒤의 목록에는
        //    계정 기본값이 이미 섞여 있어서, 사용자가 답하지 않은 차원까지 "고른 것" 이 된다.
        //
        //    🔴 실제로 만들어졌을 때만 한다. 같은 키로 다시 온 요청(created=false)은 여행을
        //    안 만들었으므로 취향도 새로 정한 것이 아니다.
        if (outcome.created()) {
            preferenceDefaults.carryOver(command.userId(), storedPreferences);
            saveMustVisitPlaces(outcome.trip().tripId(), command.mustVisitPlaceIds(), now);
        }

        return new Result(outcome.trip(), outcome.snapshot(), outcome.created());
    }

    /**
     * 꼭 가고 싶은 장소를 씨앗으로 적는다 — S15P21E201-973.
     *
     * <p>공유 일정 복제가 쓰던 {@code trip_seed_place} 를 그대로 쓴다. 추천 엔진이 이미 그
     * 표를 읽어 후보를 앞세우므로({@code SeedBoost}) 새 경로를 만들 이유가 없다.
     *
     * <p>🔴 새로 만든 여행일 때만 부른다. 같은 멱등 키로 다시 온 요청은 기존 여행을 돌려주는
     * 것이고, 그 여행에는 씨앗이 이미 있다 — 다시 적으면 기본키(trip_id, place_id)에 걸린다.
     *
     * <p>🔴 저장소가 없으면(인메모리 프로필) 조용히 건너뛴다. 씨앗은 추천을 거들 뿐이라
     * 없다고 여행 생성이 실패해야 할 이유가 없다.
     *
     * <p>순서는 사용자가 고른 순서 그대로다. 같은 장소를 두 번 고른 경우는 앞의 것만 남긴다 —
     * 표의 기본키가 (여행, 장소)라 중복이 들어가면 트랜잭션 전체가 롤백된다.
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
     * 본문의 지문.
     *
     * <p>🔴 같은 키를 <b>다른 내용</b>으로 재사용하면 409 로 거부해야 한다(API-09).
     * 그러려면 "같은 내용인가" 를 비교할 것이 필요하다.
     */
    /**
     * 사용자 ID 를 {@code UUID} 로 바꾼다. 형식이 아니면 {@code null} — S15P21E201-549.
     *
     * <p>🔴 예외를 던지지 않고 {@code null} 을 주는 이유는, 여기서 400 을 내면 <b>동의가
     * 없는 것</b>과 <b>ID 가 이상한 것</b>이 서로 다른 오류로 갈라져 앱이 두 갈래를 다뤄야
     * 하기 때문이다. 둘 다 "이 사람의 동의를 확인할 수 없다" 이고, 그때 할 일은 하나다 —
     * 저장하지 않는다. 가드가 {@code null} 을 미동의로 다룬다.
     */
    private static UUID asUuidOrNull(String userId) {
        try {
            return (userId == null) ? null : UUID.fromString(userId);
        }
        catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

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
     * 취향 답들 중 {@code dimension} 이 {@code "transport"}(대소문자 구분 안 함)인 답을
     * 찾아 {@code travel_modes} 로 바꾼다.
     *
     * <p>🔴 답이 없거나, 있어도 {@code SELECTED} 가 아니면(SKIPPED/UNKNOWN) 빈 배열이다 —
     * 지금까지의 거동(travelModes 를 못 채운 것)과 같다. 답이 있는데 값을 이해하지
     * 못하면(9절 참고) 예외를 던진다 — 여기서 조용히 넘기면 이 배선이 지금까지 안
     * 됐던 것과 똑같은 실패(값이 조용히 버려짐)를 다른 자리에서 반복하는 셈이다.
     */
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
     * <p>🔴 값이 단순 문자열 하나뿐이라 이걸로 충분하다. {@code ObjectMapper}(JSON을
     * 자바 객체로 바꾸는 라이브러리)를 안 쓴 이유 — domain 패키지({@link TravelModes})에
     * Jackson 의존을 들이지 않으려는 것이라 이 파싱은 여기(application 계층)에 남긴다.
     * 한계 — 배열 JSON({@code ["BUS","SUBWAY"]})이 오면 앞뒤가 큰따옴표가 아니라서
     * 이 벗기기가 안 먹고 원문 그대로 {@link TravelModes#fromTransportPreference}에
     * 넘어간다. 그러면 매핑표 밖의 값이라 거부되는데, 그 형태를 지어내서 해석하지 않고
     * 그대로 거절하는 것이 맞다 — 배열이 왔다는 것 자체가 계약 밖의 요청이다.
     */
    private static String unquoteJsonString(String valueJson) {
        if (valueJson != null && valueJson.length() >= 2
                && valueJson.startsWith("\"") && valueJson.endsWith("\"")) {
            return valueJson.substring(1, valueJson.length() - 1);
        }
        return valueJson;
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

            /**
             * 꼭 가고 싶은 장소의 {@code place_id} — S15P21E201-973. 비어 있으면 아무 일도 안 한다.
             *
             * <p>고른 순서를 그대로 쓴다. 새 여행일 때만 {@code trip_seed_place} 에 적힌다.
             */
            List<String> mustVisitPlaceIds) {

        /** 안 준 목록을 빈 목록으로 고정한다 — 뒤쪽이 null 을 다시 보지 않게 한다. */
        public Command {
            mustVisitPlaceIds = mustVisitPlaceIds == null ? List.of() : List.copyOf(mustVisitPlaceIds);
        }

        /** 꼭 가고 싶은 장소가 없던 시절의 시그니처. 기존 호출부를 그대로 둔다. */
        public Command(String userId, LocalDate startDate, LocalDate finishDate, Double originLat, Double originLng,
                Integer budgetKrw, int partySize, String timeWindow, String timezone,
                List<PreferenceSnapshot.PreferenceAnswer> preferences, List<ConstraintInput> constraints,
                Trip.OwnerType ownerType, String accommodationPlaceId, boolean englishMenuRequired,
                boolean foreignCardRequired, boolean soloFriendlyPriority, Integer maxTransitTransfers) {
            this(userId, startDate, finishDate, originLat, originLng, budgetKrw, partySize, timeWindow, timezone,
                    preferences, constraints, ownerType, accommodationPlaceId, englishMenuRequired,
                    foreignCardRequired, soloFriendlyPriority, maxTransitTransfers, List.of());
        }

        /**
         * 🔴 S15P21E201-317·456 이전의 시그니처를 그대로 남긴다 — 회원 전용·다섯 칸 없이
         * 여행을 만들던 기존 호출부(공유 일정 복제·테스트 다수)를 하나도 고치지 않기
         * 위해서다. {@code ownerType} 은 항상 {@code USER}, 다섯 칸은 기본값(false·null)이다.
         */
        public Command(String userId, LocalDate startDate, LocalDate finishDate, Double originLat, Double originLng,
                Integer budgetKrw, int partySize, String timeWindow, String timezone,
                List<PreferenceSnapshot.PreferenceAnswer> preferences, List<ConstraintInput> constraints) {
            this(userId, startDate, finishDate, originLat, originLng, budgetKrw, partySize, timeWindow, timezone,
                    preferences, constraints, Trip.OwnerType.USER, null, false, false, false, null);
        }

        /**
         * 🔴 S15P21E201-456 시그니처(다섯 칸 포함, ownerType 없음)를 그대로 남긴다 —
         * 회원 전용 호출부는 {@code ownerType} 을 몰라도 되게 한다.
         */
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

        /**
         * 🔴 S15P21E201-317 시그니처(ownerType 포함, 다섯 칸 없음)를 그대로 남긴다 —
         * 익명 승계 테스트처럼 소유자 종류만 필요한 호출부는 다섯 칸을 몰라도 되게 한다.
         */
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
