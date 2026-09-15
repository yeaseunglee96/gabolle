package com.gabolle.backend.trip.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * 여행 — 사용자가 입력한 조건 묶음 (TRIP-01).
 *
 * <p>🔴 조건을 저장하지 않으면 계산이 끝난 뒤 <b>무슨 조건으로 만든 일정인지</b>를
 * 되짚을 수 없다. 조건이 없으면 결과를 고칠 수도, 다시 만들 수도 없다.
 *
 * <p>ERD 의 {@code TRIPS} 표에 대응한다. 컬럼을 발명하지 않았다.
 */
public class Trip {

    private final String tripId;
    private final String createdBy;

    /**
     * 🔴 S15P21E201-317 — {@code createdBy} 가 회원(app_user)인지 익명 세션
     * (anonymous_session)인지 구분한다. 가입 전 만든 여행은 {@code ANONYMOUS} 로 시작해서,
     * 가입할 때 그 세션이 만든 여행을 전부 찾아 {@code USER} 로 바꾸는 승계(claim)의
     * 대상이 된다. 회원이 직접 만든 여행은 항상 {@code USER} 다.
     */
    private final OwnerType ownerType;

    private final LocalDate startDate;
    private final LocalDate finishDate;

    /** 출발지. 매일 여기서 일정이 시작된다. */
    private final Double originLat;
    private final Double originLng;

    private final Integer budgetKrw;
    private final int partySize;

    /** 하루 활동 시간대. 예: {@code MORNING_TO_EVENING} */
    private final String timeWindow;

    /**
     * 🔴 S15P21E201-604 — {@code time_window}(프리셋)과는 <b>다른 칸</b>이다. 같은 사실을
     * 말하는 칸 둘을 정리하는 것은 별도 티켓이므로 여기서 건드리지 않는다. 추천 엔진이 실제
     * 활동 시각을 읽으려면 이 칸이 있어야 한다 — 프리셋 이름만으로는 몇 시부터 몇 시까지인지
     * 계산할 수 없다.
     */
    private final LocalTime timeWindowStart;
    private final LocalTime timeWindowEnd;

    /**
     * 여행 중 쓸 이동 수단. 값 목록은 마이그레이션 {@code ck_trip_travel_modes} 의 아홉 개가
     * 정본이다 — DB CHECK 가 먼저 터지면 어느 필드가 문제인지 응답에 안 남으므로 여기서도
     * 검증한다({@link #validateTravelModes(String[])}).
     */
    private final String[] travelModes;

    /** 🔴 API-03 — 시간대를 공통 사전으로 고정한다. 안 맞추면 일정이 통째로 밀린다. */
    private final String timezone;

    /**
     * 🔴 S15P21E201-456 — 매일 여기서 시작하고 여기로 돌아온다. {@code place} 를 가리키는
     * FK 다. 서버가 이 값을 갖고 있지 않으면 일정의 시작점·끝점이 매일 달라진다.
     */
    private final String accommodationPlaceId;

    /** 영어 메뉴가 있는 곳을 우선한다 (선호, HARD 필터 아님). */
    private final boolean englishMenuRequired;

    /** 해외 카드를 받는 곳을 우선한다 (선호). */
    private final boolean foreignCardRequired;

    /** 혼밥하기 편한 곳을 우선한다 (선호). */
    private final boolean soloFriendlyPriority;

    /**
     * 대중교통 최대 환승 횟수. {@code null} 이면 제한 없음.
     *
     * <p>🔴 이동 수단에 {@code PRIVATE_CAR} 가 포함되면 이 값은 뜻이 없다 — 자차 이동에는
     * 환승 개념이 없다. 그 경우 저장 시점(application 계층, {@code TripCreationService})이
     * 이 값을 무시하고 {@code null} 로 만든다 — 도메인은 그 규칙을 강제하지 않는다({@code
     * travelModes} 가 나중에 바뀔 수 있는 값이라 생성자에서 못박으면 순서에 따라 결과가
     * 달라진다).
     */
    private final Integer maxTransitTransfers;

    /**
     * 사용자가 붙인 이름 — S15P21E201-1023. {@code null} 이면 <b>아직 이름이 없다</b>.
     *
     * <p>🔴 <b>생성자에 넣지 않았다.</b> 이름은 만들 때 정해지는 값이 아니라 나중에 붙는
     * 값이다. 생성자에 넣으면 이미 네 개가 사슬로 물려 있는 호출부를 전부 고쳐야 하는데,
     * 그건 이 칸이 가진 뜻과 아무 상관이 없는 변경이다.
     *
     * <p>🔴 {@code null} 을 「이름 없음」으로 쓰고 빈 문자열을 쓰지 않는다. 두 가지가
     * 같은 뜻을 말하면 화면이 둘 다 검사해야 하고, 언젠가 한쪽을 빠뜨린다.
     */
    private String title;

    private Status status;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * 🔴 TRIP-05 soft delete. {@code null} 이 아니면 지워진 것이다.
     *
     * <p>2026-09-03 이전에는 이걸 {@code Status.DELETED} 로 표현했는데, DB
     * {@code trip.status} CHECK 제약(S15P21E201-554)이 {@code DELETED} 를 안 받는다 —
     * "지워졌다" 를 말하는 자리를 {@code deleted_at} 하나로만 두기로 했기 때문이다(고지혁
     * 님 결정). 두 자리에 같은 뜻을 담으면 둘이 어긋나는 날 어느 쪽이 맞는지 아무도
     * 모른다. 그래서 이 칸 하나로만 삭제를 말한다.
     */
    private Instant deletedAt;

    /**
     * 🔴 S15P21E201-604 이전의 생성자를 그대로 남긴다 — {@code TripCreationService}(다른
     * 작업이 진행 중이라 여기서 열지 않는다)가 이 시그니처를 쓰고 있다. 새 필드
     * (travelModes·timeWindowStart·timeWindowEnd)는 null/빈 배열로 들어온 것으로 본다.
     */
    public Trip(String tripId, String createdBy,
                LocalDate startDate, LocalDate finishDate,
                Double originLat, Double originLng,
                Integer budgetKrw, int partySize,
                String timeWindow, String timezone,
                Instant createdAt) {
        this(tripId, createdBy, startDate, finishDate, originLat, originLng, budgetKrw, partySize,
                timeWindow, timezone, null, null, null, createdAt);
    }

    /** S15P21E201-604 — 추천 엔진이 읽어야 하는 travelModes·시간대 세 칸을 더한 생성자. */
    public Trip(String tripId, String createdBy,
                LocalDate startDate, LocalDate finishDate,
                Double originLat, Double originLng,
                Integer budgetKrw, int partySize,
                String timeWindow, String timezone,
                String[] travelModes, LocalTime timeWindowStart, LocalTime timeWindowEnd,
                Instant createdAt) {
        this(tripId, createdBy, startDate, finishDate, originLat, originLng, budgetKrw, partySize,
                timeWindow, timezone, travelModes, timeWindowStart, timeWindowEnd,
                null, false, false, false, null, createdAt);
    }

    /**
     * S15P21E201-456 — 숙소·영어메뉴/해외카드/혼밥우선 선호·최대환승횟수를 더한 생성자.
     *
     * <p>🔴 옛 생성자(14-인자)를 지우지 않는다 — {@code TripCreationService} 가 이 다섯
     * 칸이 없던 시절부터 그 시그니처를 썼고, 위임하며 기본값(false·null)을 채우면 그
     * 호출부를 건드리지 않고도 새 칸을 더할 수 있다.
     */
    public Trip(String tripId, String createdBy,
                LocalDate startDate, LocalDate finishDate,
                Double originLat, Double originLng,
                Integer budgetKrw, int partySize,
                String timeWindow, String timezone,
                String[] travelModes, LocalTime timeWindowStart, LocalTime timeWindowEnd,
                String accommodationPlaceId, boolean englishMenuRequired, boolean foreignCardRequired,
                boolean soloFriendlyPriority, Integer maxTransitTransfers,
                Instant createdAt) {
        this(tripId, createdBy, OwnerType.USER, startDate, finishDate, originLat, originLng, budgetKrw, partySize,
                timeWindow, timezone, travelModes, timeWindowStart, timeWindowEnd,
                accommodationPlaceId, englishMenuRequired, foreignCardRequired, soloFriendlyPriority,
                maxTransitTransfers, createdAt);
    }

    /**
     * S15P21E201-317 — 소유자 종류({@code ownerType})까지 받는 생성자. 익명 세션이 여행을
     * 만드는 경로({@code TripCreationService})가 이걸 쓴다. 위 생성자들은 항상
     * {@code OwnerType.USER} 로 고정해 이 생성자에 위임한다 — 회원 전용이던 기존 호출부를
     * 하나도 고치지 않기 위해서다.
     */
    public Trip(String tripId, String createdBy, OwnerType ownerType,
                LocalDate startDate, LocalDate finishDate,
                Double originLat, Double originLng,
                Integer budgetKrw, int partySize,
                String timeWindow, String timezone,
                String[] travelModes, LocalTime timeWindowStart, LocalTime timeWindowEnd,
                String accommodationPlaceId, boolean englishMenuRequired, boolean foreignCardRequired,
                boolean soloFriendlyPriority, Integer maxTransitTransfers,
                Instant createdAt) {

        if (startDate == null || finishDate == null) {
            throw new IllegalArgumentException("여행 시작일과 종료일은 필수다");
        }
        if (finishDate.isBefore(startDate)) {
            // 끝나는 날이 시작하는 날보다 앞일 수는 없다.
            throw new IllegalArgumentException(
                    "종료일(" + finishDate + ")이 시작일(" + startDate + ")보다 앞이다");
        }
        if (partySize < 1) {
            throw new IllegalArgumentException("인원은 1명 이상이어야 한다: " + partySize);
        }
        if (budgetKrw != null && budgetKrw < 0) {
            throw new IllegalArgumentException("예산은 음수일 수 없다: " + budgetKrw);
        }
        if (originLat != null && (originLat < -90 || originLat > 90)) {
            throw new IllegalArgumentException("위도 범위를 벗어났다: " + originLat);
        }
        if (originLng != null && (originLng < -180 || originLng > 180)) {
            throw new IllegalArgumentException("경도 범위를 벗어났다: " + originLng);
        }
        if (maxTransitTransfers != null && maxTransitTransfers < 0) {
            throw new IllegalArgumentException("최대 환승 횟수는 음수일 수 없다: " + maxTransitTransfers);
        }

        this.tripId = tripId;
        this.createdBy = createdBy;
        this.ownerType = ownerType != null ? ownerType : OwnerType.USER;
        this.startDate = startDate;
        this.finishDate = finishDate;
        this.originLat = originLat;
        this.originLng = originLng;
        this.budgetKrw = budgetKrw;
        this.partySize = partySize;
        this.timeWindow = timeWindow;
        this.timeWindowStart = timeWindowStart;
        this.timeWindowEnd = timeWindowEnd;
        this.travelModes = validateTravelModes(travelModes);
        this.accommodationPlaceId = accommodationPlaceId;
        this.englishMenuRequired = englishMenuRequired;
        this.foreignCardRequired = foreignCardRequired;
        this.soloFriendlyPriority = soloFriendlyPriority;
        this.maxTransitTransfers = maxTransitTransfers;
        this.timezone = timezone != null ? timezone : "Asia/Seoul";
        this.status = Status.PLANNING;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * 🔴 값 목록은 마이그레이션 {@code ck_trip_travel_modes} 의 아홉 개가 정본이다. DB CHECK 가
     * 먼저 터지면 어느 필드가 문제인지 응답에 안 나오므로 여기서 먼저 잡는다.
     */
    private static final Set<String> ALLOWED_TRAVEL_MODES = Set.of(
            "WALK", "BUS", "SUBWAY", "TAXI", "PRIVATE_CAR", "RENTAL_CAR", "BICYCLE", "FERRY", "OTHER");

    private static String[] validateTravelModes(String[] travelModes) {
        if (travelModes == null) {
            return new String[0];
        }
        for (String mode : travelModes) {
            if (mode == null || !ALLOWED_TRAVEL_MODES.contains(mode)) {
                throw new IllegalArgumentException("허용되지 않는 travelMode 다: " + mode);
            }
        }
        return travelModes.clone();
    }

    /**
     * 저장소가 읽어온 값 그대로 되살린다 — S15P21E201-461 JPA 저장소 전용.
     *
     * <p>{@link Builder} 를 쓴다. 생성 시점 이후 업무 규칙({@link #markReady(Instant)}·
     * {@link #markDeleted(Instant)})을 거치며 바뀐 {@code status}·{@code updatedAt}·
     * {@code deletedAt} 을 그대로 받아야 하는데, 그 규칙들은 "한 번만 반영한다" 같은
     * 부작용을 갖고 있어서 다시 태우면 값이 틀어질 수 있다. 이미 규칙을 통과해 저장된
     * 값이므로 재검증하지 않는다.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String tripId;
        private String createdBy;
        private OwnerType ownerType;
        private LocalDate startDate;
        private LocalDate finishDate;
        private Double originLat;
        private Double originLng;
        private Integer budgetKrw;
        private int partySize;
        private String timeWindow;
        private LocalTime timeWindowStart;
        private LocalTime timeWindowEnd;
        private String[] travelModes;
        private String accommodationPlaceId;
        private boolean englishMenuRequired;
        private boolean foreignCardRequired;
        private boolean soloFriendlyPriority;
        private Integer maxTransitTransfers;
        private String timezone;
        private String title;
        private Status status;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant deletedAt;

        private Builder() {
        }

        public Builder tripId(String tripId) { this.tripId = tripId; return this; }
        public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }
        public Builder ownerType(OwnerType ownerType) { this.ownerType = ownerType; return this; }
        public Builder startDate(LocalDate startDate) { this.startDate = startDate; return this; }
        public Builder finishDate(LocalDate finishDate) { this.finishDate = finishDate; return this; }
        public Builder originLat(Double originLat) { this.originLat = originLat; return this; }
        public Builder originLng(Double originLng) { this.originLng = originLng; return this; }
        public Builder budgetKrw(Integer budgetKrw) { this.budgetKrw = budgetKrw; return this; }
        public Builder partySize(int partySize) { this.partySize = partySize; return this; }
        public Builder timeWindow(String timeWindow) { this.timeWindow = timeWindow; return this; }
        public Builder timeWindowStart(LocalTime timeWindowStart) { this.timeWindowStart = timeWindowStart; return this; }
        public Builder timeWindowEnd(LocalTime timeWindowEnd) { this.timeWindowEnd = timeWindowEnd; return this; }
        public Builder travelModes(String[] travelModes) { this.travelModes = travelModes; return this; }
        public Builder accommodationPlaceId(String accommodationPlaceId) { this.accommodationPlaceId = accommodationPlaceId; return this; }
        public Builder englishMenuRequired(boolean englishMenuRequired) { this.englishMenuRequired = englishMenuRequired; return this; }
        public Builder foreignCardRequired(boolean foreignCardRequired) { this.foreignCardRequired = foreignCardRequired; return this; }
        public Builder soloFriendlyPriority(boolean soloFriendlyPriority) { this.soloFriendlyPriority = soloFriendlyPriority; return this; }
        public Builder maxTransitTransfers(Integer maxTransitTransfers) { this.maxTransitTransfers = maxTransitTransfers; return this; }
        public Builder timezone(String timezone) { this.timezone = timezone; return this; }
        /** 🔴 {@link Trip#rename} 을 태우지 않는다 — 이미 규칙을 통과해 저장된 값이다. */
        public Builder title(String title) { this.title = title; return this; }
        public Builder status(Status status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder deletedAt(Instant deletedAt) { this.deletedAt = deletedAt; return this; }

        /**
         * 🔴 생성 규칙(status=PLANNING·updatedAt=createdAt)을 먼저 태우고, 저장소가
         * 읽어온 실제 값이 있으면 그 위에 덮는다 — {@code new Trip(...)} 하나만으로는
         * status·updatedAt·deletedAt 을 지정할 방법이 없어서다(그 생성자는 항상
         * PLANNING·createdAt 으로 시작하도록 만들어졌다, TRIP-01).
         */
        public Trip build() {
            Trip trip = new Trip(tripId, createdBy, ownerType, startDate, finishDate, originLat, originLng,
                    budgetKrw, partySize, timeWindow, timezone, travelModes, timeWindowStart, timeWindowEnd,
                    accommodationPlaceId, englishMenuRequired, foreignCardRequired, soloFriendlyPriority,
                    maxTransitTransfers, createdAt);
            trip.title = title;
            if (status != null) {
                trip.status = status;
            }
            if (updatedAt != null) {
                trip.updatedAt = updatedAt;
            }
            trip.deletedAt = deletedAt;
            return trip;
        }
    }

    /** 며칠짜리 여행인가. 당일치기는 1이다. */
    public int nights() {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, finishDate);
    }

    public int days() {
        return nights() + 1;
    }

    /** S15P21E201-317 — {@code createdBy} 가 가리키는 표. */
    public enum OwnerType {
        /** {@code createdBy} 는 {@code app_user.user_id} 다. */
        USER,
        /** {@code createdBy} 는 {@code anonymous_session.session_id} 다. 가입하면 {@code USER} 로 승계된다. */
        ANONYMOUS
    }

    public enum Status {
        /** 조건만 저장된 상태. 아직 일정이 없다 */
        PLANNING,
        /** 일정이 만들어졌다 */
        READY,
        /** 여행 중 */
        IN_PROGRESS,
        COMPLETED;

        /** 🔴 삭제는 {@link Trip#isDeleted()}(= {@code deletedAt}) 로 따로 본다. 여기 안 넣는다. */
        public boolean isTerminal() {
            return this == COMPLETED;
        }
    }

    /**
     * 🔴 TRIP-05 — soft delete. 행을 지우지 않는 이유는 일정·이벤트가 이 여행을 가리키기
     * 때문이다. {@code status} 는 안 건드린다 — 지워진 뒤에도 "지워지기 전에 어느
     * 단계였나"(PLANNING 중 지웠나, READY 상태에서 지웠나)가 남아야 분석에서 구분된다.
     */
    public void markDeleted(Instant at) {
        if (this.deletedAt != null) {
            return;
        }
        this.deletedAt = at;
        this.updatedAt = at;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void markReady(Instant at) {
        if (isDeleted() || status.isTerminal()) {
            throw new IllegalStateException("끝난 여행은 상태를 바꿀 수 없다: " + status);
        }
        this.status = Status.READY;
        this.updatedAt = at;
    }

    /**
     * 카드 한 줄에 들어가는 한계에서 온 값. 마이그레이션 {@code trip.title varchar(60)} 과
     * 같은 숫자다 — DB 제약이 먼저 터지면 어느 필드가 문제인지 응답에 안 남는다.
     */
    public static final int TITLE_MAX_LENGTH = 60;

    /**
     * 이름을 붙이거나 지운다 — S15P21E201-1023.
     *
     * <p>비었거나 공백뿐이면 {@code null} 로 만든다. 즉 <b>이름 지우기가 따로 없다</b> —
     * 빈 이름을 보내는 것이 지우는 것이다. 지우기 전용 경로를 따로 두면 «빈 이름» 과
     * «이름 없음» 이 갈라지고, 그 둘은 화면에서 구분할 수 없는 같은 것이다.
     *
     * <p>🔴 줄바꿈·제어문자를 거부한다. 이름은 <b>한 줄</b>이라 카드가 두 줄로 밀리면
     * 목록 전체가 어긋난다. 그리고 나중에 이 자리에 <b>모델이 지어낸 이름</b>이 들어온다
     * (S15P21E201-1025) — 그때 막는 것보다 칸 자체가 안 받는 편이 확실하다.
     *
     * <p>길이는 글자 수로 센다({@code codePointCount}). 이모지 하나는 자바에서 두 칸을
     * 차지하지만 DB {@code varchar(60)} 은 한 글자로 세므로, 자바 길이로 재면 멀쩡한
     * 이름이 거부된다.
     */
    public void rename(String title, Instant at) {
        String trimmed = (title == null) ? null : title.trim();
        if (trimmed != null && trimmed.isEmpty()) {
            trimmed = null;
        }
        if (trimmed != null) {
            if (trimmed.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("여행 이름에 줄바꿈이나 제어문자를 넣을 수 없다");
            }
            int length = trimmed.codePointCount(0, trimmed.length());
            if (length > TITLE_MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "여행 이름은 " + TITLE_MAX_LENGTH + "자를 넘을 수 없다: " + length + "자");
            }
        }
        this.title = trimmed;
        this.updatedAt = at;
    }

    public String tripId()       { return tripId; }
    public String createdBy()    { return createdBy; }
    /** {@code null} 이면 아직 이름이 없다 — 화면이 날짜를 제목으로 쓴다. */
    public String title()        { return title; }
    public OwnerType ownerType() { return ownerType; }
    public LocalDate startDate() { return startDate; }
    public LocalDate finishDate(){ return finishDate; }
    public Double originLat()    { return originLat; }
    public Double originLng()    { return originLng; }
    public Integer budgetKrw()   { return budgetKrw; }
    public int partySize()       { return partySize; }
    public String timeWindow()   { return timeWindow; }
    public LocalTime timeWindowStart() { return timeWindowStart; }
    public LocalTime timeWindowEnd()   { return timeWindowEnd; }
    /** 방어적 복사본 — 밖에서 바꿔도 이 여행의 값은 안 바뀐다. */
    public String[] travelModes() { return travelModes.clone(); }
    public String accommodationPlaceId()     { return accommodationPlaceId; }
    public boolean englishMenuRequired()     { return englishMenuRequired; }
    public boolean foreignCardRequired()     { return foreignCardRequired; }
    public boolean soloFriendlyPriority()    { return soloFriendlyPriority; }
    public Integer maxTransitTransfers()     { return maxTransitTransfers; }
    public String timezone()     { return timezone; }
    public Status status()       { return status; }
    public Instant createdAt()   { return createdAt; }
    public Instant updatedAt()   { return updatedAt; }
    public Instant deletedAt()   { return deletedAt; }
}
